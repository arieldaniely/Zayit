@echo off
setlocal enabledelayedexpansion

echo === Zayita Installer Build Script ===
echo.

cd /d "%~dp0"

:: Create resources directory if it doesn't exist
if not exist "resources" mkdir resources

:: Copy splash image
echo Copying splash.png...
if exist "..\art\splash.png" (
    copy /Y "..\art\splash.png" "resources\splash.png" >nul
) else if exist "..\SeforimApp\src\jvmMain\assets\common\splash.png" (
    copy /Y "..\SeforimApp\src\jvmMain\assets\common\splash.png" "resources\splash.png" >nul
)
if not exist "resources\splash.png" (
    echo ERROR: Failed to find splash.png
    exit /b 1
)
echo OK

:: Copy NSIS installer and get its name
echo Copying NSIS installer...
set "NSIS_NAME="

:: Try GraalVM path first, then Release path
for %%p in (
    "..\SeforimApp\build\compose\binaries\main\graalvm-nsis"
    "..\SeforimApp\build\compose\binaries\main-release\nsis"
) do (
    for %%f in ("%%~p\zayita-*-nsis.exe" "%%~p\zayit-*-nsis.exe") do (
        set "NSIS_NAME=%%~nf"
        copy /Y "%%f" "resources\zayita-nsis.exe" >nul
        goto :nsis_copied
    )
)

echo ERROR: NSIS installer not found
echo Run: gradlew :SeforimApp:packageGraalvmNsis or gradlew :SeforimApp:packageReleaseNsis
exit /b 1

:nsis_copied
echo OK - Found: %NSIS_NAME%.exe

:: Build Rust project
echo.
echo Building Rust installer...
cargo build --release
if errorlevel 1 (
    echo ERROR: Cargo build failed
    exit /b 1
)

:: Rename exe to match NSIS name
set "EXE_NAME=%NSIS_NAME%.exe"
echo.
echo Renaming to %EXE_NAME%...
move /Y "target\release\zayita-installer.exe" "target\release\%EXE_NAME%" >nul
if errorlevel 1 (
    echo ERROR: Failed to rename exe
    exit /b 1
)
echo OK

echo.
echo === Build Complete ===
echo Output: target\release\%EXE_NAME%
echo.

endlocal
