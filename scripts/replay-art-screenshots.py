#!/usr/bin/env python3
"""Replay recorded Zayit states on a fixed X11 display and publish normalized PNGs."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import signal
import subprocess
import time
import uuid
from pathlib import Path

from PIL import Image

SOURCE_WIDTH = 2926
SOURCE_HEIGHT = 1622
TARGET_WIDTH = 1463
TARGET_HEIGHT = 811
STEMS = (
    "HOME",
    "DB-SEARCH-SIMPLE",
    "DB-SEARCH-ADVANCED",
    "BOOK-SEARCH",
    "TOC-BOOK-SEARCH",
    "INBOOK-SEARCH",
    "PIRUSHIM",
    "PIRUSHIM-TARGUMIM",
    "MEKOR",
    "CLIPBOARD-DEMO",
)


def run_text(command: list[str]) -> str:
    return subprocess.check_output(command, text=True, encoding="utf-8").strip()


def display_dimensions() -> tuple[int, int]:
    for line in run_text(["xdpyinfo"]).splitlines():
        if "dimensions:" in line:
            value = line.split("dimensions:", 1)[1].strip().split()[0]
            width, height = value.split("x", 1)
            return int(width), int(height)
    raise RuntimeError("xdpyinfo did not report display dimensions")


def window_geometry(window_id: str) -> dict[str, int]:
    values: dict[str, int] = {}
    for line in run_text(["xdotool", "getwindowgeometry", "--shell", window_id]).splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            if value.lstrip("-").isdigit():
                values[key] = int(value)
    return values


def find_window(title: str, timeout: float, app_process: subprocess.Popen[bytes]) -> str:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if app_process.poll() is not None:
            raise RuntimeError(f"Zayit exited before opening a window (exit {app_process.returncode})")
        result = subprocess.run(
            ["xdotool", "search", "--onlyvisible", "--name", title],
            text=True,
            encoding="utf-8",
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            check=False,
        )
        candidates = [item for item in result.stdout.splitlines() if item]
        if candidates:
            return max(
                candidates,
                key=lambda item: window_geometry(item).get("WIDTH", 0)
                * window_geometry(item).get("HEIGHT", 0),
            )
        time.sleep(0.5)
    raise TimeoutError(f"Zayit window containing {title!r} did not appear")


def bridge_command(bridge: Path, action: str, argument: str, timeout: float = 60.0) -> None:
    bridge.mkdir(parents=True, exist_ok=True)
    request_id = f"{time.time_ns()}-{uuid.uuid4().hex}"
    temporary = bridge / f"{request_id}.request.tmp"
    request = bridge / f"{request_id}.request"
    response = bridge / f"{request_id}.response"
    temporary.write_text(f"{action}	{argument}", encoding="utf-8")
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


def capture(window_id: str, destination: Path) -> str:
    raw = destination.with_suffix(".source.png")
    subprocess.run(["import", "-window", window_id, str(raw)], check=True)
    with Image.open(raw) as image:
        if image.size != (SOURCE_WIDTH, SOURCE_HEIGHT):
            raise RuntimeError(
                f"window is {image.width}x{image.height}; "
                f"expected fixed {SOURCE_WIDTH}x{SOURCE_HEIGHT}",
            )
        normalized = image.convert("RGB").resize(
            (TARGET_WIDTH, TARGET_HEIGHT),
            Image.Resampling.LANCZOS,
        )
        normalized.save(destination, format="PNG", optimize=True)
    raw.unlink()
    return hashlib.sha256(destination.read_bytes()).hexdigest()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixtures", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--profile", type=Path, required=True)
    parser.add_argument("--window-title", default="זית")
    parser.add_argument("--settle", type=float, default=5.0)
    parser.add_argument("--publish", action="store_true")
    return parser.parse_args()


def main() -> int:
    if os.name == "nt" or not os.environ.get("DISPLAY"):
        raise SystemExit("This replay tool requires a Linux X11 display (normally Xvfb).")
    args = parse_args()
    repo = Path(__file__).resolve().parents[1]
    fixtures = args.fixtures.resolve()
    output = args.output.resolve()
    profile = args.profile.resolve()
    output.mkdir(parents=True, exist_ok=True)
    profile.mkdir(parents=True, exist_ok=True)

    actual_display = display_dimensions()
    if actual_display != (SOURCE_WIDTH, SOURCE_HEIGHT):
        raise RuntimeError(
            f"X11 display is {actual_display[0]}x{actual_display[1]}; "
            f"expected {SOURCE_WIDTH}x{SOURCE_HEIGHT}",
        )

    bridge = output / "bridge"
    environment = os.environ.copy()
    environment.update(
        {
            "ZAYIT_SCREENSHOT_BRIDGE_DIR": str(bridge),
            "ZAYIT_SCREENSHOT_WIDTH": str(SOURCE_WIDTH),
            "ZAYIT_SCREENSHOT_HEIGHT": str(SOURCE_HEIGHT),
            "SEFORIMAPP_PORTABLE": "1",
            "SEFORIMAPP_PORTABLE_DIR": str(profile),
        },
    )
    log_path = output / "zayit-run.log"
    with log_path.open("wb") as log:
        process = subprocess.Popen(
            [
                str(repo / "gradlew"),
                ":SeforimApp:run",
                "--no-daemon",
                "--console=plain",
            ],
            cwd=repo,
            env=environment,
            stdout=log,
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
        try:
            window_id = find_window(args.window_title, 600.0, process)
            captures: list[dict[str, str]] = []
            for stem in STEMS:
                fixture = fixtures / f"{stem}.pb"
                if not fixture.is_file():
                    raise FileNotFoundError(f"missing fixture: {fixture}")
                bridge_command(bridge, "restore", str(fixture))
                for theme in ("LIGHT", "DARK"):
                    bridge_command(bridge, "theme", theme.lower())
                    time.sleep(args.settle)
                    window_id = find_window(args.window_title, 30.0, process)
                    name = f"{stem}-{theme}.png"
                    digest = capture(window_id, output / name)
                    captures.append({"file": name, "sha256": digest})
                    print(f"captured {name}", flush=True)

            manifest = {
                "version": 2,
                "sourceSize": [SOURCE_WIDTH, SOURCE_HEIGHT],
                "targetSize": [TARGET_WIDTH, TARGET_HEIGHT],
                "captures": captures,
            }
            (output / "manifest.json").write_text(
                json.dumps(manifest, ensure_ascii=False, indent=2) + chr(10),
                encoding="utf-8",
            )
            if args.publish:
                for target in (repo / "art", repo / "website" / "public" / "art"):
                    target.mkdir(parents=True, exist_ok=True)
                    for capture_info in captures:
                        source = output / capture_info["file"]
                        (target / source.name).write_bytes(source.read_bytes())
        finally:
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())