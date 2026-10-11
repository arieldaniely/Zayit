# Islands resize glow — patch for dev

Target: `kdroidFilter/Zayit`, branch `dev`, commit `357acfc23481ff76092b68c405c1c3b6c5380d48`.

## Origin

The original feedback was introduced in `34b8fe6f6040c6fbc9f51d3752d9cd6ae7045653`
(29 July 2026, “Add pinned tabs and resize feedback”). Its final appearance was refined in
`f670d25201400c9a5fdab5acf4ae72f1bde78949`, `f98c3467586ccc9050661d2ebd8ed70c026733ac`, and
`ad492c1b6b984beb13e648ffee32dc3d4fababdb`.

This patch includes only resize feedback. It is a new implementation against the current dev API.

## Implementation

- One internal `ResizeHandle` component serves both divider orientations. The split-pane library
  continues to own drag handling; feedback observes events without consuming them.
- Compose's standard hover interactions control hover feedback. Primary-button feedback survives
  movement outside the handle and resets on release or pointer-input cancellation.
- A 140 ms transition preserves the original hover/drag intensities, pointer-centered gradient,
  layered glow, and fading near the islands' rounded corners.
- The focused outline color comes from `JewelTheme`, including custom accent colors.
- `drawWithCache` caches the gradient and geometry. Animation state is read during drawing, so
  animation frames do not reconstruct the gradient or recompose the split-pane contents.
- The current dev branch uses Islands throughout. `showDivider = true` keeps its explicit divider;
  glow is enabled only when `showDivider = false`. Hidden splitters remain absent.
- Tests cover both orientations, hover versus press intensity, release outside the handle, and
  disabling/re-enabling feedback during a press.

## Apply

From a clean checkout of the target dev revision, with the patch available locally:

```powershell
git switch dev
git apply --check --whitespace=error "path/to/islands-resize-glow-dev.patch"
git apply "path/to/islands-resize-glow-dev.patch"
.\gradlew.bat :SeforimApp:jvmTest --tests '*ResizeHandleTest'
.\gradlew.bat :SeforimApp:ktlintJvmMainSourceSetCheck :SeforimApp:ktlintJvmTestSourceSetCheck
.\gradlew.bat :SeforimApp:run
```

If dev has advanced, run the application check first; do not force an unsuccessful application.

## Visual verification

Open a book with navigation/content/commentary panes. Hover and drag both vertical and horizontal
splitters. The glow should use the active accent, brighten while dragging, follow the pointer along
the divider, stay clear of rounded corners, and fade after the pointer leaves. Release outside the
handle, then hide and reopen a pane; feedback must not remain active. Also check light/dark themes
and a splitter with an explicit divider.

## Verification status

Patch application to a temporary index containing the exact target dev commit passed with
`git apply --cached --check --whitespace=error`.

Kotlin JVM compilation passed. `ResizeHandleTest` passed (2 tests, 0 failures, 0 errors), including
both divider orientations. Focused ktlint checks passed for the changed main and test source files.

## Local preview build

The local Windows preview uses the same dev source plus this patch, with Java 25 and JVM packaging
(`createDistributable` / `packageNsis`). A temporary Gradle init script selects the installed runtime
and disables Nucleus runtime optimizations for this preview only. These overrides are not in the
patch. The default Java 27 preparation failed with a Windows folder-move access error; release
minification also encountered a Maven dependency download failure. Official CI can use its normal
runtime and release packaging configuration when applying the patch.

The dev app's book database is separate from the fork's database. The local preview is being
prepared with an independent copy of the existing book database; it does not use a hard link.

NSIS installer packaging passed (BUILD SUCCESSFUL). A temporary, narrowly scoped build helper
worked around the Windows access error while moving the installer staging runtime. It validates
that both paths belong to this build's installer staging tree; it is not included in the patch
or application.

Installer: build/installers/Zayit-dev-resize-glow-357acfc2-setup.exe (139,527,571 bytes).
SHA-256: 6BF4AFF6162879A3749F9C8D49803E1C51F5AE906ACF18B5AF6A44FE3EB471D7.
The copied installer matches the build output's SHA-512 and has a valid Windows executable header.
The installer was built but has not been installed; the packaged application was opened directly.
