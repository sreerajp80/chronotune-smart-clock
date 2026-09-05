# Release Process — ChronoTune Smart Clock

This document defines the release runbook, signing configuration, build commands, and quality verification steps for shipping ChronoTune Smart Clock.

**Read first:** [../AGENTS.md](../AGENTS.md) · [../CLAUDE.md](../CLAUDE.md) · [architecture.md](architecture.md) · [guidelines/release_process.md](guidelines/release_process.md)

---

## 1. Release Scope

- App: ChronoTune Smart Clock
- Package: `in.sreerajp.chronotune_smart_clock`
- Supported release platform: Android
- Engineering standard profiles in force:
  - `Core Baseline`
  - `Production App Extension`

---

## 2. Versioning Policy

- Version format: `versionName` = `MAJOR.MINOR.PATCH` (e.g. `2.10.12`); `versionCode` = sequential integer (e.g. `2`).
- Source of truth: `app/src/main/assets/config/app_config.json` (`version` and `build`).
- Gradle integration: `app/build.gradle.kts` dynamically reads `versionName` and `versionCode` directly from `app_config.json` at build time.
- Git tag format: `vX.Y.Z` (e.g. `v2.10.12`).

---

## 3. Signing Configuration

The release build is signed using a local JKS keystore specified in `keystore.properties` at the project root:

```properties
storePassword=<your-store-password>
keyPassword=<your-key-password>
keyAlias=chronotune-smart-clock
storeFile=keystore.jks
```

> **Security Reminder:** `keystore.properties` and all `*.jks` / `*.keystore` files MUST remain git-ignored and never committed to source control.

---

## 4. Build Commands

```bash
# Clean build environment
./gradlew clean

# Run full unit test suite
./gradlew testDebugUnitTest

# Run Android Lint checks (must have 0 errors)
./gradlew lint

# Build release APK
./gradlew assembleRelease

# Build release App Bundle (for store distribution)
./gradlew bundleRelease
```

---

## 5. Release Verification Checklist

- [ ] All unit tests pass cleanly: `./gradlew testDebugUnitTest`.
- [ ] Android Lint reports zero errors: `./gradlew lint`.
- [ ] `app_config.json` version and build are updated and automatically loaded by `app/build.gradle.kts`.
- [ ] Release APK is signed properly with production keystore.
- [ ] About screen displays updated version, build, and metadata dynamically.
- [ ] Alarm playback, foreground service elevation, and full-screen overlay function correctly on a physical device.
