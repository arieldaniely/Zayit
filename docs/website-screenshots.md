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

The manual Replay website screenshots workflow runs on Ubuntu with an Xvfb display created at
exactly 2926x1622 and 96 DPI. Zayit starts maximized on that display. Every restored window geometry
is normalized to the same dimensions, so Compose never transitions from the operator's smaller
physical display to the capture size.

For each fixture the workflow:

1. restores the internal application state;
2. selects the light theme and captures the real decorated application window;
3. selects the dark theme and captures the same state again;
4. downsamples with Lanczos to 1463x811;
5. verifies the source window dimensions and publishes both art directories.

The workflow uploads the generated images, manifest, application log, and Openbox log. Its optional
commit_changes input commits only the regenerated PNG files.

Transient popups, native context menus, and an active text-selection highlight are not represented by
DesktopsState. Scenarios that require those elements still need explicit UI-action replay before they
can be fully deterministic.

## Recording new fixtures on Windows

Close all Zayit windows, then run:

    python .scriptscapture-art-screenshots.py --launch-app

The recorder launches the real app with a local file bridge. For each scenario it prompts for the
state, exports a .pb fixture, and creates local previews under
art/capture-preview/<timestamp>/. Pressing Enter during the countdown captures immediately.

To record one scenario:

    python .scriptscapture-art-screenshots.py --launch-app --scenario HOME

A complete checked run can be copied into the repository with --publish; partial publication is
refused.