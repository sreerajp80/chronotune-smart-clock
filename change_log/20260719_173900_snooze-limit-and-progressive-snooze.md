# Snooze limit + progressive snooze

Implements `plans/20260719_143530_snooze-limit-and-progressive-snooze.md`.

## What this adds

Two new alarm settings, each with a **global default** in Settings › Alarm and a
**per-alarm override** in the alarm editor:

- **Snooze limit** — Unlimited (default) / 1 / 2 / 3 / 5. Once used up, the Snooze control
  disappears and only Dismiss is left.
- **Snooze style** — *Same each time* (default) or *Getting shorter*. Getting shorter
  divides the alarm's snooze length by the snooze number, so a 10 minute base gives
  10, 5, 3, 3, 2 … and never drops below 1 minute.

Global defaults seed newly created alarms only. Editing a default never rewrites existing
alarms — the same rule the default snooze length and auto-silence already follow.

## Files changed

- `AppPrefs.kt` — `defaultMaxSnoozeCount` and `defaultSnoozeMode`, each with a flow, a
  setter and a cold-read getter.
- `data/Models.kt` — `Alarm.maxSnoozeCount` and `Alarm.snoozeMode` columns plus constants.
- `data/AppDatabase.kt` — version 7 → 8, `MIGRATION_7_8`. Defaults (`0`, `'FIXED'`) leave
  every existing alarm snoozing exactly as before.
- `data/ScheduleTiming.kt` — `snoozeGapMinutes(base, mode, count)` and
  `canSnoozeAgain(max, count)`, both pure functions.
- `ui/Receivers.kt` — `ActiveAlarm` carries the limit, style and current count, with
  `canSnooze()`, `nextSnoozeGapMinutes()` and `snoozesRemaining()` helpers.
  `scheduleSnooze()` applies the gap, increments the count and refuses once the limit is
  reached, returning `false`.
- `ui/AlarmScheduler.kt` — `scheduleAlarm()` takes an optional `snoozeCount` and carries
  the three values in the intent.
- `ui/AlarmService.kt` — passes them to the ringing UI and drops the Snooze notification
  action when none are left; the action label shows the gap and the count remaining.
- `AlarmRingingOverlay.kt` — hides the swipe-up Snooze at the limit (falling back to a
  full-width Dismiss with a short explanation), and shows "· N LEFT" while snoozes remain.
- `AlarmsScreen.kt` — "Snooze limit" and "Snooze style" rows in the editor. The style row
  previews the real sequence ("Each snooze is shorter: 10, 5, 3, 3 ... min") rather than
  describing it.
- `SettingsScreen.kt` — `DefaultMaxSnoozeCard` and `DefaultSnoozeModeCard`.
- `data/BackupManager.kt` — both columns in the backup file, defaulting for older files.

**Tests** — 5 new tests in `ScheduleTimingTest.kt`: fixed vs progressive gaps, the 1 minute
floor, unlimited meaning unlimited, and a limit of 3 allowing exactly three snoozes and
refusing the fourth.

## The design decision worth recording

**The snooze count travels in the intent, not the database.** Snooze re-arms a temporary
alarm at `id + 50000` and never writes to Room. A stored counter would have to be reset on
every normal firing, every reboot and every alarm edit — three easy places to get it wrong,
and each mistake either strands an alarm with no snoozes or quietly grants unlimited ones.
Carrying the count through the intent chain means it resets to zero by itself whenever the
alarm next fires from its own schedule, which is exactly the wanted behaviour.

The limit is enforced inside `scheduleSnooze()`, not only in the UI, so a stale notification
action cannot buy an extra snooze. The UI changes only stop a dead button being shown.

## Verification

`:app:testDebugUnitTest` and `:app:assembleDebug` both pass. Not yet exercised on a device.
