# Change log: Pause an alarm for a date range

Implements plan `plans/20260617_093853_alarm-pause-date-range.md`.

## Summary

Added the ability to pause an alarm over a selected date range. In the configure/edit alarm
sheet there is now a **"Pause alarm"** row; tapping it opens a calendar (Material3
`DateRangePicker`) where the user taps a start and end date. While today falls inside the
range the alarm does not ring; once the range passes it resumes automatically. The editor row
and the alarm list card both indicate when a pause is configured (and whether it's currently
active). The build (`:app:compileDebugKotlin`) succeeds.

## Changes by file

- **`app/src/main/java/in/sreerajp/chronotune_smart_clock/data/Models.kt`**
  - Added `pauseStartMillis: Long = 0L` and `pauseEndMillis: Long = 0L` to `Alarm`
    (UTC-midnight millis as produced by `DateRangePicker`; 0 = no pause).
  - Added helpers: `isPauseConfigured()`, `isPausedOnEpochDay(epochDay)`, `isPausedNow()`, and
    companion utilities `MILLIS_PER_DAY`, `localCalendarToEpochDay(cal)`, `todayEpochDay()`.
    Comparison is done by epoch day to avoid timezone drift.

- **`app/src/main/java/in/sreerajp/chronotune_smart_clock/data/AppDatabase.kt`**
  - Bumped Room `version` 1 → 2.
  - Added `MIGRATION_1_2` (`ALTER TABLE alarms ADD COLUMN pauseStartMillis/pauseEndMillis`)
    and registered it via `.addMigrations(MIGRATION_1_2)`. Existing alarms/world-clocks/
    music-schedules are preserved (destructive fallback kept as a backstop).

- **`app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmScheduler.kt`**
  - `nextTriggerTime(...)` is now pause-aware: it skips any candidate date inside the pause
    window. Repeating-alarm forward scan extended from 7 to 366 days (a pause can exceed a
    week); one-shot alarms landing in the window are pushed to the day after it ends. New
    params default to `0L` so `scheduleMusic` is unaffected.
  - `scheduleAlarm()` passes the alarm's pause window into `nextTriggerTime` and adds
    `PAUSE_START` / `PAUSE_END` intent extras for the receiver's re-arm.

- **`app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/Receivers.kt`**
  - `AlarmReceiver.onReceive()` reads `PAUSE_START`/`PAUSE_END`, threads them into the re-arm,
    and adds a safety guard that suppresses the ring if the alarm is currently within its
    pause window.
  - `rescheduleNextOccurrence(...)` carries the pause window onto the re-armed `Alarm`.
  - Added private `isPausedNow(start, end)` helper.

- **`app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ClockViewModel.kt`**
  - `checkInAppTriggers()` (in-app foreground ticker) now skips alarms where `isPausedNow()`.
  - `addAlarm(...)` gained `pauseStartMillis`/`pauseEndMillis` params (default 0) and sets them.

- **`app/src/main/java/in/sreerajp/chronotune_smart_clock/MainActivity.kt`**
  - `AlarmEditDialog`: new pause state, a "Pause alarm" row (shows the configured range in a
    chip or "Not set"), and a `DatePickerDialog` hosting `DateRangePicker` with **Set**,
    **Clear**, and **Cancel** actions. `onSave` signature extended with the two pause values;
    Save button passes them.
  - `AlarmsScreen`: both add and edit `onSave` call sites updated to forward the pause window.
  - `AlarmCard`: added a badge showing "Pause · <range>" (or "Paused · <range>" highlighted
    when the pause is currently active).
  - Added private `formatPauseRange(start, end)` helper (formats in UTC to match the picker).

## Behavior

- No pause (both 0) → unchanged.
- Pause set and today inside `[start, end]` → silent on both firing paths (AlarmManager +
  in-app ticker); AlarmManager is armed for the first valid occurrence after the window.
- After the window passes → alarm resumes on its normal schedule automatically.

## Verification

- `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL.
- Manual device testing (ring suppression during the window, resume after, migration data
  retention) recommended but not yet performed.
