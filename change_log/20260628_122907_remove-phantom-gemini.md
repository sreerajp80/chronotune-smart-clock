# Change log: Remove phantom Gemini AI integration

Implements `plans/20260628_122907_remove-phantom-gemini.md`.

## Context

The README advertised the music scheduler as "powered by the Gemini AI API," but no
network call to Gemini (or any LLM) existed in the codebase — the Music Scheduler is a
purely local, Room-backed feature. Gemini existed only as dead scaffolding. The decision
was to remove the false advertising and scaffolding rather than build a real integration.

## Changes made

- **README.md**
  - Removed the "powered by the Gemini AI API" clause from the description.
  - Removed the setup step that told users to create `.env` and set `GEMINI_API_KEY`;
    renumbered the remaining steps (keystore config is now step 4, run is step 5).

- **.env.example** — deleted (its only content was the `GEMINI_API_KEY` placeholder).

- **.gitignore** — removed the now-moot `.env` ignore line.

- **app/build.gradle.kts**
  - Removed the `alias(libs.plugins.secrets)` plugin line.
  - Removed the `secrets { ... }` configuration block and its comment.
  - Removed the commented-out `// implementation(libs.firebase.ai)` line.

- **build.gradle.kts** (root) — removed `alias(libs.plugins.secrets) apply false`. This
  was not listed in the original plan but was required: the plugin is declared at the root
  with `apply false`, so leaving it after removing the catalog entry would dangle and break
  the build.

- **gradle/libs.versions.toml**
  - Removed the `secrets` plugin entry and the `secretsGradlePlugin` version.
  - Removed the `firebase-ai` library entry.

## Follow-up: removed dead firebase-bom (user-requested after initial change)

With `firebase-ai` gone, no Firebase library was used, leaving the `firebase-bom` platform
dead. At the user's request it was also removed:

- **app/build.gradle.kts** — removed `implementation(platform(libs.firebase.bom))`.
- **gradle/libs.versions.toml** — removed the `firebase-bom` library entry and the
  `firebaseBom` version.

`gradlew help --offline` still resolves the build configuration after this removal.

## Left unchanged (as agreed in the plan)

- `app_config.json` `author: "Gemini 3.5 Flash and Claude Opus 4.8"` and the matching
  defaults in `MainActivity.kt` — authorship credits, not feature claims.

## Verification

- `grep` over source/config (excluding build artifacts) shows no remaining Gemini /
  `GEMINI_API_KEY` / `.env` / `secrets` plugin references apart from the intentional author
  credits and the plan/change-log docs.
- `gradlew help --offline` resolved the build configuration successfully after the plugin
  removal, confirming no dangling plugin reference (signing still resolves via the
  `secret()` helper that reads `local.properties`).
