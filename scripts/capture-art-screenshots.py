#!/usr/bin/env python3
"""Interactively capture the real Zayit window for the website screenshot set.

This recorder deliberately captures a native application window, rather than a
Compose test surface.  That keeps the real title bar, system buttons, tab titles,
font rendering, transient menus and platform decoration in the image.
"""

from __future__ import annotations

import argparse
import ctypes
import hashlib
import json
import msvcrt
import os
import shutil
import subprocess
import sys
import tempfile
import time
import uuid
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path

try:
    from PIL import Image, ImageChops
except ImportError as error:  # pragma: no cover - depends on the operator machine
    raise SystemExit("Pillow is required. Install it with: python -m pip install Pillow") from error


def configure_utf8_stdio() -> None:
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if reconfigure is not None:
            reconfigure(encoding="utf-8", errors="backslashreplace")


configure_utf8_stdio()


WIDTH = 1463
HEIGHT = 811
SOURCE_SCALE = 1
SOURCE_WIDTH = WIDTH * SOURCE_SCALE
SOURCE_HEIGHT = HEIGHT * SOURCE_SCALE
RESIZE_SETTLE_SECONDS = 1.0
SW_RESTORE = 9
SWP_NOACTIVATE = 0x0010
DWMWA_EXTENDED_FRAME_BOUNDS = 9
HWND_TOP = 0
PW_RENDERFULLCONTENT = 0x00000002
BI_RGB = 0
DIB_RGB_COLORS = 0


@dataclass(frozen=True)
class Scenario:
    stem: str
    title: str
    instructions: tuple[str, ...]


SCENARIOS = (
    Scenario(
        "HOME",
        "דף הבית",
        (
            "בחר בטאב דף הבית.",
            "השאר פתוחים גם הטאבים של בראשית פרק א, ברכות דף ב ושולחן ערוך אורח חיים סימן א.",
            "ודא שרשימת הספרים מימין מלאה ושהדף נמצא בראשו.",
        ),
    ),
    Scenario(
        "DB-SEARCH-SIMPLE",
        "חיפוש פשוט במסד",
        (
            "חפש: לחתוך צנון בסכין בשרי",
            "הצג את תוצאות החיפוש הפשוטות, לאחר שהרשימה סיימה להיטען.",
        ),
    ),
    Scenario(
        "DB-SEARCH-ADVANCED",
        "חיפוש מתקדם במסד",
        (
            "חפש: לחתוך צנון בסכין בשרי",
            "פתח את אפשרויות החיפוש המתקדם כמו בתמונת המקור והמתן לתוצאות.",
        ),
    ),
    Scenario(
        "BOOK-SEARCH",
        "השלמה אוטומטית של ספר",
        (
            "חזור לדף הבית ובחר בחיפוש לפי ספר/מקור.",
            "הקלד: שוע יו\"ד, והשאר את רשימת ההצעות פתוחה.",
        ),
    ),
    Scenario(
        "TOC-BOOK-SEARCH",
        "השלמה אוטומטית בתוכן עניינים",
        (
            "בדף הבית בחר תחילה את שולחן ערוך יורה דעה.",
            "הקלד: פו, והשאר את הצעות הסימנים פתוחות.",
        ),
    ),
    Scenario(
        "INBOOK-SEARCH",
        "חיפוש בתוך ספר",
        (
            "פתח ברכות בדף ב.",
            "פתח חיפוש בתוך הספר וחפש: שמע",
            "המתן עד שרשימת התוצאות מלאה.",
        ),
    ),
    Scenario(
        "PIRUSHIM",
        "פירושים",
        (
            "פתח ברכות בדף ב.",
            "פתח את חלונית הפירושים ובחר את אותם מפרשים שמופיעים בתמונת המקור.",
            "כוון את הגלילה כך שהקטע והחלוניות תואמים למקור.",
        ),
    ),
    Scenario(
        "PIRUSHIM-TARGUMIM",
        "פירושים ותרגומים",
        (
            "פתח בראשית פרק א.",
            "פתח את חלוניות הפירושים והתרגומים כמו בתמונת המקור.",
            "כוון את הגלילה והמחיצות לאותו קנה מידה.",
        ),
    ),
    Scenario(
        "MEKOR",
        "מקור",
        (
            "פתח את המקור שמופיע בתמונת המקור.",
            "פתח את חלונית המקורות וכוונן את המחיצות והגלילה בהתאם.",
        ),
    ),
    Scenario(
        "CLIPBOARD-DEMO",
        "בחירת טקסט ותפריט הקשר",
        (
            "פתח ברכות בדף ב והצג את אותן חלוניות כמו במקור.",
            "סמן את אותו קטע טקסט ופתח עליו את תפריט הלחיצה הימנית.",
        ),
    ),
)


