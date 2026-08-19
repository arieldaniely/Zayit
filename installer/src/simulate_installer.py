import ctypes
import math
import os
import sys
import threading
import time
from ctypes import wintypes

from PIL import Image


# ============================================================
# Basic Win32 types
# ============================================================

HANDLE = ctypes.c_void_p

HWND = HANDLE
HDC = HANDLE
HBITMAP = HANDLE
HGDIOBJ = HANDLE
HICON = HANDLE
HCURSOR = HANDLE
HBRUSH = HANDLE
HMENU = HANDLE
HINSTANCE = HANDLE

WPARAM = ctypes.c_size_t
LPARAM = ctypes.c_ssize_t
LRESULT = ctypes.c_ssize_t


# ============================================================
# DLLs
# ============================================================

user32 = ctypes.WinDLL("user32", use_last_error=True)
gdi32 = ctypes.WinDLL("gdi32", use_last_error=True)
kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)


# ============================================================
# Constants
# ============================================================

#![windows_subsystem = "windows"]
# The real Rust executable uses a subsystem attribute.
# Python itself is not changed here.

WS_POPUP = 0x80000000

WS_EX_LAYERED = 0x00080000
WS_EX_TOOLWINDOW = 0x00000080

CS_HREDRAW = 0x0002
CS_VREDRAW = 0x0001

SW_SHOW = 5

PM_REMOVE = 0x0001

WM_DESTROY = 0x0002
WM_CLOSE = 0x0010
WM_QUIT = 0x0012

SM_CXSCREEN = 0
SM_CYSCREEN = 1

BI_RGB = 0
DIB_RGB_COLORS = 0

ULW_ALPHA = 0x00000002

AC_SRC_OVER = 0
AC_SRC_ALPHA = 1

IDC_ARROW = 32512

# DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2
DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2 = ctypes.c_void_p(-4)


# ============================================================
# Structures
# ============================================================

class POINT(ctypes.Structure):
    _fields_ = [
        ("x", wintypes.LONG),
        ("y", wintypes.LONG),
    ]


class SIZE(ctypes.Structure):
    _fields_ = [
        ("cx", wintypes.LONG),
        ("cy", wintypes.LONG),
    ]


class BLENDFUNCTION(ctypes.Structure):
    _fields_ = [
        ("BlendOp", wintypes.BYTE),
        ("BlendFlags", wintypes.BYTE),
        ("SourceConstantAlpha", wintypes.BYTE),
        ("AlphaFormat", wintypes.BYTE),
    ]


class BITMAPINFOHEADER(ctypes.Structure):
    _fields_ = [
        ("biSize", wintypes.DWORD),
        ("biWidth", wintypes.LONG),
        ("biHeight", wintypes.LONG),
        ("biPlanes", wintypes.WORD),
        ("biBitCount", wintypes.WORD),
        ("biCompression", wintypes.DWORD),
        ("biSizeImage", wintypes.DWORD),
        ("biXPelsPerMeter", wintypes.LONG),
        ("biYPelsPerMeter", wintypes.LONG),
        ("biClrUsed", wintypes.DWORD),
        ("biClrImportant", wintypes.DWORD),
    ]


class BITMAPINFO(ctypes.Structure):
    _fields_ = [
        ("bmiHeader", BITMAPINFOHEADER),
        ("bmiColors", wintypes.DWORD * 1),
    ]


class MSG(ctypes.Structure):
    _fields_ = [
        ("hwnd", HWND),
        ("message", wintypes.UINT),
        ("wParam", WPARAM),
        ("lParam", LPARAM),
        ("time", wintypes.DWORD),
        ("pt", POINT),
    ]


WNDPROC = ctypes.WINFUNCTYPE(
    LRESULT,
    HWND,
    wintypes.UINT,
    WPARAM,
    LPARAM,
)


