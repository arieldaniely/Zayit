# Upstream integration, 2026-10-12

Branch: `codex/integrate-upstream-2026-10-12`.

Imported public upstream app through `b2a0f8bb` and library through `b4e9d66`, preserving their history. Public implementations supersede overlapping fork implementations. Local semantic search, personal books, PDF, portable paths and shared study have adapters to the new APIs. Dictionary readers support upstream and legacy source layouts. Feedback thumbs and entry events are connected to the new results UI. Shared study is under Tools.

## Working local development build

`artifacts/upstream-integration/Run-Zayita.cmd` runs the locally built JVM version using this computer's installed JDK and dependency cache. The launcher is machine-local and is not a distributable installer. Startup was verified: window `זיתא - דף הבית`, and catalog loaded with 1,363 categories and 7,889 books. The graphics engine reported a fallback from the Nucleus OpenGL share.

## Validation and limits

- Library search: 133 tests passed.
- App focused baseline: 63 tests passed, including feedback UI, dictionaries, history, personal imports, extraction, semantic bundles and calendar plans.
- Final app production sources compiled and launch files were generated successfully.
- Final expanded app run encountered Windows AccessDenied during staging a semantic asset directory. Source now has bounded move retries; this last change is not yet included in the running artifact and needs a rebuild and rerun.
- Generator/repository validation remains incomplete: Windows file locks affected arbitration tests. Old fork incremental-writer tests are superseded by upstream's full rebuild writers. Additional full-suite, native installer, actual semantic-model quality and visual regression checks remain outstanding.
- The independent siddur selects installed source sections using calendar rules and source headings. It does not rewrite variants embedded inside prayer text, and does not claim parity with the inaccessible private template engine. These variants are explicitly shown to the reader.
- No private upstream packages or model weights are downloaded. ProGuard remains enabled for release packaging.

The full upstream feature-by-feature audit is not finished. This checkpoint prioritizes the user's request for a running version within ten minutes.