class Rect(ctypes.Structure):
    _fields_ = (("left", ctypes.c_long), ("top", ctypes.c_long), ("right", ctypes.c_long), ("bottom", ctypes.c_long))

    @property
    def width(self) -> int:
        return self.right - self.left

    @property
    def height(self) -> int:
        return self.bottom - self.top


class BitmapInfoHeader(ctypes.Structure):
    _fields_ = (
        ("biSize", ctypes.c_uint32),
        ("biWidth", ctypes.c_long),
        ("biHeight", ctypes.c_long),
        ("biPlanes", ctypes.c_ushort),
        ("biBitCount", ctypes.c_ushort),
        ("biCompression", ctypes.c_uint32),
        ("biSizeImage", ctypes.c_uint32),
        ("biXPelsPerMeter", ctypes.c_long),
        ("biYPelsPerMeter", ctypes.c_long),
        ("biClrUsed", ctypes.c_uint32),
        ("biClrImportant", ctypes.c_uint32),
    )


class BitmapInfo(ctypes.Structure):
    _fields_ = (("bmiHeader", BitmapInfoHeader), ("bmiColors", ctypes.c_uint32 * 3))

user32 = ctypes.windll.user32
dwmapi = ctypes.windll.dwmapi
gdi32 = ctypes.windll.gdi32


def enable_dpi_awareness() -> None:
    try:
        user32.SetProcessDpiAwarenessContext(ctypes.c_void_p(-4))
    except (AttributeError, OSError):
        pass


def primary_display_size() -> tuple[int, int]:
    return user32.GetSystemMetrics(0), user32.GetSystemMetrics(1)


def window_title(hwnd: int) -> str:
    length = user32.GetWindowTextLengthW(hwnd)
    buffer = ctypes.create_unicode_buffer(length + 1)
    user32.GetWindowTextW(hwnd, buffer, len(buffer))
    return buffer.value.strip()


def visible_windows() -> list[tuple[int, str]]:
    windows: list[tuple[int, str]] = []
    callback_type = ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.c_void_p, ctypes.c_void_p)

    def visit(raw_hwnd: int, _parameter: int) -> bool:
        hwnd = int(raw_hwnd)
        if user32.IsWindowVisible(hwnd):
            title = window_title(hwnd)
            rect = Rect()
            if title and user32.GetWindowRect(hwnd, ctypes.byref(rect)) and rect.width >= 600 and rect.height >= 400:
                windows.append((hwnd, title))
        return True

    user32.EnumWindows(callback_type(visit), 0)
    return windows


def choose_window(title_hint: str) -> int:
    windows = visible_windows()
    matching = [(hwnd, title) for hwnd, title in windows if title_hint.casefold() in title.casefold()]
    candidates = matching or windows
    if not candidates:
        raise SystemExit("לא נמצא חלון מתאים. פתח את זית והרץ שוב.")
    if len(matching) == 1:
        hwnd, title = matching[0]
        print(f"נבחר חלון זית: {title}")
        return hwnd

    print("בחר את חלון זית:")
    for index, (_hwnd, title) in enumerate(candidates, start=1):
        print(f"  {index}. {title}")
    while True:
        answer = input("מספר החלון: ").strip()
        if answer.isdigit() and 1 <= int(answer) <= len(candidates):
            return candidates[int(answer) - 1][0]


def get_window_rect(hwnd: int) -> Rect:
    rect = Rect()
    if not user32.GetWindowRect(hwnd, ctypes.byref(rect)):
        raise ctypes.WinError()
    return rect


def get_frame_rect(hwnd: int) -> Rect:
    rect = Rect()
    result = dwmapi.DwmGetWindowAttribute(
        hwnd,
        DWMWA_EXTENDED_FRAME_BOUNDS,
        ctypes.byref(rect),
        ctypes.sizeof(rect),
    )
    return rect if result == 0 else get_window_rect(hwnd)


