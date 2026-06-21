# Plan: Pause an alarm for a date range

## Feature request

In the configure-alarm screen, add a **"Pause alarm"** option. Tapping it opens a
calendar where the user selects a **start date** and **end date** (a range) by tapping.
While today's date falls inside that range, the alarm does **not** ring; once the range
passes, the alarm resumes automatically. The selected range is shown under the calendar,
and the "Pause alarm" row in the editor shows an indication that a pause is configured.

## Background / why this is non-trivial

An alarm can fire from **two independent paths**, so the pause must be honored in both:

1. **AlarmManager path** — `AlarmScheduler.scheduleAlarm()` schedules the next occurrence;
   when it fires, `AlarmReceiver.onReceive()` starts `AlarmService` and re-arms the next
   occurrence (`rescheduleNextOccurrence`). The re-arm rebuilds the `Alarm` from intent
   extras, so any pause data must be carried through the intent too.
2. **In-app ticker path** — `ClockViewModel.checkInAppTriggers()` runs every 100 ms while
   the app is foregrounded and triggers alarms directly, independent of AlarmManager.

The DB currently uses Room **version 1**. We add two columns, so the version is bumped to 2
with a real migration (preserving existing alarms/world-clocks/music-schedules).

Date storage: Material3 `DateRangePicker` returns selected dates as **UTC-midnight millis**.
We store those raw millis and compare by **epoch day** (`millis / 86_400_000`) so there is no
timezone drift. "Today" is converted to the same epoch-day basis via a UTC calendar built
from the local Y/M/D.

## Files to change

1. **`app/src/main/java/in/sreerajp/chronotune_smart_clock/data/Models.kt`**
   - Add to `Alarm`: `val pauseStartMillis: Long = 0L`, `val pauseEndMillis: Long = 0L`
     (0 = no pause; both must be non-zero for a pause to be active).
   - Add helpers:
     - `isPauseConfigured(): Boolean` — both values > 0.
     - `isPausedOnEpochDay(epochDay: Long): Boolean` — `epochDay in start..end` (where
       `start = pauseStartMillis/86400000`, `end = pauseEndMillis/86400000`).
     - `isPausedNow(): Boolean` — uses today's local epoch day.
     - A small companion/util to compute "today" epoch day from a local `Calendar` via a
       UTC calendar (Y/M/D at UTC midnight). (Could also live in scheduler; keep it in the
       model so both paths reuse it.)

2. **`app/src/main/java/in/sreerajp/chronotune_smart_clock/data/AppDatabase.kt`**
   - Bump `version = 1` → `version = 2`.
   - Add `MIGRATION_1_2` that runs:
     `ALTER TABLE alarms ADD COLUMN pauseStartMillis INTEGER NOT NULL DEFAULT 0` and the
     same for `pauseEndMillis`.
   - `.addMigrations(MIGRATION_1_2)` on the builder (keep the existing destructive fallback
     as a backstop).

3. **`app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmScheduler.kt`**
   - Make `nextTriggerTime(...)` pause-aware: accept `pauseStartMillis`, `pauseEndMillis`.
     - Repeating alarms: extend the forward scan (currently `repeat(7)`) to up to ~366 days
       and return the first selected weekday that is **not** inside the pause window.
     - One-shot alarms: if the computed date is inside the pause window, advance to the day
       after `pauseEnd` at the same hour:minute.
   - `scheduleAlarm()`: pass the alarm's pause fields into `nextTriggerTime`, and add
     `putExtra("PAUSE_START", alarm.pauseStartMillis)` / `putExtra("PAUSE_END", ...)` so the
     receiver's re-arm preserves them.

4. **`app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/Receivers.kt`**
   - `AlarmReceiver.onReceive()`: read `PAUSE_START`/`PAUSE_END`. As a safety guard, if the
     alarm is currently paused, **skip** starting `AlarmService` (don't ring) but still call
     the re-arm. (With pause-aware scheduling this is belt-and-suspenders.)
   - `rescheduleNextOccurrence()`: thread the pause values into the reconstructed `Alarm` so
     the next AlarmManager arming stays pause-aware.
   - (`BootReceiver` already reschedules from DB `Alarm` objects, which now carry pause
     fields — no change needed beyond it compiling.)

5. **`app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ClockViewModel.kt`**
   - `checkInAppTriggers()`: add `&& !alarm.isPausedNow()` to the alarm trigger condition.
   - `addAlarm(...)`: add `pauseStartMillis`, `pauseEndMillis` params and set them on the new
     `Alarm`. (`updateAlarm` already takes a full `Alarm` via `.copy()` from the UI — no
     signature change needed there.)

6. **`app/src/main/java/in/sreerajp/chronotune_smart_clock/MainActivity.kt`**
   - **`AlarmEditDialog`**:
     - New state `pauseStartMillis` / `pauseEndMillis` seeded from `existing`.
     - Add a **"Pause alarm"** row (styled like the existing "Alarm Audio Tone" row) — shows
       the configured range (e.g. `Jun 20 – Jun 27`) or `Not set`, with a calendar icon, and
       opens the range picker on tap. When configured, the row visibly indicates it
       (highlighted text / "Paused" chip).
     - Add a **date-range picker dialog**: a `DatePickerDialog`/`Dialog` hosting Material3
       `DateRangePicker` (`rememberDateRangePickerState`) with **Set** and **Clear** actions.
       The picker shows the selected start/end range in its header (under the calendar).
       `@OptIn(ExperimentalMaterial3Api::class)` is already present on this function.
     - Extend the `onSave` lambda signature with `pauseStartMillis: Long, pauseEndMillis: Long`
       and pass the chosen values.
   - **`AlarmsScreen`** — both `AlarmEditDialog` call sites:
     - Add path: pass the two values into `viewModel.addAlarm(...)`.
     - Edit path: include `pauseStartMillis`/`pauseEndMillis` in `current.copy(...)`.
   - **`AlarmCard`** (optional, recommended indicator on the list): when `isPauseConfigured()`
     and active, show a small "Paused until <date>" badge so the state is visible without
     opening the editor.

## Behavior summary

- No pause set (both 0) → unchanged behavior.
- Pause set and today inside `[start, end]` → alarm is silent on both paths; AlarmManager is
  armed for the first valid occurrence **after** the range.
- After `end` passes → alarm resumes automatically on its normal schedule.

## Testing / verification

- Build the app (`gradlew assembleDebug`).
- Manual: create a repeating alarm, set a pause range covering today → confirm it does not
  ring (ticker path) and the "next alarm" arming lands after the range. Set a future-only
  range → alarm rings normally until the range, then pauses.
- Confirm Room migration 1→2 keeps existing alarms (no data wipe).

## Notes / open questions

- DB migration preserves data; if you'd rather just rely on the existing destructive
  fallback (wipes all alarms/clocks/schedules on upgrade), say so and I'll drop the
  `MIGRATION_1_2` step.
- Pause indicator on `AlarmCard` is included as recommended but can be dropped if you only
  want it inside the editor.
