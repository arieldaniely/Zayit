# Website screenshots

The website contains ten Zayit scenarios in light and dark variants. Public PNG files are 1463x811
and are mirrored in art/ and website/public/art/.

## Recorded application state

The authoritative internal-state fixtures are stored in:

    SeforimApp/src/jvmTest/resources/website-screenshots/

Each .pb file is a serialized DesktopsState captured from the real application. It contains the
desktop/window layout, tabs and exact titles, selected tab, book and line IDs, navigation and TOC
state, visible content panes, split positions, scroll anchors, commentary/source selections, and
persisted search results.

Theme is deliberately not part of the fixture. Replay sets LIGHT and DARK explicitly through the
opt-in screenshot bridge. Consequently a theme mistake made during the interactive recording (the
reversed PIRUSHIM preview pair) does not affect automated output.

## Deterministic GitHub replay

The manual Replay website screenshots workflow runs on a pinned Windows Server 2022 runner. In
screenshot mode the title bar always uses the Windows control vectors on the right, independent of
the host platform's title-bar defaults. The replay runner also removes any Win32 non-client styles
that the Nucleus AWT backend may leave on the HWND and checks them again before every capture. This
prevents the Windows Server fallback title bar from surrounding the Compose-rendered Windows 11
controls. It normalizes the borderless HWND to 1463x811 once before replay begins, then publication
fails instead of accepting a legacy or incorrectly sized frame if Windows changes it. The hosted
runner's basic 1024x768 adapter is too short, so
the workflow installs a version- and SHA-256-pinned signed indirect display driver and makes a
1920x1080 monitor primary before Zayit starts. Zayit creates its 1463x811 native surface before the
first frame and renders at Compose density 1.0. The frame is captured directly at 1463x811: it is
never enlarged, downsampled, or resized between fixtures.

For each fixture the workflow:

1. restores the internal application state and recreates the selected tab's ViewModels;
2. enforces the scenario-specific transient state, sidebars, search mode, queries, and panes;
3. reapplies and verifies text size 32 (nine increments) and two commentators per page;
4. selects the light theme and captures the decorated application window;
5. selects the dark theme and captures the same state again;
6. verifies density 1.0 and the direct 1463x811 frame, rejects partial/black PrintWindow surfaces,
   and publishes both art directories without resampling.

The workflow uploads the generated images, manifest, and application log. When requested, it creates
a result branch and opens a pull request against the branch from which the workflow was dispatched;
it never commits generated images directly to that source branch.

The clipboard scenario selects the exact recorded sentence and opens its context menu deterministically.

## Recording new fixtures on Windows

Close all Zayit windows, then run:

    python scripts\capture-art-screenshots.py --launch-app

The recorder launches the real app with a local file bridge. For each scenario it prompts for the
state, exports a .pb fixture, and creates local previews under
art/capture-preview/<timestamp>/. Pressing Enter during the countdown captures immediately.

To record one scenario:

    python scripts\capture-art-screenshots.py --launch-app --scenario HOME

A complete checked run can be copied into the repository with --publish; partial publication is
refused.