def normalize_window(
    hwnd: int,
    width: int = SOURCE_WIDTH,
    height: int = SOURCE_HEIGHT,
) -> Rect:
    user32.ShowWindow(hwnd, SW_RESTORE)
    outer = get_window_rect(hwnd)
    frame = get_frame_rect(hwnd)
    invisible_width = outer.width - frame.width
    invisible_height = outer.height - frame.height
    if not user32.SetWindowPos(
        hwnd,
        HWND_TOP,
        0,
        0,
        width + invisible_width,
        height + invisible_height,
        SWP_NOACTIVATE,
    ):
        raise ctypes.WinError()
    user32.SetForegroundWindow(hwnd)
    time.sleep(RESIZE_SETTLE_SECONDS)
    frame = get_frame_rect(hwnd)
    if (frame.width, frame.height) != (width, height):
        raise RuntimeError(f"גודל החלון הוא {frame.width}x{frame.height}; נדרש {width}x{height}")
    return frame


def capture_native_window(hwnd: int) -> tuple[Image.Image, Rect]:
    frame = normalize_window(hwnd)
    outer = get_window_rect(hwnd)
    window_dc = user32.GetWindowDC(hwnd)
    memory_dc = gdi32.CreateCompatibleDC(window_dc)
    bitmap = gdi32.CreateCompatibleBitmap(window_dc, outer.width, outer.height)
    previous = gdi32.SelectObject(memory_dc, bitmap)
    try:
        if not user32.PrintWindow(hwnd, memory_dc, PW_RENDERFULLCONTENT):
            raise ctypes.WinError()

        header = BitmapInfoHeader()
        header.biSize = ctypes.sizeof(BitmapInfoHeader)
        header.biWidth = outer.width
        header.biHeight = -outer.height
        header.biPlanes = 1
        header.biBitCount = 32
        header.biCompression = BI_RGB
        info = BitmapInfo()
        info.bmiHeader = header
        pixels = ctypes.create_string_buffer(outer.width * outer.height * 4)
        copied = gdi32.GetDIBits(
            memory_dc,
            bitmap,
            0,
            outer.height,
            pixels,
            ctypes.byref(info),
            DIB_RGB_COLORS,
        )
        if copied != outer.height:
            raise ctypes.WinError()
        image = Image.frombuffer("RGB", (outer.width, outer.height), pixels, "raw", "BGRX", 0, 1).copy()
        crop_left = frame.left - outer.left
        crop_top = frame.top - outer.top
        image = image.crop((crop_left, crop_top, crop_left + frame.width, crop_top + frame.height))
        return image, frame
    finally:
        gdi32.SelectObject(memory_dc, previous)
        gdi32.DeleteObject(bitmap)
        gdi32.DeleteDC(memory_dc)
        user32.ReleaseDC(hwnd, window_dc)


def validate_rendered_surface(source: Image.Image) -> None:
    """Reject PrintWindow captures whose Skia surface covers only part of the HWND."""
    rgb = source.convert("RGB")
    painted_bounds = ImageChops.difference(rgb, Image.new("RGB", rgb.size)).getbbox()
    if painted_bounds is None:
        raise RuntimeError("PrintWindow returned an entirely black image")
    painted_width = painted_bounds[2] - painted_bounds[0]
    painted_height = painted_bounds[3] - painted_bounds[1]
    if painted_width < source.width * 0.9 or painted_height < source.height * 0.9:
        raise RuntimeError(
            f"Skia rendered only {painted_width}x{painted_height} of the "
            f"{source.width}x{source.height} native frame",
        )


def crop_to_target_aspect(source: Image.Image) -> Image.Image:
    """Remove only the few DWM boundary pixels around the direct-size frame."""
    if source.width < WIDTH or source.height < HEIGHT:
        raise RuntimeError(
            f"Native frame {source.width}x{source.height} is smaller than {WIDTH}x{HEIGHT}",
        )
    excess_width = source.width - WIDTH
    excess_height = source.height - HEIGHT
    if excess_width > 4 or excess_height > 4:
        raise RuntimeError(
            f"Native frame {source.width}x{source.height} is not the direct {WIDTH}x{HEIGHT} frame",
        )
    left = excess_width // 2
    top = excess_height // 2
    return source.crop((left, top, left + WIDTH, top + HEIGHT))


