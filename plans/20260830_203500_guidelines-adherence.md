# Plan: Align Project Structure, Code, and Docs with Kotlin Guidelines

**Date:** 2026-08-30
**Status:** In Progress
**Scope:** Repository-wide adherence to `docs/guidelines/`

---

## 1. Objectives

Bring the ChronoTune Smart Clock codebase, documentation, and configuration into complete compliance with the guidelines under `docs/guidelines/`:
1. Create root `CLAUDE.md` and `AGENTS.md` adhering to `CLAUDE_MD_GUIDELINE.md` and `AGENTS_MD_GUIDELINE.md`.
2. Populate living project docs (`docs/architecture.md`, `docs/security.md`, `docs/release_process.md`) according to `DOCS_FOLDER_GUIDELINE.md`.
3. Standardize About screen configuration to Pattern A in `guideline.md §1`:
   - Config asset placed at `app/src/main/assets/config/app_config.json`.
   - Typed model `AppConfig` and loader `ConfigService` in `in.sreerajp.chronotune_smart_clock.config`.
   - Data-driven rendering in `SettingsScreen.kt`.
4. Update `app/build.gradle.kts` to support `keystore.properties` per `guideline.md §2`.
5. Add unit tests for `AppConfig` and `ConfigService`.

---

## 2. Target Files

- `CLAUDE.md` (New)
- `AGENTS.md` (New)
- `docs/architecture.md` (New)
- `docs/security.md` (New)
- `docs/release_process.md` (New)
- `app/src/main/assets/config/app_config.json` (New)
- `app/src/main/assets/app_config.json` (Delete legacy)
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/config/AppConfig.kt` (New)
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/config/ConfigService.kt` (New)
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/SettingsScreen.kt` (Modify)
- `app/build.gradle.kts` (Modify)
- `app/src/test/java/in/sreerajp/chronotune_smart_clock/config/ConfigServiceTest.kt` (New)

---

## 3. Verification

- Run `./gradlew testDebugUnitTest` to ensure all unit tests pass.
- Verify all relative markdown links across docs and instruction files.
