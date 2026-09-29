"""Create Lucene input records from the Round 2 checkpoint on one Kaggle GPU."""

from __future__ import annotations

import argparse
import html
import json
import re
import sqlite3
import struct
import time
import unicodedata
from html.parser import HTMLParser
from pathlib import Path

import numpy as np
import regex
import torch
import torch.nn as nn
import torch.nn.functional as F
from safetensors.torch import load_file
from transformers import AutoTokenizer, BertModel, PreTrainedTokenizerFast


MARKS = re.compile(r"[\u0591-\u05BD\u05BF-\u05C7]")
INVISIBLE = re.compile(r"[\u200B-\u200F\u202A-\u202E\u2060-\u2069\uFEFF]")
PUNCTUATION = re.compile(r"([!?.,;:])\1{2,}")
SPACE = re.compile(r"\s+")
TRANSLATION = str.maketrans({
    "`": "'", "´": "'", "‘": "'", "’": "'", "‚": "'", "‛": "'", "׳": "'",
    "“": '"', "”": '"', "„": '"', "‟": '"', "״": '"', "־": "-", "–": "-",
    "—": "-", "−": "-", "…": "...", "׃": ":",
})
BLOCK_TAGS = {
    "address", "article", "aside", "blockquote", "br", "div", "footer", "h1", "h2",
    "h3", "h4", "h5", "h6", "header", "li", "p", "section", "table", "td", "th", "tr",
}
RECORD_BYTES = 8 + 8 + 4 + 256 * 4


