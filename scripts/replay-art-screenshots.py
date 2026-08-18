#!/usr/bin/env python3
"""Replay recorded Zayit states in a native Windows window and publish normalized PNGs."""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import math
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
DEFAULT_WINDOWS_DPI = 96
CONTROL_GLYPH_THRESHOLD = 25
CONTROL_GLYPH_PATCH_TOP = 8
CONTROL_GLYPH_PATCH_HEIGHT = 20
CONTROL_GLYPH_PATCH_HALF_WIDTH = 8
WINDOWS_11_CONTROL_GLYPHS = (
    ("minimize", 115, (4, 10, 14, 11), 10, 10),
    ("maximize", 69, (4, 5, 14, 15), 36, 36),
    ("close", 23, (4, 5, 14, 15), 18, 60),
)
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


def require_windows_11_control_pane(path: Path) -> None:
    """Verify the captured pixels contain the deterministic Windows 11 glyphs."""
    image = Image.open(path).convert("L")
    for name, offset, expected_bbox, minimum_pixels, maximum_pixels in WINDOWS_11_CONTROL_GLYPHS:
        centers_to_try = [image.width - offset, offset]
        last_error = None
        found_valid = False

        for center_x in centers_to_try:
            patch = image.crop(
                (
                    center_x - CONTROL_GLYPH_PATCH_HALF_WIDTH,
                    CONTROL_GLYPH_PATCH_TOP,
                    center_x + CONTROL_GLYPH_PATCH_HALF_WIDTH,
                    CONTROL_GLYPH_PATCH_TOP + CONTROL_GLYPH_PATCH_HEIGHT,
                ),
            )
            background = patch.getpixel((0, 0))
            glyph_pixels = [
                (x, y)
                for y in range(patch.height)
                for x in range(patch.width)
                if abs(patch.getpixel((x, y)) - background) > CONTROL_GLYPH_THRESHOLD
            ]
            if not glyph_pixels:
                last_error = f"Missing Windows 11 {name} glyph in {path.name}"
                continue
            actual_bbox = (
                min(x for x, _y in glyph_pixels),
                min(y for _x, y in glyph_pixels),
                max(x for x, _y in glyph_pixels) + 1,
                max(y for _x, y in glyph_pixels) + 1,
            )
            if actual_bbox != expected_bbox or not minimum_pixels <= len(glyph_pixels) <= maximum_pixels:
                last_error = (
                    f"Unexpected Windows caption rendering in {path.name}: {name} glyph "
                    f"bounds={actual_bbox}, pixels={len(glyph_pixels)}"
                )
                continue
            
            found_valid = True
            break
            
        if not found_valid:
            raise RuntimeError(last_error)


def primary_display_scale(capture_tools) -> float:
    dpi = int(capture_tools.user32.GetDpiForSystem())
    if dpi <= 0:
        raise RuntimeError(f"Windows returned an invalid system DPI: {dpi}")
    return dpi / DEFAULT_WINDOWS_DPI


def terminate_process_tree(process: subprocess.Popen[bytes]) -> None:
    """Stop Gradle and the JavaExec app it owns, including on a failed replay."""
    if process.poll() is not None:
        return
    subprocess.run(
        ["taskkill", "/PID", str(process.pid), "/T", "/F"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
        creationflags=subprocess.CREATE_NO_WINDOW,
    )
    try:
        process.wait(timeout=15)
    except subprocess.TimeoutExpired:
        process.kill()


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
    display_scale = primary_display_scale(capture_tools)
    window_width_dp = math.ceil(SOURCE_WIDTH / display_scale)
    window_height_dp = math.ceil(SOURCE_HEIGHT / display_scale)
    if display_width < SOURCE_WIDTH or display_height < SOURCE_HEIGHT:
        raise RuntimeError(
            f"Windows display is {display_width}x{display_height}; "
            f"a real display of at least {SOURCE_WIDTH}x{SOURCE_HEIGHT} is required",
        )

    environment = os.environ.copy()
    environment.update({
        "ZAYIT_SCREENSHOT_BRIDGE_DIR": str(bridge),
        "ZAYIT_SCREENSHOT_LOGICAL_WIDTH": str(window_width_dp),
        "ZAYIT_SCREENSHOT_LOGICAL_HEIGHT": str(window_height_dp),
        # Keep the native HWND size independent of the runner's display-scale setting.
        # Compose content is also rendered at density 1 below, so both layers stay 1:1.
        "J2D_UISCALE": str(EXPECTED_DENSITY),
        "SEFORIMAPP_PORTABLE": "1",
        "SEFORIMAPP_PORTABLE_DIR": str(profile),
    })
    gradle_properties = [
        f"-PzayitScreenshotBridgeDir={bridge}",
        f"-PzayitScreenshotLogicalWidth={window_width_dp}",
        f"-PzayitScreenshotLogicalHeight={window_height_dp}",
        f"-PzayitScreenshotPortableDir={profile}",
    ]
    database_path = environment.get("SEFORIMAPP_DATABASE_PATH")
    if database_path:
        gradle_properties.append(f"-PzayitScreenshotDatabasePath={database_path}")
    log_path = output / "zayit-run.log"
    existing_hwnds = {hwnd for hwnd, _title in capture_tools.matching_windows(args.window_title)}
    with log_path.open("wb") as log:
        process = subprocess.Popen(
            [
                str(repo / "gradlew.bat"),
                ":SeforimApp:run",
                "--no-daemon",
                "--no-configuration-cache",
                "--console=plain",
                *gradle_properties,
            ],
            cwd=repo,
            env=environment,
            stdout=log,
            stderr=subprocess.STDOUT,
            creationflags=subprocess.CREATE_NO_WINDOW,
        )
        try:
            hwnd = capture_tools.wait_for_window(
                args.window_title,
                600.0,
                process,
                log_path,
                exclude_hwnds=existing_hwnds,
            )
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
                    capture_path = output / name
                    digest, _frame = capture_tools.capture(hwnd, capture_path)
                    require_windows_11_control_pane(capture_path)
                    captures.append({"file": name, "sha256": digest})
                    print(f"captured {name}", flush=True)

            manifest = {
                "version": 6,
                "platform": "windows",
                "logicalSize": [TARGET_WIDTH, TARGET_HEIGHT],
                "displaySize": [display_width, display_height],
                "displayScale": display_scale,
                "windowStateSizeDp": [window_width_dp, window_height_dp],
                "sourceSize": [native_frame.width, native_frame.height],
                "targetSize": [TARGET_WIDTH, TARGET_HEIGHT],
                "uiScale": EXPECTED_DENSITY,
                "windowControls": "windows-11-compose-validated",
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
            terminate_process_tree(process)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
