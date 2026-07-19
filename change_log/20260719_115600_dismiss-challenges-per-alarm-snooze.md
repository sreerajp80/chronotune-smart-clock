# Change Log — Dismiss Challenges + Per-Alarm Custom Snooze

Implements plan `plans/20260719_114831_dismiss-challenges-per-alarm-snooze.md`.

## What changed

### 1. Dismiss challenges (wake-up tasks before an alarm can be turned off)
Each alarm can now require a challenge before Dismiss works:
- **None** (default) — plain tap, unchanged.
- **Math** — solve arithmetic (e.g. `7 × 8 = ?`) with an on-screen keypad.
- **Phrase** — retype a random word (Easy) or sentence (Medium/Hard) exactly.
- **Memory** — tap shuffled number tiles in ascending order.

Difficulty (Easy/Medium/Hard) scales each type; "Rounds to solve" sets how many
in a row. Snooze (swipe up) is unchanged and needs no challenge. The challenge
also carries through **snooze** and through **repeat re-arming**, so a snoozed or
repeating alarm still requires it.

### 2. Per-alarm custom snooze
The alarm editor now has a Snooze length section: quick presets (5/10/15/20/30)
plus a custom stepper (1–60 min). The chosen value is stored per alarm and used
when that alarm is snoozed. The global Settings default is unchanged and is only
the starting value for a newly created alarm.

## Files changed

- `data/Models.kt` — added `dismissChallenge`, `challengeDifficulty`,
  `challengeCount` fields to the `Alarm` entity.
- `data/AppDatabase.kt` — DB version 3 → 4, new `MIGRATION_3_4` adds the three
  columns (existing alarms default to challenge = None).
- `ui/AlarmScheduler.kt` — puts the challenge fields as intent extras.
- `ui/Receivers.kt` — `ActiveAlarm` gains the three fields; `AlarmReceiver`
  reads the extras and carries them into repeat re-arming; `scheduleSnooze` /
  `snooze` / `AlarmSnoozeReceiver` carry them through snooze.
- `ui/AlarmService.kt` — new `EXTRA_*` constants, `startIntent` puts them, the
  service reads them back, and the notification snooze action carries them.
- `AlarmRingingOverlay.kt` — DISMISS now opens the challenge panel when a
  challenge is set; only completing it calls the real dismiss.
- `DismissChallenge.kt` — **new file**: the `DismissChallengePanel` plus the
  Math (keypad), Phrase (text), and Memory (number tiles) challenge composables,
  and the `DismissChallengeType` constants.
- `AlarmsScreen.kt` — editor UI for snooze length + dismiss challenge; extended
  the `onSave` callback and both call sites; added `ChoiceChip` / `StepperControl`
  helper composables.
- `ui/ClockViewModel.kt` — `addAlarm` accepts snooze + challenge params.

## Verification

- `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL, no warnings.

## Known limitation

The alarm notification's own "Dismiss" action (visible only if the full-screen
alarm is swiped away to reach the notification shade) still stops the alarm
without a challenge. The full-screen ringing overlay — the normal path — enforces
the challenge. Closing this notification-shade bypass can be a follow-up.