def capture(hwnd: int, output: Path) -> tuple[str, Rect]:
    source, frame = capture_native_window(hwnd)
    validate_rendered_surface(source)
    source = crop_to_target_aspect(source)
    image = source
    output.parent.mkdir(parents=True, exist_ok=True)
    image.save(output, "PNG", optimize=True)
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    return digest, frame

def countdown(seconds: int) -> None:
    deadline = time.monotonic() + seconds
    shown = None
    while True:
        remaining = max(0, int(deadline - time.monotonic() + 0.999))
        if remaining != shown:
            print(f"\rהצילום יתבצע בעוד {remaining:2d} שניות (Enter = עכשיו)...", end="", flush=True)
            shown = remaining
        if msvcrt.kbhit() and msvcrt.getwch() in ("\r", "\n"):
            break
        if time.monotonic() >= deadline:
            break
        time.sleep(0.05)
    print("\rמצלם עכשיו...                                      ")

def git_revision(repo: Path) -> str | None:
    result = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=repo,
        check=False,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip() if result.returncode == 0 else None


def send_bridge_command(
    bridge_directory: Path,
    action: str,
    snapshot: Path,
    timeout: float = 15.0,
) -> None:
    bridge_directory.mkdir(parents=True, exist_ok=True)
    request_id = uuid.uuid4().hex
    temporary = bridge_directory / f"{request_id}.tmp"
    request = bridge_directory / f"{request_id}.request"
    response = bridge_directory / f"{request_id}.response"
    temporary.write_text(f"{action}\t{snapshot.resolve()}", encoding="utf-8")
    temporary.replace(request)

    deadline = time.monotonic() + timeout
    while not response.exists():
        if time.monotonic() >= deadline:
            request.unlink(missing_ok=True)
            temporary.unlink(missing_ok=True)
            raise TimeoutError(
                "זית לא אישרה את שמירת המצב. הפעל אותה עם ZAYIT_SCREENSHOT_BRIDGE_DIR "
                "זהה לתיקיית ה-bridge של הכלי.",
            )
        time.sleep(0.05)
    result = response.read_text(encoding="utf-8")
    response.unlink()
    if result != "ok":
        raise RuntimeError(f"שמירת המצב הפנימי נכשלה: {result}")

def matching_windows(title_hint: str) -> list[tuple[int, str]]:
    return [
        (hwnd, title)
        for hwnd, title in visible_windows()
        if title_hint.casefold() in title.casefold()
    ]


def wait_for_window(
    title_hint: str,
    timeout: float = 600.0,
    process: subprocess.Popen[bytes] | None = None,
    error_log: Path | None = None,
) -> int:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if process is not None and process.poll() is not None:
            details = ""
            if error_log is not None and error_log.is_file():
                details = error_log.read_text(encoding="utf-8", errors="replace").strip()
            message = f"הפעלת זית נכשלה (קוד יציאה {process.returncode})."
            if details:
                message += f"\n\n{details}"
            raise RuntimeError(message)
        matches = matching_windows(title_hint)
        if len(matches) == 1:
            print(f"נבחר חלון זית: {matches[0][1]}")
            return matches[0][0]
        if len(matches) > 1:
            raise RuntimeError(f"נמצאו {len(matches)} חלונות זית; השאר חלון אחד בלבד.")
        time.sleep(1)
    raise TimeoutError("חלון זית לא הופיע בתוך עשר דקות.")


def configure_java_home(environment: dict[str, str]) -> None:
    configured = environment.get("JAVA_HOME")
    if configured and (Path(configured) / "bin" / "java.exe").is_file():
        return
    candidates = [
        Path.home() / ".gradle" / "jdks",
        Path(r"C:\Program Files\Android\Android Studio\jbr"),
    ]
    for candidate in candidates:
        if (candidate / "bin" / "java.exe").is_file():
            environment["JAVA_HOME"] = str(candidate)
            return
        if candidate.is_dir():
            for child in sorted(candidate.iterdir(), reverse=True):
                if (child / "bin" / "java.exe").is_file():
                    environment["JAVA_HOME"] = str(child)
                    return
    raise RuntimeError(
        "לא נמצא JDK. התקן Java או הגדר JAVA_HOME לפני הפעלת המקליט.",
    )


