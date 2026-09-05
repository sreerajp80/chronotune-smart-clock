# Plan: Read App Version and Build Directly from app_config.json

**Date:** 2026-09-05  
**Status:** Ready for Review  
**Scope:** Build configuration and release documentation  

---

## 1. Issue Description

Currently, the app's version name and build number are defined statically in `app/build.gradle.kts`:
```kotlin
versionCode = 2
versionName = "2.10.12"
```
At the same time, `app/src/main/assets/config/app_config.json` stores the identical version and build values:
```json
"version": "2.10.12",
"build": "2"
```
Because both files must be edited independently whenever a new release is prepared, there is a risk of version drift.

---

## 2. Proposed Solution

Make `app/src/main/assets/config/app_config.json` the single source of truth for versioning:
1. Update `app/build.gradle.kts` to parse `"version"` and `"build"` from `src/main/assets/config/app_config.json` during the Gradle build configuration.
2. Provide safe fallbacks (`"1.0.0"` and `1`) if the JSON file is missing or values cannot be read.
3. Update `docs/release_process.md` to document that `app_config.json` is now the single source of truth, eliminating manual duplicate entries.

---

## 3. Files to Change

1. `app/build.gradle.kts` (Modify):
   - Add a lightweight parser function to extract `version` and `build` from `src/main/assets/config/app_config.json`.
   - Set `versionCode = appVersionCode` and `versionName = appVersionName` in `defaultConfig`.

2. `docs/release_process.md` (Modify):
   - Update Section 2 (Versioning Policy) and Section 5 (Checklist) to reflect that `app_config.json` is the single source of truth and `build.gradle.kts` loads it automatically.

---

## 4. Verification Plan

1. Run `./gradlew testDebugUnitTest` to ensure the project configures and unit tests succeed.
2. Run `./gradlew lint` or a dry-run assembly task to verify Gradle evaluates `versionCode` and `versionName` without issues.
3. Validate that `ConfigService.loadAndVerify` detects no mismatch between `PackageInfo` and `app_config.json`.
