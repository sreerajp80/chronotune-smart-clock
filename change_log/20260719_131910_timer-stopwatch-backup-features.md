# Change log — Timer / Stopwatch / Backup features

Implements plan `plans/20260719_124948_timer-stopwatch-backup-features.md`.
Build verified: `:app:compileDebugKotlin` and `:app:assembleDebug` both succeed.

## Feature A — Ring auto-silence (per-alarm + global default)

A ring can now stop itself after a chosen time instead of only "ring until dismissed".
Values: Never / 1 / 5 / 10 / 15 min. Each alarm keeps its own value; a new alarm seeds from
the global default; timers use the global default. Existing alarms upgrade to "Never".

- `data/Models.kt` — added `autoSilenceMinutes: Int = 0` to the `Alarm` entity.
- `data/AppDatabase.kt` — DB version `4 → 5` with non-destructive `MIGRATION_4_5`
  (`ALTER TABLE alarms ADD COLUMN autoSilenceMinutes INTEGER NOT NULL DEFAULT 0`).
- `AppPrefs.kt` — new global pref `defaultAutoSilenceMinutes` (default 0) with StateFlow,
  setter, cold-read `getDefaultAutoSilenceMinutes`, and `AUTO_SILENCE_CHOICES`.
- `AlarmsScreen.kt` — "Auto-silence after" chip row in the alarm edit sheet (seeded from the
  alarm's value when editing, or the global default for a new alarm); threaded through the
  `onSave` callback and both add/update call sites.
- `ui/ClockViewModel.kt` — `addAlarm` gained an `autoSilenceMinutes` parameter (defaults to
  the global pref).
- `SettingsScreen.kt` — "Default auto-silence" card on the Alarm tab; updated the tab's note.
- Firing path plumbed end-to-end so the value survives cold process / reboot / snooze:
  - `ui/AlarmScheduler.kt` — carries `AUTO_SILENCE_MIN` (alarm's value for alarms; global
    default for timers).
  - `ui/Receivers.kt` — `AlarmReceiver` reads the extra into `ActiveAlarm` and into the
    rebuilt `Alarm` for repeating re-arm; `snooze()`/`scheduleSnooze()`/`AlarmSnoozeReceiver`
    carry it onto the snoozed re-ring; new field on `ActiveAlarmState.ActiveAlarm`.
  - `ui/AlarmService.kt` — reads `EXTRA_AUTO_SILENCE`; when > 0 posts a delayed
    `stopAlarmAndSelf()` (Handler) and cancels it on teardown / destroy. Carries the value on
    `startIntent` and the snooze notification action.

## Feature B — "+1 min" on the finished-timer ring

- `ui/TimerEngine.kt` — new `restartForOneMinute(...)` puts a finished timer back to RUNNING
  for 60 s and re-arms its ring.
- `ui/AlarmService.kt` — the finished-timer ring notification now shows a "+1 min" action
  (timer id derived as ring id − `RING_ID_OFFSET`).
- `ui/Receivers.kt` — new `TimerAddMinuteReceiver`: stops the current ring, then calls
  `restartForOneMinute`.
- `AndroidManifest.xml` — registered `TimerAddMinuteReceiver`.

## Feature C — Stopwatch share / export laps

- `StopwatchScreen.kt` — a Share icon in the header (shown only when laps exist) sends a
  plain-text lap summary (total + `Lap N — elapsed (+delta)`) via an `ACTION_SEND` chooser.
  Added `buildLapShareText` helper. No new permissions.

## Feature D — Backup & restore (export / import)

JSON backup of alarms, world clocks, music schedules and timer presets via the system file
picker (SAF — no storage permission). Running timers / stopwatch excluded.

- `data/BackupManager.kt` (new) — versioned `exportToJson` / `importFromJson` with
  Merge/Replace modes; ids dropped on import so Room assigns fresh keys; older backups without
  the new auto-silence field default to 0.
- `data/Daos.kt` — added one-shot reads (`getAll*Once`) and `deleteAll*` for alarms, world
  clocks, music schedules, timer presets.
- `data/repository/ClockRepository.kt` — exposed the new DAO helpers.
- `ui/ClockViewModel.kt` — `exportBackup(uri)` / `importBackup(uri, mode)` doing file IO on
  `Dispatchers.IO`; a `backupEvent` StateFlow for UI toasts; Replace cancels currently-armed
  alarms/music before deletion, and both modes re-arm everything afterward.
- `ClockAppScreen.kt` — passes the `ClockViewModel` into `SettingsScreen`.
- `SettingsScreen.kt` — "Backup & restore" card on the About tab with Export / Import buttons
  and a Merge/Replace dialog; result toasts.

## Notes

- No new Android permissions.
- World-clock city search was already present (`LocationSearchDialog`); no change made.
- The auto-silence self-stop is best-effort: if the OS reaps the process before the timeout,
  playback stops anyway.
