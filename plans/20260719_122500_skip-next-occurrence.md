# Skip-next-occurrence toggle for repeating alarms

**Status:** completed

## What the issue is

Repeating alarms (e.g. 7:00 AM every weekday) only have two states: **on** (rings
every selected day) and **off** (never rings). There is no way to skip **just the next
firing** and then let the alarm carry on as normal.

Real-world need: it is Thursday night, tomorrow (Friday) is a holiday, but the user
still wants the alarm next Monday. Today they must turn the alarm off and *remember* to
turn it back on before Monday — easy to forget and oversleep. Google Clock and Samsung
Clock solve this with a **"Skip next alarm"** toggle. ChronoTune does not have it.

## How alarms work today (findings)

- `Alarm` (in [Models.kt](../app/src/main/java/in/sreerajp/chronotune_smart_clock/data/Models.kt))
  already stores a **pause window** as UTC-midnight epoch-day millis and has helpers
  (`localCalendarToEpochDay`, `todayEpochDay`, `isPausedOnEpochDay`). Skip-next fits the
  exact same pattern — a single stored epoch day instead of a range.
- [ScheduleTiming.kt](../app/src/main/java/in/sreerajp/chronotune_smart_clock/data/ScheduleTiming.kt)
  `nextTriggerTime(...)` is the **single source of truth** for the next fire time. It
  already skips candidate dates inside the pause window. We add the same skip check for
  the skip-day.
- [AlarmScheduler.kt](../app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmScheduler.kt)
  `scheduleAlarm` calls `nextTriggerTime` and packs alarm fields into the broadcast
  Intent so the receiver can re-arm the next occurrence.
- [Receivers.kt](../app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/Receivers.kt)
  `AlarmReceiver.rescheduleNextOccurrence` rebuilds an `Alarm` from Intent extras (it does
  **not** read the DB) and re-schedules. It also has a pause-window safety guard that
  suppresses a stray ring.
- [ClockViewModel.kt](../app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ClockViewModel.kt)
  `checkInAppTriggers` is an in-app fallback that fires alarms while the app is open; it
  already checks `!alarm.isPausedNow()`.
- [AlarmsScreen.kt](../app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmsScreen.kt)
  `AlarmCard` renders each alarm and already shows a small "Pause" badge — a good place to
  put a "Skip next" control and badge.
- Persistence: Room DB is at **version 5**; migrations are additive `ALTER TABLE`
  columns. Backup round-trips every field explicitly in
  [BackupManager.kt](../app/src/main/java/in/sreerajp/chronotune_smart_clock/data/BackupManager.kt).

## Design

Add one field: `skipNextEpochDay: Long = 0L` on `Alarm`.

- `0` = not skipping.
- `>0` = skip the occurrence that falls on that epoch day (same UTC-midnight basis as the
  pause window).

**Setting skip:** compute the true next occurrence with `nextTriggerTime(...)` (ignoring
any existing skip), store its epoch day in `skipNextEpochDay`, then re-schedule. Because
`nextTriggerTime` now jumps over that day, the alarm lands on the *following* occurrence.

**No clearing needed (by design).** All checks use exact epoch-day equality
(`candidate == skipNextEpochDay`, `today == skipNextEpochDay`). A stored skip day only
ever matches once; after it passes it can never equal a future candidate again, so a
consumed value is permanently inert. The UI treats skip as "active" only while
`skipNextEpochDay >= todayEpochDay`, so a stale value shows nothing and simply gets
overwritten the next time the user taps skip. This keeps the `BroadcastReceiver` path free
of any DB writes.

**One-shot alarms** (no repeat days) do not get the toggle — they already fire only once.

## Files to change

1. **`data/Models.kt`** — add `val skipNextEpochDay: Long = 0L` to `Alarm`. Add helpers:
   - `fun isSkippingActive(): Boolean = skipNextEpochDay >= todayEpochDay()` (well, `> 0 &&
     skipNextEpochDay >= todayEpochDay()`).
   - `fun isSkippedOnEpochDay(day: Long): Boolean = skipNextEpochDay > 0L && day == skipNextEpochDay`.
   - `fun isSkippedToday(): Boolean = isSkippedOnEpochDay(todayEpochDay())`.