def launch_app(
    repo: Path,
    build_root: Path,
    bridge_directory: Path,
    output: Path,
) -> subprocess.Popen[bytes]:
    existing = matching_windows("זית")
    if existing:
        raise RuntimeError("זית כבר פתוחה. סגור אותה לפני הרצה עם --launch-app.")
    if build_root == repo and "onedrive" in str(repo).casefold():
        staged_root = Path(tempfile.gettempdir()) / f"zayit-art-recorder-source-{uuid.uuid4().hex}"
        print(f"מעתיק את קוד המקור מחוץ ל-OneDrive: {staged_root}")
        result = subprocess.run(
            [
                "robocopy",
                str(repo),
                str(staged_root),
                "/E",
                "/COPY:DAT",
                "/DCOPY:DAT",
                "/R:2",
                "/W:1",
                "/XD",
                ".git",
                ".gradle",
                "build",
                "node_modules",
                ".idea",
                ".kotlin",
                "capture-preview",
                "/XF",
                "local.properties",
            ],
            check=False,
        )
        if result.returncode > 7:
            raise RuntimeError(f"robocopy נכשל עם קוד {result.returncode}")
        build_root = staged_root
    gradle = build_root / "gradlew.bat"
    if not gradle.is_file():
        raise FileNotFoundError(f"לא נמצא gradlew.bat ב-{build_root}")
    environment = os.environ.copy()
    configure_java_home(environment)
    environment["ZAYIT_SCREENSHOT_BRIDGE_DIR"] = str(bridge_directory)
    environment["ZAYIT_SCREENSHOT_LOGICAL_WIDTH"] = str(WIDTH)
    environment["ZAYIT_SCREENSHOT_LOGICAL_HEIGHT"] = str(HEIGHT)
    environment["J2D_UISCALE"] = str(SOURCE_SCALE)
    output.mkdir(parents=True, exist_ok=True)
    stdout_path = output / "zayit-run.log"
    stderr_path = output / "zayit-run.err.log"
    with stdout_path.open("wb") as stdout, stderr_path.open("wb") as stderr:
        return subprocess.Popen(
            [str(gradle), ":SeforimApp:run", "--no-daemon", "--console=plain"],
            cwd=build_root,
            env=environment,
            stdout=stdout,
            stderr=stderr,
            creationflags=subprocess.CREATE_NO_WINDOW,
        )

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--window-title", default="זית", help="טקסט שמופיע בכותרת חלון זית")
    parser.add_argument("--output", type=Path, help="תיקיית תצוגה מקדימה")
    parser.add_argument("--scenario", choices=[scenario.stem for scenario in SCENARIOS])
    parser.add_argument("--bridge-dir", type=Path, help="תיקיית התקשורת עם זית")
    parser.add_argument("--launch-app", action="store_true", help="הפעל את זית דרך Gradle במצב הקלטה")
    parser.add_argument("--build-root", type=Path, help="שורש בנייה חלופי (למשל עותק מחוץ ל-OneDrive)")
    parser.add_argument("--restore-fixture", type=Path, help="שחזר snapshot פנימי וצא")
    parser.add_argument("--delay", type=int, default=10, help="מספר השניות בין Enter לצילום (ברירת מחדל: 10)")
    parser.add_argument("--publish", action="store_true", help="העתק בסיום אל art ואל website/public/art")
    return parser.parse_args()


