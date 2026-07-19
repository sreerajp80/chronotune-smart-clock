# Holiday / work-day awareness for alarms

Implements `plans/20260719_143046_holiday-workday-awareness.md`.

## What this adds

Each alarm now has a **Holidays** setting with three choices:

- **All days** (default) — ignores the holiday list, exactly how the app behaved before.
- **Skip holidays** — stays quiet on any day marked as a holiday.
- **Work days only** — stays quiet on holidays, and *also* rings on a day marked as a
  working day even when that weekday is not one of the alarm's selected days. This is for
  compensatory working Saturdays.

The days themselves live in one shared list, managed under
**Settings › Holidays & work days**. A day is marked either `HOLIDAY` or `WORKING_DAY`,
with an optional name.

## Files changed

**New**

- `data/SpecialDay.kt` — the `special_days` Room entity plus `SpecialDayRegistry`, the
  in-memory map the scheduling path reads.
- `HolidaysScreen.kt` — the manage-days UI (date picker, name, holiday/working-day choice,
  delete). Past days are dimmed rather than hidden.

**Changed**

- `data/Models.kt` — `Alarm.holidayMode` column and the three mode constants.
- `data/Daos.kt` — `SpecialDayDao`.
- `data/AppDatabase.kt` — registered the entity and DAO, version 6 → 7, `MIGRATION_6_7`.
- `data/ScheduleTiming.kt` — `nextTriggerTime()` takes `holidayMode` and a
  `dayKind: (Long) -> String?` lambda. Holidays join the existing `isSkipped()` check; the
  new `isSelectedDay()` helper is what lets a marked working day override the weekday list.
- `data/repository/ClockRepository.kt` — special-day reads/writes. The DAO is an optional
  constructor argument so the ring-only services can still build a repository without it.
- `ui/AlarmScheduler.kt` — passes the mode and `SpecialDayRegistry::kindOf` into
  `nextTriggerTime`, and carries `HOLIDAY_MODE` in the alarm intent.
- `ui/Receivers.kt` — reads the mode on re-arm; `BootReceiver` loads the registry before
  rescheduling; a suppression guard stops a stale pending alarm ringing on a holiday.
- `ui/ClockViewModel.kt` — the `specialDays` flow, add/delete actions, registry sync, and
  start-up pruning of past days.
- `AlarmsScreen.kt` — the Holidays selector in the alarm editor, a badge on the alarm row,
  and a holiday-aware "rings in X" toast.
- `SettingsScreen.kt` — new `HOLIDAYS` settings section.
- `MainActivity.kt` — passes the new DAO into the repository.
- `data/BackupManager.kt` — `specialDays` array in the backup file; `holidayMode` on each
  alarm. Old backup files still load, defaulting to `ALL_DAYS`.

**Tests** — `app/src/test/.../ScheduleTimingTest.kt` (new): 7 holiday tests covering each
mode, runs of consecutive holidays, one-shot alarms, and the rule that `SKIP_HOLIDAYS` must
*not* honour working-day marks.

## Two things worth knowing

**Marking a day re-arms the alarms.** An alarm already handed to `AlarmManager` keeps its
old trigger time, so marking next Monday as a holiday would not stop the alarm already
pending for that Monday. Every edit to the day list therefore reloads the registry and
re-arms every enabled holiday-aware alarm. Alarms on `ALL_DAYS` are left alone, so a single
edit does not churn every pending intent on the device.

**Cold-process firing.** Scheduling happens inside broadcast receivers, where a blocking
database read is not allowed, and the re-arm path rebuilds the alarm from intent extras —
a whole day list cannot travel in an Intent. So `AlarmReceiver` detects a holiday-aware
alarm with an unloaded registry and finishes the work inside `goAsync()` after loading it.
If loading fails the map stays empty, which means "no holidays known": the alarm rings as
it always did. It can ring on a holiday in that case, but it is never silently lost.

## Not included

Importing holidays from the device calendar was deliberately left out (it needs
`READ_CALENDAR` and its own UI). The `source` column on `special_days` is the hook for it —
a future import can replace its own rows without touching hand-entered days.

## Verification

`:app:testDebugUnitTest` and `:app:assembleDebug` both pass. Not yet exercised on a device.