class VisibleText(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.parts: list[str] = []
        self.hidden = 0

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag in {"script", "style", "template"}:
            self.hidden += 1
        elif tag in BLOCK_TAGS:
            self.parts.append(" ")

    def handle_endtag(self, tag: str) -> None:
        if tag in {"script", "style", "template"} and self.hidden:
            self.hidden -= 1
        elif tag in BLOCK_TAGS:
            self.parts.append(" ")

    def handle_data(self, data: str) -> None:
        if not self.hidden:
            self.parts.append(data)


def clean(text: str) -> str:
    if not text:
        return ""
    if "<" in text or "&" in text:
        parser = VisibleText()
        parser.feed(text)
        parser.close()
        text = "".join(parser.parts)
    text = unicodedata.normalize("NFKC", html.unescape(text))
    text = INVISIBLE.sub("", MARKS.sub("", text)).translate(TRANSLATION)
    text = regex.sub(r"\p{Script=Latin}+", " ", text)
    return SPACE.sub(" ", PUNCTUATION.sub(r"\1\1", text)).strip()


class SentenceEncoder(nn.Module):
    def __init__(self, backbone: BertModel, dimension: int) -> None:
        super().__init__()
        self.backbone = backbone
        self.projection = nn.Linear(backbone.config.hidden_size, dimension, bias=False)
        self.projection_norm = nn.LayerNorm(dimension)

    def forward(self, input_ids: torch.Tensor, attention_mask: torch.Tensor) -> torch.Tensor:
        output = self.backbone(input_ids=input_ids, attention_mask=attention_mask).last_hidden_state
        mask = attention_mask.unsqueeze(-1).to(output.dtype)
        pooled = (output * mask).sum(1) / mask.sum(1).clamp_min(1)
        return F.normalize(self.projection_norm(self.projection(pooled)).float(), p=2, dim=1)


def load_model(checkpoint: Path, device: torch.device) -> tuple[PreTrainedTokenizerFast, SentenceEncoder]:
    metadata = json.loads((checkpoint / "sentence_encoder_config.json").read_text(encoding="utf-8"))
    assert int(metadata["embedding_dim"]) == 256
    assert metadata["passage_prefix"] == "[PASSAGE]"
    tokenizer_file = checkpoint / "tokenizer" / "tokenizer.json"
    tokenizer = PreTrainedTokenizerFast(tokenizer_file=str(tokenizer_file))
    vocab = tokenizer.get_vocab()
    for name, candidate in (("pad_token", "[PAD]"), ("unk_token", "[UNK]"),
                            ("cls_token", "[CLS]"), ("sep_token", "[SEP]"),
                            ("mask_token", "[MASK]")):
        if getattr(tokenizer, name) is None and candidate in vocab:
            setattr(tokenizer, name, candidate)
    backbone = BertModel.from_pretrained(checkpoint / "backbone")
    model = SentenceEncoder(backbone, 256)
    weights = load_file(str(checkpoint / "projection.safetensors"), device="cpu")
    model.projection.load_state_dict({"weight": weights["projection.weight"]})
    model.projection_norm.load_state_dict({
        "weight": weights["projection_norm.weight"],
        "bias": weights["projection_norm.bias"],
    })
    return tokenizer, model.to(device).eval()


def run(args: argparse.Namespace) -> None:
    assert args.shards > 0 and args.shards % args.gpus == 0
    assert 0 <= args.gpu < args.gpus
    torch.set_num_threads(2)
    device = torch.device(f"cuda:{args.gpu}" if torch.cuda.is_available() else "cpu")
    if device.type == "cuda":
        assert torch.cuda.device_count() >= args.gpus, "Select Kaggle T4 x2 accelerator"
    print(f"worker {args.gpu}: device={device}", flush=True)
    tokenizer, model = load_model(args.checkpoint, device)
    args.output.mkdir(parents=True, exist_ok=True)
    counts = {shard: 0 for shard in range(args.gpu, args.shards, args.gpus)}
    outputs = {shard: (args.output / f"shard-{shard:02d}.bin").open("wb", buffering=1 << 20)
               for shard in counts}
    connection = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)
    started = time.monotonic()
    validated = False

    @torch.inference_mode()
    def encode_batch(batch: list[tuple[int, int, int, str]]) -> None:
        nonlocal validated
        tokens = tokenizer(
            [f"[PASSAGE] {row[3]}" for row in batch], padding=True, truncation=True,
            max_length=256, return_tensors="pt",
        )
        token_inputs = {key: tokens[key].to(device) for key in ("input_ids", "attention_mask")}
        with torch.autocast("cuda", dtype=torch.float16, enabled=device.type == "cuda" and args.amp):
            vectors = model(**token_inputs).cpu().numpy().astype("<f4", copy=False)
        assert vectors.shape == (len(batch), 256) and np.isfinite(vectors).all()
        if args.gpu == 0 and args.onnx is not None and not validated:
            import onnxruntime as ort

            session = ort.InferenceSession(str(args.onnx), providers=["CPUExecutionProvider"])
            cosine_values = []
            for position in range(min(8, len(batch))):
                reference = session.run(None, {
                    "input_ids": token_inputs["input_ids"][position:position + 1].cpu().numpy(),
                    "attention_mask": token_inputs["attention_mask"][position:position + 1].cpu().numpy(),
                })[0][0]
                cosine_values.append(float(np.dot(reference, vectors[position]) /
                                           (np.linalg.norm(reference) * np.linalg.norm(vectors[position]))))
            print(f"GPU/ONNX minimum cosine: {min(cosine_values):.6f}", flush=True)
            assert min(cosine_values) >= 0.985, "GPU vectors do not match the bundled ONNX model"
            validated = True
        for (line_id, book_id, is_base, _), vector in zip(batch, vectors):
            shard = line_id % args.shards
            outputs[shard].write(struct.pack("<qqi", line_id, book_id, is_base))
            outputs[shard].write(vector.tobytes())
            counts[shard] += 1

    try:
        rows = connection.execute(
            "SELECT l.id, l.bookId, l.content, b.isBaseBook FROM line l "
            "JOIN book b ON b.id = l.bookId WHERE l.id % ? = ? ORDER BY l.id",
            (args.gpus, args.gpu),
        )
        batch: list[tuple[int, int, int, str]] = []
        scanned = 0
        for line_id, book_id, content, is_base in rows:
            scanned += 1
            normalized = clean(content or "")
            if normalized:
                batch.append((line_id, book_id, is_base, normalized))
            if len(batch) >= args.batch:
                encode_batch(batch)
                batch.clear()
            if scanned % 100_000 == 0:
                print(f"worker {args.gpu}: scanned {scanned}, indexed {sum(counts.values())}, "
                      f"elapsed {time.monotonic() - started:.0f}s", flush=True)
        if batch:
            encode_batch(batch)
    finally:
        connection.close()
        for output in outputs.values():
            output.close()
    for shard, count in counts.items():
        assert (args.output / f"shard-{shard:02d}.bin").stat().st_size == count * RECORD_BYTES
    (args.output / f"worker-{args.gpu}.json").write_text(
        json.dumps({"scanned": scanned, "counts": counts}, indent=2) + "\n", encoding="utf-8",
    )
    print(f"worker {args.gpu}: completed {sum(counts.values())} vectors", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--db", type=Path, required=True)
    parser.add_argument("--checkpoint", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--gpu", type=int, required=True)
    parser.add_argument("--gpus", type=int, default=2)
    parser.add_argument("--shards", type=int, default=8)
    parser.add_argument("--batch", type=int, default=128)
    parser.add_argument("--amp", action="store_true")
    parser.add_argument("--onnx", type=Path)
    run(parser.parse_args())