class WNDCLASSW(ctypes.Structure):
    _fields_ = [
        ("style", wintypes.UINT),
        ("lpfnWndProc", WNDPROC),
        ("cbClsExtra", ctypes.c_int),
        ("cbWndExtra", ctypes.c_int),
        ("hInstance", HINSTANCE),
        ("hIcon", HICON),
        ("hCursor", HCURSOR),
        ("hbrBackground", HBRUSH),
        ("lpszMenuName", wintypes.LPCWSTR),
        ("lpszClassName", wintypes.LPCWSTR),
    ]


# ============================================================
# Win32 function prototypes
# ============================================================

kernel32.GetModuleHandleW.argtypes = [
    wintypes.LPCWSTR
]
kernel32.GetModuleHandleW.restype = HINSTANCE


user32.SetProcessDpiAwarenessContext.argtypes = [
    ctypes.c_void_p
]
user32.SetProcessDpiAwarenessContext.restype = wintypes.BOOL


user32.GetSystemMetrics.argtypes = [
    ctypes.c_int
]
user32.GetSystemMetrics.restype = ctypes.c_int


user32.RegisterClassW.argtypes = [
    ctypes.POINTER(WNDCLASSW)
]
user32.RegisterClassW.restype = wintypes.ATOM


user32.LoadCursorW.argtypes = [
    HINSTANCE,
    wintypes.LPCWSTR,
]
user32.LoadCursorW.restype = HCURSOR


user32.CreateWindowExW.argtypes = [
    wintypes.DWORD,
    wintypes.LPCWSTR,
    wintypes.LPCWSTR,
    wintypes.DWORD,
    ctypes.c_int,
    ctypes.c_int,
    ctypes.c_int,
    ctypes.c_int,
    HWND,
    HMENU,
    HINSTANCE,
    ctypes.c_void_p,
]
user32.CreateWindowExW.restype = HWND


user32.ShowWindow.argtypes = [
    HWND,
    ctypes.c_int,
]
user32.ShowWindow.restype = wintypes.BOOL


user32.IsWindow.argtypes = [
    HWND
]
user32.IsWindow.restype = wintypes.BOOL


user32.GetDC.argtypes = [
    HWND
]
user32.GetDC.restype = HDC


user32.ReleaseDC.argtypes = [
    HWND,
    HDC
]
user32.ReleaseDC.restype = ctypes.c_int


user32.PeekMessageW.argtypes = [
    ctypes.POINTER(MSG),
    HWND,
    wintypes.UINT,
    wintypes.UINT,
    wintypes.UINT,
]
user32.PeekMessageW.restype = wintypes.BOOL


user32.TranslateMessage.argtypes = [
    ctypes.POINTER(MSG)
]
user32.TranslateMessage.restype = wintypes.BOOL


user32.DispatchMessageW.argtypes = [
    ctypes.POINTER(MSG)
]
user32.DispatchMessageW.restype = LRESULT


user32.DefWindowProcW.argtypes = [
    HWND,
    wintypes.UINT,
    WPARAM,
    LPARAM,
]
user32.DefWindowProcW.restype = LRESULT


user32.PostQuitMessage.argtypes = [
    ctypes.c_int
]
user32.PostQuitMessage.restype = None


user32.DestroyWindow.argtypes = [
    HWND
]
user32.DestroyWindow.restype = wintypes.BOOL


user32.UpdateLayeredWindow.argtypes = [
    HWND,
    HDC,
    ctypes.POINTER(POINT),
    ctypes.POINTER(SIZE),
    HDC,
    ctypes.POINTER(POINT),
    wintypes.COLORREF,
    ctypes.POINTER(BLENDFUNCTION),
    wintypes.DWORD,
]
user32.UpdateLayeredWindow.restype = wintypes.BOOL


gdi32.CreateCompatibleDC.argtypes = [
    HDC
]
gdi32.CreateCompatibleDC.restype = HDC


gdi32.CreateDIBSection.argtypes = [
    HDC,
    ctypes.POINTER(BITMAPINFO),
    wintypes.UINT,
    ctypes.POINTER(ctypes.c_void_p),
    HANDLE,
    wintypes.DWORD,
]
gdi32.CreateDIBSection.restype = HBITMAP


gdi32.SelectObject.argtypes = [
    HDC,
    HGDIOBJ
]
gdi32.SelectObject.restype = HGDIOBJ


