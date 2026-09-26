# Repository Guidelines

## Project Structure & Module Organization
- `SeforimApp/`: Kotlin Multiplatform app (Compose). Shared code in `src/commonMain`, desktop in `src/jvmMain`, Android in `src/androidMain`.
- `SeforimLibrary/`: composite build providing domain + persistence (`core`, `dao`, `generator`), consumed as `io.github.kdroidfilter.seforimlibrary:*`.
- Shared modules: `htmlparser/`, `icons/`, `logger/`, `navigation/`, `pagination/`, `texteffects/`, `jewel/`, `network/`.
- Codegen: `cataloggen/` generates `PrecomputedCatalog.kt` used by the app.
- Tests live next to code under `src/<target>Test/kotlin` (e.g., `SeforimApp/src/jvmTest`).

## Build, Test, and Development Commands
- `./gradlew build` — build all modules.
- `./gradlew :SeforimApp:run` — run the desktop app.
- `./gradlew :SeforimApp:hotRunJvm` / `./gradlew :SeforimApp:reload` — desktop hot reload loop.
- `./gradlew :SeforimApp:installDebug` — install Android debug build.
- `./gradlew :SeforimApp:jvmTest` (or `./gradlew test`) — run tests (desktop-only or all).
- `./gradlew :SeforimApp:lint` — run static checks.
- `./gradlew ktlintCheck` / `./gradlew ktlintFormat` — check or auto-format code style with ktlint.
- `SEFORIM_DB=/path/to/seforim.db ./gradlew :cataloggen:generatePrecomputedCatalog` — regenerate the precomputed catalog.

## Coding Style & Naming Conventions
- Kotlin + Compose; 4-space indentation; keep lines ~120 chars (max line length: 140).
- Naming: `PascalCase` for types/composables, `camelCase` for functions/properties, `UPPER_SNAKE_CASE` for constants.
- UI: prefer `SomethingView` and `SomethingViewModel` naming patterns.
- Keep shared logic in `commonMain`; avoid leaking platform-specific types across source sets.
- DI uses Metro (`AppGraph`, `@Provides`); access via `LocalAppGraph` (avoid singletons).

### Ktlint Formatting & Chaining Rules
- **Chaining after multiline lambdas (`chain-wrapping`)**: When continuing a call chain after a multiline lambda or block ending with `}` on its own line, attach the dot immediately to the closing brace (`}.method(...)`), never on a new line:
  ```kotlin
  // Correct
  items
      .filter {
          it.isActive
      }.map { it.name }

  // Incorrect (causes "Unexpected newline before '.'")
  items
      .filter {
          it.isActive
      }
      .map { it.name }
  ```
- **Chained member navigation & assertions (`chain-method-continuation`)**: In assertions or nested calls with long property/call chains, break the chain across lines and put each chained method or safe call (`.`, `?.`) on a new line:
  ```kotlin
  // Correct
  assertTrue(
      coordinator.state.value.participants
          .none { it.id == "peer" },
  )
  assertEquals(
      "local",
      coordinator.state.value.notes["note"]
          ?.body,
  )

  // Incorrect (causes "Expected newline before '.'" / "Expected newline before '?.'")
  assertTrue(coordinator.state.value.participants.none { it.id == "peer" })
  ```
- **Trailing commas**: Use trailing commas in multiline argument, parameter, and collection lists.

## Localization & Resources
- Never hardcode user-visible text; add keys to `SeforimApp/src/commonMain/composeResources/strings.xml`.
- The required locale for user-facing text is Hebrew (`he`); provide Hebrew strings for all new keys.

## Testing Guidelines
- Use Kotlin Test (and Compose UI testing where applicable); keep tests small and focused.
- Naming: mirror the source name with `...Test.kt` (e.g., `BookContentViewModelTest.kt`).

## Commit & Pull Request Guidelines
- Git history favors imperative subjects (often `Refactor: ...`, `Add ...`, `Fix ...`); keep the first line scannable.
- PRs should include: what changed, modules touched, linked issues, screenshots/GIFs for UI changes, and verification steps (e.g., `:SeforimApp:run`).

## Security & Configuration Tips
- Do not commit secrets or API keys; keep machine-specific settings in `local.properties`.
