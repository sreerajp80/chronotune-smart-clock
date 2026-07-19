# Change log — Skip-next-occurrence toggle for repeating alarms

Implements plan
[plans/20260719_122500_skip-next-occurrence.md](../plans/20260719_122500_skip-next-occurrence.md).

## What changed

Added a **"Skip next alarm"** control to repeating alarms. It skips just the next firing,
then the alarm carries on as normal — so a user does not have to turn an alarm off and
remember to turn it back on for a one-off day (e.g. a holiday).

Design mirrors the existing pause-window feature: a single stored date (UTC-midnight epoch
day). Because every check compares this date for exact equality, a skip day matches only
once and a consumed value is permanently inert, so no background/broadcast code ever needs
to clear it.

## Files changed

1. **data/Models.kt** — added `skipNextEpochDay: Long = 0L` to `Alarm`, plus helpers
   `isSkippedOnEpochDay`, `isSkippedToday`, and `isSkippingActive`.
2. **data/AppDatabase.kt** — bumped DB version 5 → 6; added additive `MIGRATION_5_6`
   (`ALTER TABLE alarms ADD COLUMN skipNextEpochDay INTEGER NOT NULL DEFAULT 0`) and
   registered it. Existing alarms upgrade with default 0 (no behavior change).
3. **data/ScheduleTiming.kt** — `nextTriggerTime` gained a `skipEpochDay` param; the
   candidate-date scan now skips a date that is paused **or** equals the skip day (both the
   repeating and one-shot branches).
4. **ui/AlarmScheduler.kt** — passes `alarm.skipNextEpochDay` into `nextTriggerTime` and
   carries it in the broadcast Intent (`SKIP_EPOCH_DAY`) so re-arm keeps skipping.
5. **ui/Receivers.kt** — `AlarmReceiver` reads `SKIP_EPOCH_DAY`, adds a safety guard that
   suppresses a stray ring on the skipped day, and carries the skip day into the re-armed
   `Alarm` in `rescheduleNextOccurrence`.
6. **ui/ClockViewModel.kt** — in-app trigger check now also requires `!alarm.isSkippedToday()`;
   added `setSkipNext(alarm, skip)` which computes/stores the next-occurrence epoch day (or
   clears it), persists, and re-schedules.
7. **AlarmsScreen.kt** — `AlarmCard` shows a new `AlarmSkipNextRow` for enabled, repeating
   alarms: "Skip next alarm" when idle, and a highlighted "Skipping <date>" with an "Undo"
   affordance while a skip is pending. Added `formatSkipDay` helper and wired
   `onSkipNext` → `viewModel.setSkipNext`.
8. **data/BackupManager.kt** — `skipNextEpochDay` added to `alarmToJson` / `alarmFromJson`
   so backups round-trip losslessly.

## Verification

- `./gradlew :app:compileDebugKotlin` — **BUILD SUCCESSFUL**.
- Skip control is hidden for one-shot alarms and for disabled alarms.
- Migration is additive; existing alarms keep current behavior.

## Notes

- No DB writes were added to any `BroadcastReceiver`; stale skip values are inert by design.
- Skip is intentionally not carried onto snoozed re-rings.
- Skip and pause are independent: a date is unavailable if it is paused or is the skip day.
