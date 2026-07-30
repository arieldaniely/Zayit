#!/usr/bin/env python3
"""Make the signed MttVDD monitor the primary Windows display at a fixed resolution."""

from __future__ import annotations

import argparse
import ctypes
import os
import time
from ctypes import wintypes
from dataclasses import dataclass


CCHDEVICENAME = 32
CCHFORMNAME = 32
DISPLAY_DEVICE_ATTACHED_TO_DESKTOP = 0x00000001
DISPLAY_DEVICE_PRIMARY_DEVICE = 0x00000004
ENUM_CURRENT_SETTINGS = -1
DM_POSITION = 0x00000020
DM_PELSWIDTH = 0x00080000
DM_PELSHEIGHT = 0x00100000
DM_DISPLAYFREQUENCY = 0x00400000
CDS_UPDATEREGISTRY = 0x00000001
CDS_SET_PRIMARY = 0x00000010
CDS_NORESET = 0x10000000
DISP_CHANGE_SUCCESSFUL = 0


class PointL(ctypes.Structure):
    _fields_ = (("x", wintypes.LONG), ("y", wintypes.LONG))


class DevModeW(ctypes.Structure):
    _fields_ = (
        ("dmDeviceName", wintypes.WCHAR * CCHDEVICENAME),
        ("dmSpecVersion", wintypes.WORD),
        ("dmDriverVersion", wintypes.WORD),
        ("dmSize", wintypes.WORD),
        ("dmDriverExtra", wintypes.WORD),
        ("dmFields", wintypes.DWORD),
        ("dmPosition", PointL),
        ("dmDisplayOrientation", wintypes.DWORD),
        ("dmDisplayFixedOutput", wintypes.DWORD),
        ("dmColor", wintypes.SHORT),
        ("dmDuplex", wintypes.SHORT),
        ("dmYResolution", wintypes.SHORT),
        ("dmTTOption", wintypes.SHORT),
        ("dmCollate", wintypes.SHORT),
        ("dmFormName", wintypes.WCHAR * CCHFORMNAME),
        ("dmLogPixels", wintypes.WORD),
        ("dmBitsPerPel", wintypes.DWORD),
        ("dmPelsWidth", wintypes.DWORD),
        ("dmPelsHeight", wintypes.DWORD),
        ("dmDisplayFlags", wintypes.DWORD),
        ("dmDisplayFrequency", wintypes.DWORD),
        ("dmICMMethod", wintypes.DWORD),
        ("dmICMIntent", wintypes.DWORD),
        ("dmMediaType", wintypes.DWORD),
        ("dmDitherType", wintypes.DWORD),
        ("dmReserved1", wintypes.DWORD),
        ("dmReserved2", wintypes.DWORD),
        ("dmPanningWidth", wintypes.DWORD),
        ("dmPanningHeight", wintypes.DWORD),
    )


class DisplayDeviceW(ctypes.Structure):
    _fields_ = (
        ("cb", wintypes.DWORD),
        ("DeviceName", wintypes.WCHAR * 32),
        ("DeviceString", wintypes.WCHAR * 128),
        ("StateFlags", wintypes.DWORD),
        ("DeviceID", wintypes.WCHAR * 128),
        ("DeviceKey", wintypes.WCHAR * 128),
    )


@dataclass(frozen=True)
class Display:
    name: str
    description: str
    device_id: str
    state_flags: int

    @property
    def attached(self) -> bool:
        return bool(self.state_flags & DISPLAY_DEVICE_ATTACHED_TO_DESKTOP)

    @property
    def primary(self) -> bool:
        return bool(self.state_flags & DISPLAY_DEVICE_PRIMARY_DEVICE)


def windows_api():
    if os.name != "nt":
        raise RuntimeError("Virtual display configuration requires Windows")
    user32 = ctypes.WinDLL("user32", use_last_error=True)
    user32.EnumDisplayDevicesW.argtypes = (
        wintypes.LPCWSTR,
        wintypes.DWORD,
        ctypes.POINTER(DisplayDeviceW),
        wintypes.DWORD,
    )
    user32.EnumDisplayDevicesW.restype = wintypes.BOOL
    user32.EnumDisplaySettingsExW.argtypes = (
        wintypes.LPCWSTR,
        wintypes.DWORD,
        ctypes.POINTER(DevModeW),
        wintypes.DWORD,
    )
    user32.EnumDisplaySettingsExW.restype = wintypes.BOOL
    user32.ChangeDisplaySettingsExW.argtypes = (
        wintypes.LPCWSTR,
        ctypes.POINTER(DevModeW),
        wintypes.HWND,
        wintypes.DWORD,
        wintypes.LPVOID,
    )
    user32.ChangeDisplaySettingsExW.restype = wintypes.LONG
    return user32


