#![windows_subsystem = "windows"]

use image::GenericImageView;
use std::ffi::c_void;
use std::io::Write;
use std::process::Command;
use winreg::enums::*;
use winreg::RegKey;
use std::ptr;
use std::sync::atomic::{AtomicBool, AtomicU32, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::{Duration, Instant};
use windows::core::w;
use windows::Win32::Foundation::{HINSTANCE, HWND, LPARAM, LRESULT, POINT, SIZE, WPARAM};
use windows::Win32::Graphics::Gdi::{
    CreateCompatibleDC, CreateDIBSection, DeleteDC, DeleteObject, GetDC, ReleaseDC, SelectObject,
    BITMAPINFO, BITMAPINFOHEADER, BI_RGB, DIB_RGB_COLORS,
};
use windows::Win32::System::LibraryLoader::GetModuleHandleW;
use windows::Win32::UI::HiDpi::{
    GetDpiForSystem, SetProcessDpiAwarenessContext, DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2,
};
use windows::Win32::UI::WindowsAndMessaging::{
    CreateWindowExW, DefWindowProcW, DispatchMessageW, GetSystemMetrics,
    LoadCursorW, PeekMessageW, PostQuitMessage, RegisterClassW, ShowWindow, TranslateMessage, UpdateLayeredWindow,
    CS_HREDRAW, CS_VREDRAW, IDC_ARROW, MSG, PM_REMOVE, SM_CXSCREEN, SM_CYSCREEN, SW_SHOW, ULW_ALPHA,
    WM_CLOSE, WM_DESTROY, WNDCLASSW, WS_EX_LAYERED, WS_EX_TOOLWINDOW, WS_POPUP,
};

// Embed splash image at compile time
const SPLASH_PNG: &[u8] = include_bytes!("../resources/splash.png");
// Embed NSIS installer at compile time
const NSIS_DATA: &[u8] = include_bytes!("../resources/zayita-nsis.exe");

// Progress bar configuration
const PROGRESS_BAR_HEIGHT: i32 = 4;
const PROGRESS_BAR_COLOR: (u8, u8, u8) = (212, 175, 55); // Gold color matching the brand

fn main() {
    // Set DPI awareness before any window creation (like JetBrains Runtime does)
    unsafe {
        let _ = SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
    }

    // Decode splash image
    let img = image::load_from_memory(SPLASH_PNG).expect("Failed to decode splash image");
    let (orig_width, orig_height) = img.dimensions();

    // Match the 720 x 420 dp setup window using the visible card, not PNG padding.
    let rgba = img.to_rgba8();
    let mut left = orig_width;
    let mut top = orig_height;
    let mut right = 0;
    let mut bottom = 0;
    for (x, y, pixel) in rgba.enumerate_pixels() {
        // Ignore the faint shadow surrounding the card.
        if pixel[3] >= 128 {
            left = left.min(x);
            top = top.min(y);
            right = right.max(x + 1);
            bottom = bottom.max(y + 1);
        }
    }
    let visible_width = right.saturating_sub(left).max(1);
    let visible_height = bottom.saturating_sub(top).max(1);
    let dpi_scale = unsafe { GetDpiForSystem() } as f64 / 96.0;
    let scale_x = 720.0 / visible_width as f64 * dpi_scale;
    let scale_y = 420.0 / visible_height as f64 * dpi_scale;
    let target_width = (orig_width as f64 * scale_x).round().max(1.0) as u32;
    let target_height = (orig_height as f64 * scale_y).round().max(1.0) as u32;

    let resized_img = if target_width != orig_width || target_height != orig_height {
        image::imageops::resize(&img, target_width, target_height, image::imageops::FilterType::Lanczos3)
    } else {
        img.to_rgba8()
    };

    let width = target_width;
    let height = target_height;

    // Convert to BGRA format (premultiplied alpha for layered window)
    // Using ARGB format: 0x00ff0000 (R), 0x0000ff00 (G), 0x000000ff (B), 0xff000000 (A)
    // with premultiplied alpha (like JBR splash screen)
    let mut base_bgra_pixels = Vec::with_capacity((width * height * 4) as usize);
    for pixel in resized_img.pixels() {
        let a = pixel[3] as f32 / 255.0;
        base_bgra_pixels.push((pixel[2] as f32 * a) as u8); // B premultiplied
        base_bgra_pixels.push((pixel[1] as f32 * a) as u8); // G premultiplied
        base_bgra_pixels.push((pixel[0] as f32 * a) as u8); // R premultiplied
        base_bgra_pixels.push(pixel[3]); // A
    }

    // Progress tracking (0-100)
    let progress = Arc::new(AtomicU32::new(0));
    let progress_thread = Arc::clone(&progress);

    // Flag to signal installation complete
    let install_complete = Arc::new(AtomicBool::new(false));
    let install_complete_thread = Arc::clone(&install_complete);

    // Start installation in background thread
    let mut install_thread = Some(thread::spawn(move || {
        install_with_progress(progress_thread);
        install_complete_thread.store(true, Ordering::SeqCst);
    }));

    // Create and show splash window
    let mut hwnd = create_splash_window(width as i32, height as i32, &base_bgra_pixels);

    // Non-blocking message loop with progress bar animation
    let mut smooth_progress: f32 = 0.0;
    let frame_duration = Duration::from_millis(33); // ~30 FPS
    let mut last_frame = Instant::now();
    let start_time = Instant::now();
    let mut last_visibility_check = Instant::now();

    unsafe {
        use windows::Win32::UI::WindowsAndMessaging::IsWindow;

        let mut msg = MSG::default();
        loop {
            // Process all pending messages without blocking
            while PeekMessageW(&mut msg, HWND::default(), 0, 0, PM_REMOVE).as_bool() {
                if msg.message == 0x0012 { // WM_QUIT
                    // Ignore WM_QUIT during installation - we control when to exit
                    continue;
                }
                let _ = TranslateMessage(&msg);
                DispatchMessageW(&msg);
            }

            // Periodically check window validity and recreate if needed
            let now = Instant::now();
            if now.duration_since(last_visibility_check) >= Duration::from_millis(500) {
                last_visibility_check = now;
                if !IsWindow(hwnd).as_bool() {
                    // Window was destroyed - recreate it
                    hwnd = create_splash_window(width as i32, height as i32, &base_bgra_pixels);
                    // Force redraw with current progress
                    let _ = update_splash_with_progress(
                        hwnd,
                        width as i32,
                        height as i32,
                        &base_bgra_pixels,
                        smooth_progress,
                        start_time.elapsed().as_secs_f32(),
                    );
                } else {
                    // Ensure window is visible
                    let _ = ShowWindow(hwnd, SW_SHOW);
                }
            }

            // Check if installation is complete
            if install_complete.load(Ordering::SeqCst) {
                // Animate to 100% smoothly
                while smooth_progress < 100.0 {
                    smooth_progress = (smooth_progress + 5.0).min(100.0);
                    // Check window validity and recreate if needed
                    if !IsWindow(hwnd).as_bool() {
                        hwnd = create_splash_window(width as i32, height as i32, &base_bgra_pixels);
                    }
                    let _ = update_splash_with_progress(
                        hwnd,
                        width as i32,
                        height as i32,
                        &base_bgra_pixels,
                        smooth_progress,
                        start_time.elapsed().as_secs_f32(),
                    );
                    thread::sleep(Duration::from_millis(20));
                }

                // Wait for installation thread to finish
                if let Some(handle) = install_thread.take() {
                    let _ = handle.join();
                }

                // Launch the application after NSIS silent install
                launch_application();

                PostQuitMessage(0);
                break;
            }

            // Update progress bar at ~30 FPS with smooth animation
            if now.duration_since(last_frame) >= frame_duration {
                let target_progress = progress.load(Ordering::SeqCst) as f32;

                // Smooth interpolation toward target
                // Also add time-based minimum progress so it never looks stuck
                let elapsed_secs = start_time.elapsed().as_secs_f32();
                let time_based_min = (elapsed_secs * 2.0).min(85.0); // Slow increase up to 85%

                let effective_target = target_progress.max(time_based_min);

                // Smooth easing toward target
                if smooth_progress < effective_target {
                    smooth_progress += ((effective_target - smooth_progress) * 0.1).max(0.5);
                    smooth_progress = smooth_progress.min(effective_target);
                }

                let _ = update_splash_with_progress(
                    hwnd,
                    width as i32,
                    height as i32,
                    &base_bgra_pixels,
                    smooth_progress,
                    elapsed_secs,
                );
                last_frame = now;
            }

            // Small sleep to prevent CPU spinning
            thread::sleep(Duration::from_millis(10));
        }
    }
}

fn install_with_progress(progress: Arc<AtomicU32>) {
    // Step 1: Check for and uninstall old MSI installation
    uninstall_old_msi(&progress);

    // Step 2: Extract NSIS installer to temp directory
    let temp_dir = std::env::temp_dir();
    let nsis_path = temp_dir.join("zayita-installer-temp.exe");

    {
        let mut file = std::fs::File::create(&nsis_path).expect("Failed to create temp NSIS file");
        file.write_all(NSIS_DATA).expect("Failed to write NSIS data");
    }

    progress.store(40, Ordering::SeqCst);

    // Step 3: Run NSIS installer silently
    let status = Command::new(&nsis_path)
        .arg("/S")
        .status();

    if let Err(e) = status {
        eprintln!("Failed to run NSIS installer: {}", e);
    }

    // Step 4: Register custom URL protocols as trusted for Office applications (no admin privileges needed)
    register_office_trusted_protocols();

    progress.store(100, Ordering::SeqCst);

    // Clean up temp file
    let _ = std::fs::remove_file(&nsis_path);
}

/// Registers Zayita's custom URL schemes (`zayita:` and `zayit:`) as trusted protocols
/// in Microsoft Office (Word, Excel, PowerPoint, Outlook, etc.) under `HKEY_CURRENT_USER`.
/// This suppresses the "potential security concern" hyperlink warning without requiring
/// administrator privileges.
fn register_office_trusted_protocols() {
    let hkcu = RegKey::predef(HKEY_CURRENT_USER);

    // Common Office version keys:
    // 16.0 = Office 2016, 2019, 2021, 2024, Microsoft 365
    // 15.0 = Office 2013
    // 14.0 = Office 2010
    // 12.0 = Office 2007
    let mut versions = vec![
        "16.0".to_string(),
        "15.0".to_string(),
        "14.0".to_string(),
        "12.0".to_string(),
    ];

    // Also dynamically discover any additional version numbers under HKCU\SOFTWARE\Microsoft\Office
    for search_root in &[r"SOFTWARE\Microsoft\Office", r"SOFTWARE\Policies\Microsoft\Office"] {
        if let Ok(office_key) = hkcu.open_subkey(search_root) {
            for subkey_name in office_key.enum_keys().filter_map(|k| k.ok()) {
                if subkey_name.chars().next().map_or(false, |c| c.is_ascii_digit())
                    && subkey_name.contains('.')
                    && !versions.contains(&subkey_name)
                {
                    versions.push(subkey_name);
                }
            }
        }
    }

    // Register both canonical 'zayita' and legacy 'zayit', with colon (standard Office requirement)
    // and without colon as a fallback.
    let protocols = ["zayita:", "zayita", "zayit:", "zayit"];

    let base_paths = [
        r"SOFTWARE\Policies\Microsoft\Office",
        r"SOFTWARE\Microsoft\Office",
    ];

    for base in &base_paths {
        for ver in &versions {
            let trusted_path = format!(
                r"{}\{}\Common\Security\Trusted Protocols\All Applications",
                base, ver
            );
            for proto in &protocols {
                let full_path = format!(r"{}\{}", trusted_path, proto);
                let _ = hkcu.create_subkey(&full_path);
            }
        }
    }
}

/// Detects and silently uninstalls any old MSI-based Zayit/Zayita installation.
fn uninstall_old_msi(progress: &Arc<AtomicU32>) {
    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    let hklm = RegKey::predef(HKEY_LOCAL_MACHINE);

    let search_paths: &[(&RegKey, &str)] = &[
        (&hkcu, r"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall"),
        (&hklm, r"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall"),
        (&hklm, r"SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall"),
    ];

    for (root, path) in search_paths {
        if let Ok(uninstall_key) = root.open_subkey(path) {
            for key_name in uninstall_key.enum_keys().filter_map(|k| k.ok()) {
                if let Ok(subkey) = uninstall_key.open_subkey(&key_name) {
                    let display_name: Result<String, _> = subkey.get_value("DisplayName");
                    if let Ok(name) = display_name {
                        let is_target = name == "Zayit" || name == "Zayita" || name == "זית" || name == "זיתא";
                        if !is_target {
                            continue;
                        }
                        // Verify this is an MSI installation (not NSIS) by checking UninstallString
                        let is_msi = subkey
                            .get_value::<String, _>("UninstallString")
                            .map(|s| s.to_lowercase().contains("msiexec"))
                            .unwrap_or(false);

                        if !is_msi {
                            continue;
                        }

                        progress.store(10, Ordering::SeqCst);
                        // Silently uninstall the old MSI
                        let _ = Command::new("msiexec")
                            .args(["/x", &key_name, "/qn", "/norestart"])
                            .status();
                        progress.store(30, Ordering::SeqCst);
                        return;
                    }
                }
            }
        }
    }
}

fn update_splash_with_progress(
    hwnd: HWND,
    width: i32,
    height: i32,
    base_pixels: &[u8],
    progress: f32,
    _anim_time: f32,
) -> bool {
    use windows::Win32::UI::WindowsAndMessaging::IsWindow;

    // Check if window is still valid before attempting to update
    unsafe {
        if !IsWindow(hwnd).as_bool() {
            return false;
        }
    }

    // Create a copy of the base pixels and overlay the progress bar
    let mut pixels = base_pixels.to_vec();

    // Restore the original thin, square-ended RTL bar. The unfinished track is clear.
    let bar_y_start = (height as f32 * 0.900).round() as i32;
    let bar_height = (PROGRESS_BAR_HEIGHT as f32 * unsafe { GetDpiForSystem() } as f32 / 96.0)
        .round().max(1.0) as i32;
    let bar_width = (width as f32 * 0.65).round() as i32;
    let bar_x_start = (width - bar_width) / 2;
    let bar_x_end = bar_x_start + bar_width;
    let filled_width = (bar_width as f32 * (progress / 100.0).clamp(0.0, 1.0)).round() as i32;
    let fill_start = bar_x_end - filled_width;
    let (r, g, b) = PROGRESS_BAR_COLOR;
    for y in bar_y_start.max(0)..(bar_y_start + bar_height).min(height) {
        for x in bar_x_start.max(0)..bar_x_end.min(width) {
            let index = ((y * width + x) * 4) as usize;
            if x >= fill_start {
                pixels[index..index + 4].copy_from_slice(&[b, g, r, 255]);
            } else {
                pixels[index..index + 4].fill(0);
            }
        }
    }

    // Update the layered window with new pixels
    unsafe {
        let screen_dc = GetDC(None);
        let mem_dc = CreateCompatibleDC(screen_dc);

        let bmi = BITMAPINFO {
            bmiHeader: BITMAPINFOHEADER {
                biSize: std::mem::size_of::<BITMAPINFOHEADER>() as u32,
                biWidth: width,
                biHeight: -height, // Negative height = top-down DIB
                biPlanes: 1,
                biBitCount: 32,
                biCompression: BI_RGB.0,
                ..Default::default()
            },
            ..Default::default()
        };

        let mut bits: *mut c_void = ptr::null_mut();
        let hbitmap = match CreateDIBSection(mem_dc, &bmi, DIB_RGB_COLORS, &mut bits, None, 0) {
            Ok(bmp) => bmp,
            Err(_) => {
                let _ = DeleteDC(mem_dc);
                let _ = ReleaseDC(None, screen_dc);
                return false;
            }
        };

        if !bits.is_null() {
            ptr::copy_nonoverlapping(pixels.as_ptr(), bits as *mut u8, pixels.len());
        }

        let old_bitmap = SelectObject(mem_dc, hbitmap);

        let size = SIZE { cx: width, cy: height };
        let pt_src = POINT { x: 0, y: 0 };
        let blend = windows::Win32::Graphics::Gdi::BLENDFUNCTION {
            BlendOp: 0,
            BlendFlags: 0,
            SourceConstantAlpha: 255,
            AlphaFormat: 1,
        };

        let result = UpdateLayeredWindow(
            hwnd,
            screen_dc,
            None,
            Some(&size),
            mem_dc,
            Some(&pt_src),
            None,
            Some(&blend),
            ULW_ALPHA,
        );

        SelectObject(mem_dc, old_bitmap);
        let _ = DeleteObject(hbitmap);
        let _ = DeleteDC(mem_dc);
        let _ = ReleaseDC(None, screen_dc);

        result.is_ok()
    }
}

fn get_install_path() -> std::path::PathBuf {
    let local_app_data = std::env::var("LOCALAPPDATA")
        .unwrap_or_else(|_| {
            let user = std::env::var("USERNAME").unwrap_or_else(|_| "User".to_string());
            format!(r"C:\Users\{}\AppData\Local", user)
        });
    let zayita_path = std::path::PathBuf::from(&local_app_data).join("Programs").join("zayita").join("zayita.exe");
    if zayita_path.exists() {
        return zayita_path;
    }
    let zayit_path = std::path::PathBuf::from(&local_app_data).join("Programs").join("zayit").join("zayit.exe");
    if zayit_path.exists() {
        return zayit_path;
    }
    zayita_path
}

fn launch_application() {
    let exe_path = get_install_path();

    // Wait a bit for NSIS to fully complete file operations
    thread::sleep(Duration::from_millis(500));

    for attempt in 0..5 {
        if exe_path.exists() {
            let _ = Command::new(&exe_path).spawn();
            return;
        }
        if attempt < 4 {
            thread::sleep(Duration::from_millis(500));
        }
    }
}

fn create_splash_window(img_width: i32, img_height: i32, pixels: &[u8]) -> HWND {
    unsafe {
        let h_module = GetModuleHandleW(None).unwrap();
        let instance: HINSTANCE = std::mem::transmute(h_module);

        let wnd_class = WNDCLASSW {
            style: CS_HREDRAW | CS_VREDRAW,
            lpfnWndProc: Some(wnd_proc),
            hInstance: instance,
            lpszClassName: w!("ZayitaSplash"),
            hCursor: LoadCursorW(None, IDC_ARROW).unwrap(),
            ..Default::default()
        };

        RegisterClassW(&wnd_class);

        // Center window on screen
        let screen_width = GetSystemMetrics(SM_CXSCREEN);
        let screen_height = GetSystemMetrics(SM_CYSCREEN);
        let x = (screen_width - img_width) / 2;
        let y = (screen_height - img_height) / 2;

        // Create layered window (no border, transparent background)
        let hwnd = CreateWindowExW(
            WS_EX_LAYERED | WS_EX_TOOLWINDOW,
            w!("ZayitaSplash"),
            w!("Zayita Installer"),
            WS_POPUP,
            x,
            y,
            img_width,
            img_height,
            None,
            None,
            Some(&instance),
            None,
        )
        .unwrap();

        // Create bitmap and update layered window
        let screen_dc = GetDC(None);
        let mem_dc = CreateCompatibleDC(screen_dc);

        let bmi = BITMAPINFO {
            bmiHeader: BITMAPINFOHEADER {
                biSize: std::mem::size_of::<BITMAPINFOHEADER>() as u32,
                biWidth: img_width,
                biHeight: -img_height, // Negative height = top-down DIB (like JBR: bmi.biHeight = -splash->height)
                biPlanes: 1,
                biBitCount: 32,
                biCompression: BI_RGB.0,
                ..Default::default()
            },
            ..Default::default()
        };

        let mut bits: *mut c_void = ptr::null_mut();
        let hbitmap = CreateDIBSection(mem_dc, &bmi, DIB_RGB_COLORS, &mut bits, None, 0).unwrap();

        // Copy pixel data
        if !bits.is_null() {
            ptr::copy_nonoverlapping(pixels.as_ptr(), bits as *mut u8, pixels.len());
        }

        let old_bitmap = SelectObject(mem_dc, hbitmap);

        // Update layered window with the bitmap
        let size = SIZE {
            cx: img_width,
            cy: img_height,
        };
        let pt_src = POINT { x: 0, y: 0 };
        let blend = windows::Win32::Graphics::Gdi::BLENDFUNCTION {
            BlendOp: 0,        // AC_SRC_OVER
            BlendFlags: 0,
            SourceConstantAlpha: 255,
            AlphaFormat: 1,    // AC_SRC_ALPHA
        };

        let _ = UpdateLayeredWindow(
            hwnd,
            screen_dc,
            None,
            Some(&size),
            mem_dc,
            Some(&pt_src),
            None,
            Some(&blend),
            ULW_ALPHA,
        );

        // Cleanup
        SelectObject(mem_dc, old_bitmap);
        let _ = DeleteObject(hbitmap);
        let _ = DeleteDC(mem_dc);
        let _ = ReleaseDC(None, screen_dc);

        let _ = ShowWindow(hwnd, SW_SHOW);

        hwnd
    }
}

unsafe extern "system" fn wnd_proc(
    hwnd: HWND,
    msg: u32,
    wparam: WPARAM,
    lparam: LPARAM,
) -> LRESULT {
    match msg {
        WM_CLOSE => {
            // Ignore WM_CLOSE during installation - prevent external closure
            LRESULT(0)
        }
        WM_DESTROY => {
            // Don't call PostQuitMessage - the main loop will recreate the window if needed
            // and will control when to actually quit
            LRESULT(0)
        }
        _ => DefWindowProcW(hwnd, msg, wparam, lparam),
    }
}
