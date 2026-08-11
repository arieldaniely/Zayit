#!/usr/bin/env python3
"""Replay recorded Zayit states in a native Windows window and publish normalized PNGs."""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
import subprocess
import sys
import time
import uuid
from pathlib import Path

from PIL import Image

TARGET_WIDTH = 1463
TARGET_HEIGHT = 811
SOURCE_WIDTH = TARGET_WIDTH
SOURCE_HEIGHT = TARGET_HEIGHT
EXPECTED_DENSITY = 1.0
STEMS = (
    "HOME", "DB-SEARCH-SIMPLE", "DB-SEARCH-ADVANCED", "BOOK-SEARCH",
    "TOC-BOOK-SEARCH", "INBOOK-SEARCH", "PIRUSHIM", "PIRUSHIM-TARGUMIM",
    "MEKOR", "CLIPBOARD-DEMO",
)


def load_windows_capture_module(repo: Path):
    path = repo / "scripts" / "capture-art-screenshots.py"
    spec = importlib.util.spec_from_file_location("zayit_windows_capture", path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Could not load {path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def bridge_command(bridge: Path, action: str, argument: str, timeout: float = 60.0) -> None:
    bridge.mkdir(parents=True, exist_ok=True)
    request_id = f"{time.time_ns()}-{uuid.uuid4().hex}"
    temporary = bridge / f"{request_id}.request.tmp"
    request = bridge / f"{request_id}.request"
    response = bridge / f"{request_id}.response"
    temporary.write_text(f"{action}\t{argument}", encoding="utf-8")
    os.replace(temporary, request)
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if response.is_file():
            result = response.read_text(encoding="utf-8")
            response.unlink()
            if result != "ok":
                raise RuntimeError(f"Zayit bridge rejected {action}: {result}")
            return
        time.sleep(0.1)
    raise TimeoutError(f"Timed out waiting for Zayit bridge command {action}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixtures", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--profile", type=Path, required=True)
    parser.add_argument("--window-title", default="זית")
    parser.add_argument("--settle", type=float, default=5.0)
    parser.add_argument("--publish", action="store_true")
    return parser.parse_args()


def require_source_frame(capture_tools, hwnd: int):
    frame = capture_tools.get_frame_rect(hwnd)
    if frame.width < SOURCE_WIDTH or frame.height < SOURCE_HEIGHT:
        raise RuntimeError(
            f"Native frame is {frame.width}x{frame.height}; expected at least "
            f"{SOURCE_WIDTH}x{SOURCE_HEIGHT}. The window was not resized because "
            "resizing after Compose created its Skia surface produces black pixels."
        )
    capture_tools.crop_to_target_aspect(Image.new("RGB", (frame.width, frame.height)))
    return frame


def main() -> int:
    if os.name != "nt":
        raise SystemExit("This replay tool requires Windows so screenshots contain real Windows controls.")
    args = parse_args()
    repo = Path(__file__).resolve().parents[1]
    fixtures = args.fixtures.resolve()
    output = args.output.resolve()
    profile = args.profile.resolve()
    output.mkdir(parents=True, exist_ok=True)
    profile.mkdir(parents=True, exist_ok=True)
    bridge = output / "bridge"

    capture_tools = load_windows_capture_module(repo)
    capture_tools.enable_dpi_awareness()
    display_width, display_height = capture_tools.primary_display_size()
    if display_width < SOURCE_WIDTH or display_height < SOURCE_HEIGHT:
        raise RuntimeError(
            f"Windows display is {display_width}x{display_height}; "
            f"a real display of at least {SOURCE_WIDTH}x{SOURCE_HEIGHT} is required",
        )

    environment = os.environ.copy()
    environment.update({
        "ZAYIT_SCREENSHOT_BRIDGE_DIR": str(bridge),
        "ZAYIT_SCREENSHOT_LOGICAL_WIDTH": str(SOURCE_WIDTH),
        "ZAYIT_SCREENSHOT_LOGICAL_HEIGHT": str(SOURCE_HEIGHT),
        "SEFORIMAPP_PORTABLE": "1",
        "SEFORIMAPP_PORTABLE_DIR": str(profile),
    })
    log_path = output / "zayit-run.log"
    with log_path.open("wb") as log:
        process = subprocess.Popen(
            [str(repo / "gradlew.bat"), ":SeforimApp:run", "--no-daemon", "--console=plain"],
            cwd=repo,
            env=environment,
            stdout=log,
            stderr=subprocess.STDOUT,
            creationflags=subprocess.CREATE_NO_WINDOW,
        )
        try:
            hwnd = capture_tools.wait_for_window(args.window_title, 600.0, process, log_path)
            bridge_command(bridge, "verify-platform", "windows")
            bridge_command(bridge, "verify-density", str(EXPECTED_DENSITY))
            native_frame = require_source_frame(capture_tools, hwnd)

            # The app is born at this size. Captures only verify it and never resize it.
            def verify_window(current_hwnd: int, width: int = TARGET_WIDTH, height: int = TARGET_HEIGHT):
                frame = capture_tools.get_frame_rect(current_hwnd)
                if (frame.width, frame.height) != (native_frame.width, native_frame.height):
                    raise RuntimeError(
                        f"Native frame changed from {native_frame.width}x{native_frame.height} "
                        f"to {frame.width}x{frame.height}. "
                        "Refusing to resize because that would destabilize Compose layout."
                    )
                capture_tools.user32.SetForegroundWindow(current_hwnd)
                return frame

            capture_tools.normalize_window = verify_window
            settings = fixtures / "visual-settings.properties"
            if not settings.is_file():
                raise FileNotFoundError(f"missing visual settings: {settings}")
            bridge_command(bridge, "settings", str(settings))
            time.sleep(args.settle)

            captures: list[dict[str, str]] = []
            for stem in STEMS:
                fixture = fixtures / f"{stem}.pb"
                if not fixture.is_file():
                    raise FileNotFoundError(f"missing fixture: {fixture}")
                bridge_command(bridge, "restore", str(fixture))
                bridge_command(bridge, "scenario", stem, timeout=120.0)
                for theme in ("LIGHT", "DARK"):
                    bridge_command(bridge, "theme", theme.lower())
                    if stem == "CLIPBOARD-DEMO":
                        frame = capture_tools.get_frame_rect(hwnd)
                        capture_tools.user32.SetCursorPos(frame.left + 1030, frame.top + 285)
                        bridge_command(bridge, "clipboard-demo", "open")
                    time.sleep(args.settle)
                    name = f"{stem}-{theme}.png"
                    digest, _frame = capture_tools.capture(hwnd, output / name)
                    captures.append({"file": name, "sha256": digest})
                    print(f"captured {name}", flush=True)

            manifest = {
                "version": 5,
                "platform": "windows",
                "logicalSize": [TARGET_WIDTH, TARGET_HEIGHT],
                "displaySize": [display_width, display_height],
                "sourceSize": [native_frame.width, native_frame.height],
                "targetSize": [TARGET_WIDTH, TARGET_HEIGHT],
                "uiScale": EXPECTED_DENSITY,
                "captures": captures,
            }
            (output / "manifest.json").write_text(
                json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
            )
            if args.publish:
                for target in (repo / "art", repo / "website" / "public" / "art"):
                    target.mkdir(parents=True, exist_ok=True)
                    for capture_info in captures:
                        source = output / capture_info["file"]
                        (target / source.name).write_bytes(source.read_bytes())
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