def main() -> int:
    if sys.platform != "win32":
        raise SystemExit("הכלי מצלם עיטורי חלון של Windows ולכן חייב לרוץ ב-Windows.")
    args = parse_args()
    repo = Path(__file__).resolve().parents[1]
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    output = (args.output or repo / "art" / "capture-preview" / timestamp).resolve()
    scenarios = [scenario for scenario in SCENARIOS if args.scenario in (None, scenario.stem)]
    bridge_directory = (args.bridge_dir or output / "bridge").resolve()
    enable_dpi_awareness()
    if args.launch_app:
        build_root = (args.build_root or repo).resolve()
        process = launch_app(repo, build_root, bridge_directory, output)
        hwnd = wait_for_window(
            args.window_title,
            process=process,
            error_log=output / "zayit-run.err.log",
        )
    else:
        configured_bridge = os.getenv("ZAYIT_SCREENSHOT_BRIDGE_DIR")
        if configured_bridge:
            bridge_directory = Path(configured_bridge).resolve()
        elif args.bridge_dir is None and not args.restore_fixture:
            raise RuntimeError(
                "המצב הפנימי מחייב להפעיל את זית דרך --launch-app או עם "
                "ZAYIT_SCREENSHOT_BRIDGE_DIR.",
            )
        hwnd = choose_window(args.window_title)
    if args.restore_fixture:
        send_bridge_command(bridge_directory, "restore", args.restore_fixture)
        print(f"שוחזר המצב הפנימי מ-{args.restore_fixture}")
        return 0
    manifest: dict[str, object] = {
        "version": 1,
        "capturedAt": datetime.now(timezone.utc).isoformat(),
        "gitRevision": git_revision(repo),
        "windowTitle": window_title(hwnd),
        "width": WIDTH,
        "height": HEIGHT,
        "captures": [],
    }

    visual_settings = output / "fixtures" / "visual-settings.properties"
    send_bridge_command(bridge_directory, "record-settings", visual_settings)
    manifest["visualSettings"] = {
        "file": str(visual_settings.relative_to(output)),
        "sha256": hashlib.sha256(visual_settings.read_bytes()).hexdigest(),
    }
    print(f"\nהצילומים יישמרו זמנית ב: {output}")
    for index, scenario in enumerate(scenarios, start=1):
        print(f"\n[{index}/{len(scenarios)}] {scenario.title} ({scenario.stem})")
        for instruction in scenario.instructions:
            print(f"  • {instruction}")
        reference = repo / "art" / f"{scenario.stem}-LIGHT.png"
        print(f"  תמונת ייחוס ישנה: {reference}")
        input("שחזר את המצב בערכת הנושא הבהירה, המתן לטעינה מלאה ולחץ Enter כאן...")
        fixture = output / "fixtures" / f"{scenario.stem}.pb"
        send_bridge_command(bridge_directory, "record", fixture)
        fixture_digest = hashlib.sha256(fixture.read_bytes()).hexdigest()
        manifest.setdefault("scenarios", []).append(
            {"stem": scenario.stem, "fixture": str(fixture.relative_to(output)), "sha256": fixture_digest},
        )

        for theme, prompt in (
            ("LIGHT", None),
            ("DARK", "החלף רק לערכת הנושא הכהה, אל תשנה את מצב המסך, ואז לחץ Enter..."),
        ):
            if prompt:
                input(prompt)
            countdown(args.delay)
            name = f"{scenario.stem}-{theme}.png"
            digest, frame = capture(hwnd, output / name)
            manifest["captures"].append(
                {
                    "file": name,
                    "sha256": digest,
                    "windowTitle": window_title(hwnd),
                    "frame": {"left": frame.left, "top": frame.top, "width": frame.width, "height": frame.height},
                }
            )
            print(f"  נשמר: {name}")

    manifest_path = output / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    expected_count = len(scenarios) * 2
    generated = list(output.glob("*.png"))
    if len(generated) != expected_count:
        raise RuntimeError(f"נוצרו {len(generated)} תמונות במקום {expected_count}")

    if args.publish:
        if len(scenarios) != len(SCENARIOS):
            raise RuntimeError("לא ניתן לפרסם הרצה חלקית; הקלט את כל עשרת התרחישים.")
        for target in (repo / "art", repo / "website" / "public" / "art"):
            target.mkdir(parents=True, exist_ok=True)
            for image in generated:
                shutil.copy2(image, target / image.name)
        fixture_target = repo / "SeforimApp" / "src" / "jvmTest" / "resources" / "website-screenshots"
        fixture_target.mkdir(parents=True, exist_ok=True)
        for fixture in (output / "fixtures").glob("*.pb"):
            shutil.copy2(fixture, fixture_target / fixture.name)
        shutil.copy2(visual_settings, fixture_target / visual_settings.name)
        shutil.copy2(manifest_path, fixture_target / "manifest.json")
        print("הצילומים הועתקו לשתי תיקיות art והמצבים הפנימיים פורסמו כ-test resources.")
    else:
        print(f"\nהסתיים. בדוק את התמונות ב-{output}")
        print("לא הוחלפו קבצי האתר. לאחר בדיקה הרץ שוב עם --publish.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