2. **`data/AppDatabase.kt`** — bump `version = 5` → `6`; add `MIGRATION_5_6` that runs
   `ALTER TABLE alarms ADD COLUMN skipNextEpochDay INTEGER NOT NULL DEFAULT 0`; register it
   in `addMigrations(...)`.

3. **`data/ScheduleTiming.kt`** — add `skipEpochDay: Long = 0L` param to `nextTriggerTime`.
   In the candidate loop (and the one-shot branch), skip a date when its epoch day equals
   `skipEpochDay`, reusing the existing `Alarm.localCalendarToEpochDay(cal)` conversion
   (treat it just like the `isPaused` check).

4. **`ui/AlarmScheduler.kt`** — pass `alarm.skipNextEpochDay` into `nextTriggerTime`; add
   `putExtra("SKIP_EPOCH_DAY", alarm.skipNextEpochDay)` to the alarm Intent so re-arm keeps
   skipping.

5. **`ui/Receivers.kt`**:
   - `AlarmReceiver.onReceive`: read `SKIP_EPOCH_DAY`; add a safety guard that suppresses
     the ring when `today == skipEpochDay` (mirrors the pause guard).
   - `rescheduleNextOccurrence`: carry `skipNextEpochDay` into the rebuilt `Alarm` so the
     re-armed occurrence still honors the skip.
   - (Snooze re-arm path stays unchanged — a snoozed alarm should still ring.)

6. **`ui/ClockViewModel.kt`**:
   - `checkInAppTriggers`: add `&& !alarm.isSkippedToday()` to the alarm fire condition.
   - New `fun setSkipNext(alarm: Alarm, skip: Boolean)`: computes the next-occurrence epoch
     day via `nextTriggerTime(hour, minute, repeatDays, pauseStart, pauseEnd)` (skip param
     left 0), stores it (or 0 to clear), persists via `repository.updateAlarm`, and
     re-schedules (or cancels) with the scheduler — same shape as `toggleAlarm`.

7. **`AlarmsScreen.kt`** — in `AlarmCard`, for an **enabled, repeating** alarm show a small
   tappable "Skip next" chip near the pause badge:
   - Inactive → outline chip labelled "Skip next"; tap calls `onSkipNext(true)`.
   - Active (`alarm.isSkippingActive()`) → filled badge "Skipping <formatted date>"; tap
     calls `onSkipNext(false)` to un-skip.
   Wire a new `onSkipNext: (Boolean) -> Unit` param from `AlarmsScreen` →
   `viewModel.setSkipNext(alarm, it)`. Reuse the existing `formatPauseRange`-style
   `SimpleDateFormat("MMM d", UTC)` helper to render the skipped date.

8. **`data/BackupManager.kt`** — add `skipNextEpochDay` to `alarmToJson` and
   `alarmFromJson` (`o.optLong("skipNextEpochDay", 0L)`) so backups round-trip losslessly.

## Testing / verification plan

- Build the app (`./gradlew assembleDebug`).
- Repeating weekday alarm: tap "Skip next" → badge shows the correct upcoming date; the
  "rings in…" behavior (via `nextTriggerTime`) lands on the following selected day.
- Tap again → skip clears, next fire returns to the immediate next day.
- Confirm a one-shot alarm shows no skip chip.
- Confirm DB upgrade from v5 keeps existing alarms (additive migration, default 0).
- Confirm backup export/import preserves the field.

## Notes / decisions

- No DB writes added to any `BroadcastReceiver`; consumed skip values are inert by design
  (documented above).
- Skip is intentionally **not** carried onto snoozed re-rings.
- Interaction with pause window: independent — `nextTriggerTime` skips a date if it is
  paused **or** equals the skip day.
