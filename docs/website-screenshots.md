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

The manual Replay website screenshots workflow runs on a pinned Windows Server 2022 runner so the
captured title bar and window controls are the real Windows variants. The hosted runner's basic
1024x768 adapter cannot expose a 4K mode, so the workflow installs a version- and SHA-256-pinned,
signed indirect display driver and makes its 3840x2160 monitor primary before Zayit starts. It then
verifies that at least 2926x1622 physical pixels are available. Zayit creates its 2926x1622 native
surface before the first frame and renders the complete window at Compose density 2.0, matching a
logical 1463x811 viewport. The window is never enlarged after Skia creates its surface and is never
resized between fixtures.

For each fixture the workflow:

1. restores the internal application state without restoring window geometry;
2. applies the recorded visual settings, including text size 32 (nine increments above the minimum)
   and at most two commentators per page;
3. selects the light theme and captures the real decorated Windows application window;
4. selects the dark theme and captures the same state again;
5. verifies Windows platform controls and Compose density 2.0, then rejects partial/black
   PrintWindow surfaces and unexpected window dimensions;
6. downsamples with Lanczos to 1463x811 and publishes both art directories.

The workflow uploads the generated images, manifest, and application log. When requested, it creates
a result branch and opens a pull request against the branch from which the workflow was dispatched;
it never commits generated images directly to that source branch.

The clipboard scenario replays its text selection and native right-click after restoring the fixture.

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
