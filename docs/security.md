# Security — ChronoTune Smart Clock

This document details the security posture, offline architecture, permission model, and data privacy protections for ChronoTune Smart Clock.

**Read first:** [../AGENTS.md](../AGENTS.md) · [../CLAUDE.md](../CLAUDE.md) · [architecture.md](architecture.md) · [guidelines/security.md](guidelines/security.md)

---

## 1. Security Scope

- Product: ChronoTune Smart Clock
- Data sensitivity level: Low (local time management data, alarms, audio preferences)
- Engineering standard profiles in force:
  - `Core Baseline`
  - `Production App Extension`
- Platform: Android (minSdk 24, targetSdk 36)

---

## 2. Threat Model & Privacy Principles

1. **100% Offline by Design**: The application does not declare or use `android.permission.INTERNET`. No outbound network connections, remote telemetry, third-party analytics, or cloud trackers exist in the binary.
2. **Local Data Isolation**: All alarms, timers, schedules, and preferences remain strictly within the app's sandboxed private storage (`/data/data/in.sreerajp.chronotune_smart_clock/`).
3. **Safe Data Export**: Backups generated via `BackupManager` are plain, self-describing JSON files selected directly by the user via the Android Storage Access Framework (SAF).

---

## 3. Granular Permission Profile

The app requests only minimal permissions strictly necessary for clock functionality:

| Permission | Type | Justification |
|---|---|---|
| `POST_NOTIFICATIONS` | Runtime (Android 13+) | Displaying alarm alerts, timer countdowns, and stopwatch chronometers. |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Special / System | Triggering alarm rings and timer finishes at exact clock times. |
| `USE_FULL_SCREEN_INTENT` | Normal / Runtime (Android 14+) | Presenting full-screen alarm ringing overlay over the lock screen. |
| `RECEIVE_BOOT_COMPLETED` | Normal | Rescheduling active alarms and music sessions across device restarts. |
| `WAKE_LOCK` | Normal | Preventing CPU sleep during active alarm tone synthesis. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Foreground Service | Synthesizing alarm audio and managing playback notifications. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Foreground Service | Running persistent timer countdowns and stopwatch tracking. |
| `READ_CALENDAR` | Optional Runtime | Importing regional holidays into the Special Days registry upon explicit user action. |

---

## 4. Logging & Secrets Policy

- **No Secrets Policy**: The app contains zero hardcoded API keys, authorization tokens, or backend credentials.
- **Log Hygiene**: User-created alarm titles, file URIs, and custom dates are never logged in release builds.
- **Keystore Protection**: Keystore signing credentials are kept strictly out of git version control via `.gitignore` and `keystore.properties`.
