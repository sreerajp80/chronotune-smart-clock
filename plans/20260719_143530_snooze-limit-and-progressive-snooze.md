# Snooze limit + progressive snooze (global default and per alarm)

**Status:** completed

## The issue

Snooze today is unlimited and always the same length. `snoozeMinutes` is per alarm, but
a user can hit Snooze forever, and every snooze is the same gap. Two things are missing:

1. **Max snooze count** — after N snoozes the alarm must stop offering Snooze, so the
   user has to actually get up.
2. **Progressive snooze** — each snooze is shorter than the last (10 → 7 → 5 → 3 …),
   which nudges the user awake instead of letting them drift.

Both must have a **global default in Settings** (used when a new alarm is created) and a
**per-alarm override** in the alarm editor. This matches the pattern already used by
auto-silence: `AppPrefs.defaultAutoSilenceMinutes` seeds `Alarm.autoSilenceMinutes`.

## How snooze works now

- The ringing UI ([AlarmRingingOverlay.kt](../app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmRingingOverlay.kt))
  and the notification action both lead to `ActiveAlarmState.scheduleSnooze()` in
  [Receivers.kt](../app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/Receivers.kt).
- That builds a **temporary one-shot `Alarm` with `id + 50000`** and schedules it
  `snoozeMinutes` from now, copying tone, volume, challenge and auto-silence forward
  through intent extras.
- Nothing is written back to the database, so there is currently nowhere that a
  snooze count lives.

That last point drives the design below.

## Design

### Count rides in the intent, not the database

Each snooze re-ring is a fresh intent built from the previous one. So the count is
carried as one more extra:

- New extra `SNOOZE_COUNT`, absent (0) on the first real firing.
- `scheduleSnooze()` puts `previousCount + 1` on the re-ring intent.
- The ringing screen and notification read it to decide whether Snooze is still allowed.

This keeps the whole feature off the DB write path (important — snooze happens from a
broadcast receiver, and a stale DB counter would also need resetting on every normal
firing, boot, and alarm edit). The count naturally resets to 0 whenever the alarm next
fires from its own schedule, which is exactly the wanted behaviour.

### Two new per-alarm columns

```kotlin
val maxSnoozeCount: Int = 0,        // 0 = unlimited (today's behaviour)
val snoozeMode: String = "FIXED"    // FIXED | PROGRESSIVE
```

`PROGRESSIVE` computes the gap for snooze number `n` (1-based) from the alarm's own
`snoozeMinutes` as the starting point, halving down with a floor of 1 minute:

```
gap(n) = max(1, round(snoozeMinutes / n))    // 10, 5, 3, 3, 2, 2, 1 ...
```

A pure helper `snoozeGapMinutes(base, mode, count)` in `ScheduleTiming.kt`, so it is
unit-testable and the same rule is used by the overlay label, the notification, and the
scheduling call. `FIXED` returns `base` unchanged.

### Two new global defaults

In `AppPrefs`, following the existing `defaultAutoSilenceMinutes` shape exactly
(a `MutableStateFlow`, a `set…` writer, and a `get…(context)` synchronous reader):

- `defaultMaxSnoozeCount` (default 0 = unlimited)
- `defaultSnoozeMode` (default `FIXED`)

These seed a **newly created** alarm only. Editing the global default never rewrites
existing alarms — same rule as auto-silence, so the per-alarm value always wins.

### What happens when the limit is reached

When `maxSnoozeCount > 0` and `SNOOZE_COUNT >= maxSnoozeCount`:

- The ringing overlay hides the swipe-up Snooze control and shows a full-width Dismiss,
  with a line of text saying no snoozes are left.
- The notification does not add the Snooze action.
- `scheduleSnooze()` also checks the limit itself and refuses, so a stale notification
  or a race cannot get past the UI check. This is the real enforcement point; the UI
  changes are just so the user is not shown a dead button.

The alarm still respects `autoSilenceMinutes` — the last ring is not forced to play
forever.

### Showing the user where they are

While snoozes remain, the overlay's snooze button already prints the gap. It will now
read e.g. `SNOOZE (5 MIN) · 2 OF 3 LEFT` when a limit is set, so the count is visible
before the last one is used.

## Files to change

- `AppPrefs.kt` — `defaultMaxSnoozeCount` and `defaultSnoozeMode` (flow + setter +
  synchronous getter + load in the init read).
- `data/Models.kt` — `maxSnoozeCount` and `snoozeMode` columns on `Alarm`.
- `data/AppDatabase.kt` — bump version, add the migration adding both columns with
  defaults `0` / `'FIXED'` so existing alarms keep unlimited fixed snooze.
- `data/ScheduleTiming.kt` — `snoozeGapMinutes(base, mode, count)` helper.
- `ui/AlarmScheduler.kt` — carry `MAX_SNOOZE_COUNT` and `SNOOZE_MODE` in the alarm
  intent extras.
- `ui/Receivers.kt` — read `SNOOZE_COUNT`; `scheduleSnooze()` increments it, applies
  `snoozeGapMinutes`, and refuses when the limit is reached; carry the new extras
  through the snooze re-ring and the re-arm path.
- `ui/AlarmService.kt` — pass the new extras to the ringing UI; omit the Snooze
  notification action when no snoozes remain.
- `ui/AlarmActivity.kt` — pass the count and limit into the overlay.
- `AlarmRingingOverlay.kt` — hide/disable Snooze at the limit, show remaining count and
  the progressive gap.
- `AlarmsScreen.kt` — per-alarm editor rows: "Max snoozes" (Unlimited / 1 / 2 / 3 / 5)
  and "Snooze length" (Fixed / Getting shorter); seed both from `AppPrefs` on create.
- `SettingsScreen.kt` — the two global defaults, next to the existing auto-silence
  default.
- `res/values/strings.xml` — new labels.
- `data/BackupManager.kt` — include the two new preferences and columns.
- `app/src/test/.../ExampleRobolectricTest.kt` — tests for `snoozeGapMinutes` (fixed vs
  progressive, floor at 1) and for the limit check refusing the (N+1)th snooze.

## Open choice for the user

The progressive formula above is `snoozeMinutes / n` (10 → 5 → 3 → 3 → 2). An
alternative is a fixed subtraction (10 → 7 → 4 → 1) or a simple halving (10 → 5 → 2).
I picked division because it degrades gently and never hits 0. Say the word if you want
a different curve.

## Order of work

1. Columns + migration + `AppPrefs` defaults.
2. `snoozeGapMinutes` helper + unit tests.
3. Extras plumbing and the enforcement inside `scheduleSnooze()`.
4. Overlay + notification UI.
5. Settings and alarm-editor UI, backup.

## Relation to the other plan

Independent of `20260719_143046_holiday-workday-awareness.md`. Both touch
`Models.kt`, `AppDatabase.kt` (migration number), `AlarmsScreen.kt` and
`BackupManager.kt`, so whichever is implemented second must take the next DB version
number rather than reusing it.