def displays(user32) -> list[Display]:
    result: list[Display] = []
    index = 0
    while True:
        device = DisplayDeviceW()
        device.cb = ctypes.sizeof(device)
        if not user32.EnumDisplayDevicesW(None, index, ctypes.byref(device), 0):
            break
        result.append(
            Display(device.DeviceName, device.DeviceString, device.DeviceID, device.StateFlags),
        )
        index += 1
    return result


def display_mode(user32, display_name: str, mode_index: int) -> DevModeW | None:
    mode = DevModeW()
    mode.dmSize = ctypes.sizeof(mode)
    if not user32.EnumDisplaySettingsExW(display_name, mode_index, ctypes.byref(mode), 0):
        return None
    return mode


def find_mode(user32, display_name: str, width: int, height: int) -> DevModeW:
    matches: list[DevModeW] = []
    index = 0
    while True:
        mode = display_mode(user32, display_name, index)
        if mode is None:
            break
        if mode.dmPelsWidth == width and mode.dmPelsHeight == height:
            matches.append(mode)
        index += 1
    if not matches:
        raise RuntimeError(f"{display_name} does not advertise {width}x{height}")
    return min(matches, key=lambda item: abs(int(item.dmDisplayFrequency) - 60))


def change_display(user32, name: str, mode: DevModeW, flags: int) -> None:
    result = user32.ChangeDisplaySettingsExW(name, ctypes.byref(mode), None, flags, None)
    if result != DISP_CHANGE_SUCCESSFUL:
        raise RuntimeError(f"ChangeDisplaySettingsExW({name}) failed with code {result}")


def choose_virtual_display(items: list[Display]) -> Display:
    for item in items:
        identity = f"{item.description} {item.device_id}".lower()
        if "virtual display driver" in identity or "mttvdd" in identity:
            return item
    details = ", ".join(f"{item.name}={item.description}" for item in items)
    raise RuntimeError(f"MttVDD virtual display was not found; displays: {details}")


def wait_for_virtual_display(user32, width: int, height: int, timeout: float = 30.0) -> tuple[list[Display], Display]:
    deadline = time.monotonic() + timeout
    last_error: RuntimeError | None = None
    while time.monotonic() < deadline:
        items = displays(user32)
        try:
            target = choose_virtual_display(items)
            find_mode(user32, target.name, width, height)
            return items, target
        except RuntimeError as error:
            last_error = error
            time.sleep(1)
    raise RuntimeError(f"Virtual display did not become ready: {last_error}")


def configure(width: int, height: int) -> None:
    user32 = windows_api()
    try:
        user32.SetProcessDpiAwarenessContext(ctypes.c_void_p(-4))
    except AttributeError:
        pass

    initial, target = wait_for_virtual_display(user32, width, height)
    target_mode = find_mode(user32, target.name, width, height)

    next_x = width
    for item in initial:
        if item.name == target.name or not item.attached:
            continue
        current = display_mode(user32, item.name, ENUM_CURRENT_SETTINGS)
        if current is None:
            continue
        current.dmPosition = PointL(next_x, 0)
        current.dmFields = DM_POSITION
        change_display(user32, item.name, current, CDS_UPDATEREGISTRY | CDS_NORESET)
        next_x += int(current.dmPelsWidth)

    target_mode.dmPosition = PointL(0, 0)
    target_mode.dmFields |= DM_POSITION | DM_PELSWIDTH | DM_PELSHEIGHT | DM_DISPLAYFREQUENCY
    change_display(
        user32,
        target.name,
        target_mode,
        CDS_UPDATEREGISTRY | CDS_SET_PRIMARY | CDS_NORESET,
    )
    result = user32.ChangeDisplaySettingsExW(None, None, None, 0, None)
    if result != DISP_CHANGE_SUCCESSFUL:
        raise RuntimeError(f"Applying the display topology failed with code {result}")

    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        current_items = displays(user32)
        current_target = next((item for item in current_items if item.name == target.name), None)
        current_mode = display_mode(user32, target.name, ENUM_CURRENT_SETTINGS)
        if (
            current_target is not None
            and current_target.primary
            and current_mode is not None
            and current_mode.dmPelsWidth == width
            and current_mode.dmPelsHeight == height
        ):
            actual_width = user32.GetSystemMetrics(0)
            actual_height = user32.GetSystemMetrics(1)
            if actual_width != width or actual_height != height:
                raise RuntimeError(
                    f"Primary metrics are {actual_width}x{actual_height}; expected {width}x{height}",
                )
            print(f"Primary virtual display: {target.name} {width}x{height}")
            return
        time.sleep(1)
    raise RuntimeError(f"{target.name} did not become the {width}x{height} primary display")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--width", type=int, default=3840)
    parser.add_argument("--height", type=int, default=2160)
    parser.add_argument("--list", action="store_true", help="List adapters without changing them")
    args = parser.parse_args()
    user32 = windows_api()
    if args.list:
        for item in displays(user32):
            print(
                f"{item.name}: {item.description}; id={item.device_id}; "
                f"attached={item.attached}; primary={item.primary}",
            )
        return 0
    configure(args.width, args.height)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