gdi32.DeleteObject.argtypes = [
    HGDIOBJ
]
gdi32.DeleteObject.restype = wintypes.BOOL


gdi32.DeleteDC.argtypes = [
    HDC
]
gdi32.DeleteDC.restype = wintypes.BOOL


# ============================================================
# Error helper
# ============================================================

def win32_error(message):
    error = ctypes.get_last_error()
    raise ctypes.WinError(error, message)


# ============================================================
# Window procedure
# ============================================================

@WNDPROC
def wnd_proc(hwnd, msg, wparam, lparam):

    if msg == WM_CLOSE:
        # Same as Rust:
        # ignore WM_CLOSE while installation is running.
        return 0

    if msg == WM_DESTROY:
        # Same as Rust:
        # do not PostQuitMessage here.
        return 0

    return user32.DefWindowProcW(
        hwnd,
        msg,
        wparam,
        lparam
    )


# ============================================================
# Splash simulator
# ============================================================

class ZayitaSplash:

    CLASS_NAME = "ZayitaSplash"

    def __init__(self):

        # ----------------------------------------------------
        # DPI
        # ----------------------------------------------------

        user32.SetProcessDpiAwarenessContext(
            DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2
        )

        # ----------------------------------------------------
        # Load splash image
        # ----------------------------------------------------

        base_dir = os.path.dirname(
            os.path.abspath(__file__)
        )

        image_path = os.path.join(
            "art",
            "splash.png"
        )

        if not os.path.isfile(image_path):
            raise FileNotFoundError(
                f"לא נמצא splash.png:\n{image_path}"
            )

        img = Image.open(
            image_path
        ).convert("RGBA")

        orig_width, orig_height = img.size

        # ----------------------------------------------------
        # Exactly the same resizing logic as Rust
        # ----------------------------------------------------

        target_width = (
            760
            if orig_width > 800
            else orig_width
        )

        target_height = int(
            round(
                target_width *
                (
                    orig_height /
                    orig_width
                )
            )
        )

        if (
            target_width != orig_width
            or
            target_height != orig_height
        ):
            img = img.resize(
                (
                    target_width,
                    target_height
                ),
                Image.Resampling.LANCZOS
            )
        else:
            img = img.convert("RGBA")

        self.width = target_width
        self.height = target_height

        # ----------------------------------------------------
        # RGBA -> premultiplied BGRA
        #
        # Exactly the conceptual operation used by Rust.
        # ----------------------------------------------------

        rgba = img.tobytes()

        self.base_pixels = bytearray(
            len(rgba)
        )

        for i in range(
            0,
            len(rgba),
            4
        ):
            r = rgba[i]
            g = rgba[i + 1]
            b = rgba[i + 2]
            a = rgba[i + 3]

            alpha = a / 255.0

            self.base_pixels[i] = int(
                b * alpha
            )

            self.base_pixels[i + 1] = int(
                g * alpha
            )

            self.base_pixels[i + 2] = int(
                r * alpha
            )

            self.base_pixels[i + 3] = a

        # ----------------------------------------------------
        # Runtime state
        # ----------------------------------------------------

        self.progress = 0

        self.smooth_progress = 0.0

        self.install_complete = False

        self.start_time = time.perf_counter()

        self.last_frame = self.start_time

        self.last_visibility_check = self.start_time

        # ----------------------------------------------------
        # Create splash window
        # ----------------------------------------------------

        self.hwnd = self.create_splash_window()

        if not self.hwnd:
            win32_error("CreateWindowExW failed")

        # Initial frame
        self.update_splash_with_progress(
            self.smooth_progress,
            0.0
        )

        user32.ShowWindow(
            self.hwnd,
            SW_SHOW
        )


    # ========================================================
    # Register/create window
    # ========================================================

    def create_splash_window(self):

        hinstance = kernel32.GetModuleHandleW(
            None
        )

        if not hinstance:
            win32_error("GetModuleHandleW failed")

        # ----------------------------------------------------
        # Register class
        # ----------------------------------------------------

        wc = WNDCLASSW()

        wc.style = (
            CS_HREDRAW |
            CS_VREDRAW
        )

        wc.lpfnWndProc = wnd_proc

        wc.hInstance = hinstance

        # MAKEINTRESOURCEW(IDC_ARROW)
        cursor_resource = ctypes.cast(
            ctypes.c_void_p(IDC_ARROW),
            wintypes.LPCWSTR
        )

        wc.hCursor = user32.LoadCursorW(
            None,
            cursor_resource
        )

        wc.lpszClassName = self.CLASS_NAME

        # RegisterClassW returns zero if the class is already
        # registered. That is fine for this simulator.
        user32.RegisterClassW(
            ctypes.byref(wc)
        )

        # ----------------------------------------------------
        # Center
        # ----------------------------------------------------

        screen_width = user32.GetSystemMetrics(
            SM_CXSCREEN
        )

        screen_height = user32.GetSystemMetrics(
            SM_CYSCREEN
        )

        x = (
            screen_width -
            self.width
        ) // 2

        y = (
            screen_height -
            self.height
        ) // 2

        # ----------------------------------------------------
        # Create WS_POPUP + layered window
        # ----------------------------------------------------

        hwnd = user32.CreateWindowExW(
            WS_EX_LAYERED |
            WS_EX_TOOLWINDOW,

            self.CLASS_NAME,

            "Zayita Installer",

            WS_POPUP,

            x,
            y,

            self.width,
            self.height,

            None,
            None,
            hinstance,
            None
        )

        return hwnd


    # ========================================================
    # UpdateLayeredWindow rendering
    # ========================================================

    def update_splash_with_progress(
        self,
        progress,
        anim_time
    ):

        if not user32.IsWindow(
            self.hwnd
        ):
            return False

        width = self.width
        height = self.height

        # ----------------------------------------------------
        # Copy base image
        # ----------------------------------------------------

        pixels = bytearray(
            self.base_pixels
        )

        # ----------------------------------------------------
        # Rust:
        #
        # const PROGRESS_BAR_HEIGHT: i32 = 6;
        # ----------------------------------------------------

        bar_height_f = 6.0
        radius = bar_height_f * 0.5

        # ----------------------------------------------------
        # Rust:
        #
        # bar_y_start = (height as f32 * 0.908).round() as i32;
        # ----------------------------------------------------

        bar_y_start = int(round(height * 0.908))
        bar_y_end = bar_y_start + 6

        # ----------------------------------------------------
        # Rust:
        #
        # let bar_width = (width as f32 * 0.65).round() as i32;
        # ----------------------------------------------------

        bar_width = int(round(width * 0.65))
        bar_width_f = float(bar_width)

        bar_x_start = (width - bar_width) // 2
        bar_x_end = bar_x_start + bar_width

        y_center = bar_y_start + radius

        # ----------------------------------------------------
        # Track capsule
        # ----------------------------------------------------

        track_x_left = bar_x_start + radius
        track_x_right = bar_x_end - radius

        # ----------------------------------------------------
        # Fill amount
        # ----------------------------------------------------

        filled_pct = max(0.0, min(1.0, progress / 100.0))
        filled_width_f = bar_width_f * filled_pct

        # ----------------------------------------------------
        # RTL:
        #
        # right edge stays fixed, left edge moves left
        # ----------------------------------------------------

        fill_x_right = track_x_right
        fill_x_left = min(bar_x_end - filled_width_f + radius, fill_x_right)

        # ----------------------------------------------------
        # Shimmer: smooth angled light beam gliding continuously
        # ----------------------------------------------------

        beam_width = 80.0
        travel_dist = bar_width_f + beam_width * 2.0
        speed = 260.0  # pixels per second
        beam_center_x = (bar_x_end + beam_width) - ((anim_time * speed) % travel_dist)

        # ----------------------------------------------------
        # Scan region
        # ----------------------------------------------------

        y_start_scan = max(bar_y_start - 1, 0)
        y_end_scan = min(bar_y_end + 1, height - 1)
        x_start_scan = max(bar_x_start - 1, 0)
        x_end_scan = min(bar_x_end + 1, width - 1)

        # ----------------------------------------------------
        # Pixel loop
        # ----------------------------------------------------

        for y in range(y_start_scan, y_end_scan + 1):
            py = y + 0.5
            for x in range(x_start_scan, x_end_scan + 1):
                px = x + 0.5

                # Track distance & coverage (Anti-aliased)
                clamped_track_x = max(track_x_left, min(px, track_x_right))
                dist_track = math.sqrt((px - clamped_track_x) ** 2 + (py - y_center) ** 2)
                if dist_track > (radius + 0.5):
                    continue

                track_cov = max(0.0, min(1.0, radius + 0.5 - dist_track))

                # Fill distance & coverage (Anti-aliased)
                if filled_width_f > 0.1:
                    clamped_fill_x = max(fill_x_left, min(px, fill_x_right))
                    dist_fill = math.sqrt((px - clamped_fill_x) ** 2 + (py - y_center) ** 2)
                    fill_cov = max(0.0, min(1.0, radius + 0.5 - dist_fill)) * track_cov
                else:
                    fill_cov = 0.0

                pixel_idx = (y * width + x) * 4
                if pixel_idx + 3 >= len(pixels):
                    continue

                orig_b = float(self.base_pixels[pixel_idx])
                orig_g = float(self.base_pixels[pixel_idx + 1])
                orig_r = float(self.base_pixels[pixel_idx + 2])

                cur_r = orig_r
                cur_g = orig_g
                cur_b = orig_b

                # Transparent frosted glass track (subtle translucent glass sheen over background)
                glass_alpha = 0.22 * track_cov
                if dist_track > (radius - 1.0) and dist_track <= (radius + 0.5):
                    edge_factor = max(0.0, min(1.0, 1.0 - abs(dist_track - (radius - 0.5))))
                    glass_alpha += 0.18 * edge_factor * track_cov

                cur_r = cur_r * (1.0 - glass_alpha) + 255.0 * glass_alpha
                cur_g = cur_g * (1.0 - glass_alpha) + 255.0 * glass_alpha
                cur_b = cur_b * (1.0 - glass_alpha) + 255.0 * glass_alpha

                # Filled gold section with smooth shimmer reflection
                if fill_cov > 0.0:
                    rel_y = (py - bar_y_start) / bar_height_f
                    gold_r = 228.0 - rel_y * 25.0
                    gold_g = 190.0 - rel_y * 25.0
                    gold_b = 68.0 - rel_y * 20.0

                    # Slanted shimmer light beam
                    px_slanted = px + (py - y_center) * 1.2
                    dist_beam = abs(px_slanted - beam_center_x)
                    if dist_beam < (beam_width * 0.5):
                        shimmer_factor = 0.5 * (1.0 + math.cos(dist_beam / (beam_width * 0.5) * math.pi))
                        shine_strength = 0.45 * shimmer_factor
                        gold_r = gold_r * (1.0 - shine_strength) + 255.0 * shine_strength
                        gold_g = gold_g * (1.0 - shine_strength) + 245.0 * shine_strength
                        gold_b = gold_b * (1.0 - shine_strength) + 195.0 * shine_strength

                    cur_r = cur_r * (1.0 - fill_cov) + gold_r * fill_cov
                    cur_g = cur_g * (1.0 - fill_cov) + gold_g * fill_cov
                    cur_b = cur_b * (1.0 - fill_cov) + gold_b * fill_cov

                # BGRA output
                pixels[pixel_idx] = int(min(255.0, max(0.0, cur_b)))
                pixels[pixel_idx + 1] = int(min(255.0, max(0.0, cur_g)))
                pixels[pixel_idx + 2] = int(min(255.0, max(0.0, cur_r)))
                pixels[pixel_idx + 3] = 255

        # ----------------------------------------------------
        # Create screen DC
        # ----------------------------------------------------

        screen_dc = user32.GetDC(
            None
        )

        if not screen_dc:
            return False

        # ----------------------------------------------------
        # Compatible memory DC
        # ----------------------------------------------------

        mem_dc = gdi32.CreateCompatibleDC(
            screen_dc
        )

        if not mem_dc:

            user32.ReleaseDC(
                None,
                screen_dc
            )

            return False

        # ----------------------------------------------------
        # BITMAPINFO
        #
        # Same:
        #
        # biHeight = -height
        # 32-bit
        # BI_RGB
        # ----------------------------------------------------

        bmi = BITMAPINFO()

        bmi.bmiHeader.biSize = ctypes.sizeof(
            BITMAPINFOHEADER
        )

        bmi.bmiHeader.biWidth = width

        bmi.bmiHeader.biHeight = -height

        bmi.bmiHeader.biPlanes = 1

        bmi.bmiHeader.biBitCount = 32

        bmi.bmiHeader.biCompression = BI_RGB

        # ----------------------------------------------------
        # DIB section
        # ----------------------------------------------------

        bits = ctypes.c_void_p()

        hbitmap = gdi32.CreateDIBSection(
            mem_dc,
            ctypes.byref(bmi),
            DIB_RGB_COLORS,
            ctypes.byref(bits),
            None,
            0
        )

        if not hbitmap:

            gdi32.DeleteDC(
                mem_dc
            )

            user32.ReleaseDC(
                None,
                screen_dc
            )

            return False

        # ----------------------------------------------------
        # Copy image bytes
        # ----------------------------------------------------

        if bits.value:

            ctypes.memmove(
                bits.value,
                bytes(pixels),
                len(pixels)
            )

        # ----------------------------------------------------
        # Select bitmap
        # ----------------------------------------------------

        old_bitmap = gdi32.SelectObject(
            mem_dc,
            hbitmap
        )

        # ----------------------------------------------------
        # UpdateLayeredWindow parameters
        # ----------------------------------------------------

        size = SIZE(
            width,
            height
        )

        pt_src = POINT(
            0,
            0
        )

        blend = BLENDFUNCTION(
            AC_SRC_OVER,
            0,
            255,
            AC_SRC_ALPHA
        )

        result = user32.UpdateLayeredWindow(
            self.hwnd,
            screen_dc,
            None,
            ctypes.byref(size),
            mem_dc,
            ctypes.byref(pt_src),
            0,
            ctypes.byref(blend),
            ULW_ALPHA
        )

        # ----------------------------------------------------
        # Cleanup
        # ----------------------------------------------------

        gdi32.SelectObject(
            mem_dc,
            old_bitmap
        )

        gdi32.DeleteObject(
            hbitmap
        )

        gdi32.DeleteDC(
            mem_dc
        )

        user32.ReleaseDC(
            None,
            screen_dc
        )

        return bool(result)


    # ========================================================
    # Simulated installer
    # ========================================================

    def install_with_progress(self):

        # ----------------------------------------------------
        # Step 1
        # Same conceptual sequence as uninstall_old_msi()
        # ----------------------------------------------------

        self.set_progress(
            0
        )

        time.sleep(
            1.0
        )

        self.set_progress(
            10
        )

        # Pretend to uninstall old MSI
        time.sleep(
            1.8
        )

        self.set_progress(
            30
        )

        # ----------------------------------------------------
        # Step 2
        # Extract embedded NSIS
        # ----------------------------------------------------

        time.sleep(
            0.8
        )

        self.set_progress(
            40
        )

        # Intentional apparent "freeze"
        time.sleep(
            2.8
        )

        # ----------------------------------------------------
        # Step 3
        # Run NSIS
        # ----------------------------------------------------

        self.set_progress(
            60
        )

        # Intentional apparent "freeze"
        time.sleep(
            3.2
        )

        self.set_progress(
            75
        )

        time.sleep(
            1.2
        )

        self.set_progress(
            85
        )

        # Another pause
        time.sleep(
            2.4
        )

        self.set_progress(
            95
        )

        time.sleep(
            1.0
        )

        self.set_progress(
            100
        )

        self.install_complete = True


    # ========================================================
    # Atomic-like progress update
    # ========================================================

    def set_progress(self, value):

        self.progress = max(
            0,
            min(
                100,
                int(value)
            )
        )


    # ========================================================
    # Main message/animation loop
    # ========================================================

    def run(self):

        # ----------------------------------------------------
        # Background installation
        # ----------------------------------------------------

        install_thread = threading.Thread(
            target=self.install_with_progress,
            daemon=True
        )

        install_thread.start()

        frame_duration = (
            1.0 / 30.0
        )

        # ----------------------------------------------------
        # Main loop
        # ----------------------------------------------------

        while True:

            # ================================================
            # Process messages without blocking
            # ================================================

            msg = MSG()

            while user32.PeekMessageW(
                ctypes.byref(msg),
                None,
                0,
                0,
                PM_REMOVE
            ):

                if msg.message == WM_QUIT:

                    # Ignore WM_QUIT during installation,
                    # same policy as the Rust code.
                    continue

                user32.TranslateMessage(
                    ctypes.byref(msg)
                )

                user32.DispatchMessageW(
                    ctypes.byref(msg)
                )

            now = time.perf_counter()

            # ================================================
            # Window check every 500 ms
            # ================================================

            if (
                now -
                self.last_visibility_check
                >= 0.5
            ):

                self.last_visibility_check = now

                if not user32.IsWindow(
                    self.hwnd
                ):

                    self.hwnd = (
                        self.create_splash_window()
                    )

                    self.update_splash_with_progress(
                        self.smooth_progress,
                        now -
                        self.start_time
                    )

                else:

                    user32.ShowWindow(
                        self.hwnd,
                        SW_SHOW
                    )

            # ================================================
            # Complete
            # ================================================

            if self.install_complete:

                # Same 5% animation as Rust
                while (
                    self.smooth_progress
                    < 100.0
                ):

                    self.smooth_progress = min(
                        self.smooth_progress +
                        5.0,
                        100.0
                    )

                    self.update_splash_with_progress(
                        self.smooth_progress,
                        time.perf_counter()
                        -
                        self.start_time
                    )

                    time.sleep(
                        0.020
                    )

                install_thread.join()

                # In real Rust:
                #
                # launch_application()
                #
                # Simulator intentionally does nothing.

                time.sleep(
                    0.6
                )

                break

            # ================================================
            # 30 FPS rendering
            # ================================================

            if (
                now -
                self.last_frame
                >= frame_duration
            ):

                target_progress = float(
                    self.progress
                )

                elapsed_secs = (
                    now -
                    self.start_time
                )

                # ============================================
                # Rust:
                #
                # let time_based_min =
                #   (elapsed_secs * 2.0).min(85.0);
                # ============================================

                time_based_min = min(
                    elapsed_secs * 2.0,
                    85.0
                )

                effective_target = max(
                    target_progress,
                    time_based_min
                )

                # ============================================
                # Rust smooth interpolation
                # ============================================

                if (
                    self.smooth_progress
                    <
                    effective_target
                ):

                    self.smooth_progress += (
                        (
                            effective_target -
                            self.smooth_progress
                        )
                        * 0.1
                    )

                    self.smooth_progress = min(
                        self.smooth_progress,
                        effective_target
                    )

                # ============================================
                # Render
                # ============================================

                self.update_splash_with_progress(
                    self.smooth_progress,
                    elapsed_secs
                )

                self.last_frame = now

            # ================================================
            # Rust:
            #
            # thread::sleep(Duration::from_millis(10));
            # ================================================

            time.sleep(
                0.010
            )

        # ----------------------------------------------------
        # Close
        # ----------------------------------------------------

        if user32.IsWindow(
            self.hwnd
        ):

            user32.DestroyWindow(
                self.hwnd
            )


# ============================================================
# Main
# ============================================================

def main():

    if sys.platform != "win32":
        raise RuntimeError(
            "הסימולטור מיועד ל-Windows בלבד."
        )

    try:

        app = ZayitaSplash()

        app.run()

    except Exception as e:

        print(
            f"\nשגיאה: {e}\n"
        )

        raise


if __name__ == "__main__":
    main()