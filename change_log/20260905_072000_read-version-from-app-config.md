# Change Log: Read App Version and Build Directly from app_config.json

**Date:** 2026-09-05  
**Plan:** [plans/20260905_072000_read-version-from-app-config.md](plans/20260905_072000_read-version-from-app-config.md)  
**Author:** AI Pair Programmer  

---

## 1. Summary of Changes

Configured the Gradle build script to dynamically parse the version name and build number from `app/src/main/assets/config/app_config.json`. This unifies version management so that `app_config.json` serves as the single source of truth for both runtime About screen display and Android APK/AAB build packaging.

---

## 2. Detailed Modifications

### Build Configuration
- **`app/build.gradle.kts`**:
  - Added `parseAppConfigVersion()` helper that reads `src/main/assets/config/app_config.json` and extracts `version` and `build` with fallbacks (`"1.0.0"` and `1`).
  - Assigned `versionName = appVersionName` and `versionCode = appVersionCode` in `defaultConfig`.

### Documentation
- **`docs/release_process.md`**:
  - Updated Section 2 (Versioning Policy) to declare `app/src/main/assets/config/app_config.json` as the single source of truth for versioning.
  - Updated Section 5 (Checklist) to clarify that `build.gradle.kts` automatically loads the version and build values from `app_config.json`.

---

## 3. Verification

- Executed `./gradlew testDebugUnitTest`: all unit tests executed and passed (`BUILD SUCCESSFUL in 16s`).
- Verified `BuildConfig.java`: `VERSION_CODE = 2` and `VERSION_NAME = "2.10.12"` were accurately generated from `app_config.json`.
