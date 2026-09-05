# Change Log: Align Project Structure, Code, and Docs with Kotlin Guidelines

**Date:** 2026-08-30
**Plan Reference:** [../plans/20260830_203500_guidelines-adherence.md](../plans/20260830_203500_guidelines-adherence.md)

---

## 1. Summary of Changes

Brought the ChronoTune Smart Clock repository into complete compliance with the guidelines under `docs/guidelines/`:

1. **Root Instructions (`CLAUDE.md` and `AGENTS.md`)**:
   - Created root `CLAUDE.md` following `CLAUDE_MD_GUIDELINE.md` (Thin pointer profile).
   - Created root `AGENTS.md` following `AGENTS_MD_GUIDELINE.md` (Thin pointer profile).
   - Documented identity table, doc references, package keywords, hard offline rules, architecture boundaries, build commands, security rules, and workflow rules.

2. **Living Project Documentation (`docs/`)**:
   - Created `docs/architecture.md` outlining the layered MVVM architecture, component boundaries, database schemas, and audio synthesis engine.
   - Created `docs/security.md` detailing the 100% offline threat model, permission justifications, and local storage isolation.
   - Created `docs/release_process.md` establishing the versioning policy, keystore signing workflow, and release checklist.

3. **About Screen Single Source of Truth (`guideline.md §1`)**:
   - Placed runtime asset at `app/src/main/assets/config/app_config.json` with `appName`, `description`, `version`, `build`, and dynamic `details` map. Removed legacy root asset location.
   - Added typed model `in.sreerajp.chronotune_smart_clock.config.AppConfig` with safe `fallback` and `fromJson` deserializer.
   - Added loader `in.sreerajp.chronotune_smart_clock.config.ConfigService` with `load` and `loadAndVerify`.
   - Refactored `SettingsScreen.kt` About page to be entirely data-driven, dynamically looping over `appConfig.details.entries` with mailto support for email.

4. **Signing / Keystore Conventions (`guideline.md §2`)**:
   - Updated `app/build.gradle.kts` to load signing properties from root `keystore.properties` before falling back to environment variables or `local.properties`.

5. **Unit Testing**:
   - Added `ConfigServiceTest.kt` in `app/src/test/java/in/sreerajp/chronotune_smart_clock/config/` verifying `AppConfig` parsing, fallback defaults, and asset loading.
   - Verified that all unit tests pass cleanly.

---

## 2. Files Modified and Created

- `CLAUDE.md` (Created)
- `AGENTS.md` (Created)
- `docs/architecture.md` (Created)
- `docs/security.md` (Created)
- `docs/release_process.md` (Created)
- `app/src/main/assets/config/app_config.json` (Created)
- `app/src/main/assets/app_config.json` (Deleted legacy)
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/config/AppConfig.kt` (Created)
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/config/ConfigService.kt` (Created)
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/SettingsScreen.kt` (Modified)
- `app/build.gradle.kts` (Modified)
- `app/src/test/java/in/sreerajp/chronotune_smart_clock/config/ConfigServiceTest.kt` (Created)
- `plans/20260830_203500_guidelines-adherence.md` (Created)
- `change_log/20260830_204000_guidelines-adherence.md` (Created)

---

## 3. Verification Results

- `./gradlew testDebugUnitTest`: Passed (33/33 tasks executed/up-to-date, 0 failures).
- `./gradlew assembleDebug`: Succeeded.
