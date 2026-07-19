# Dismiss Challenges + Per-Alarm Custom Snooze

**Status:** completed

## What we are adding

Two related alarm features:

1. **Dismiss challenges** — before an alarm can be dismissed, the user must
   complete a task that proves they are awake. Four choices, chosen per alarm:
   - **None** (default) — plain tap Dismiss, exactly like today.
   - **Math** — solve simple problems (e.g. `7 × 8 = ?`). Has a difficulty
     (Easy / Medium / Hard) and a count (how many to solve in a row).
   - **Type a phrase** — retype a random word or sentence exactly.
   - **Memory** — tap shuffled number tiles in the correct (ascending) order.

   Difficulty and count apply to whichever challenge is chosen:
   - Math: difficulty sets number size / operations; count sets how many.
   - Phrase: difficulty picks a short word vs a longer sentence.
   - Memory: difficulty sets how many number tiles; count sets rounds.

2. **Per-alarm custom snooze** — today the snooze length comes from a global
   default in Settings (fixed choices: 5/10/15/20/30). Each alarm already stores
   its own `snoozeMinutes` in the database, but there is no UI to change it. We
   add a snooze picker inside the alarm editor with the quick choices **plus a
   custom value** the user can type (1–60 minutes). That value is used when this
   alarm is snoozed. The Settings default stays as-is and is used only as the
   starting value for a newly created alarm.

## The issue / why

- The ringing overlay only offers tap-to-Dismiss, so a half-asleep user can turn
  the alarm off without waking up. Challenges fix this.
- Snooze length is global-only. A user who wants a 3-minute snooze on one alarm
  and 15 on another cannot do it, even though the data field already exists.

## Data model

Add three fields to the `Alarm` entity (all with safe defaults so existing
alarms behave exactly as before):

- `dismissChallenge: String = "NONE"` — `NONE | MATH | PHRASE | MEMORY`
- `challengeDifficulty: String = "EASY"` — `EASY | MEDIUM | HARD`
- `challengeCount: Int = 1` — how many to solve in a row (1–10)

`snoozeMinutes` already exists on `Alarm`; no new column for snooze.

### Room migration

Bump DB `version` 3 → 4 and add `MIGRATION_3_4`:
```
ALTER TABLE alarms ADD COLUMN dismissChallenge TEXT NOT NULL DEFAULT 'NONE'
ALTER TABLE alarms ADD COLUMN challengeDifficulty TEXT NOT NULL DEFAULT 'EASY'
ALTER TABLE alarms ADD COLUMN challengeCount INTEGER NOT NULL DEFAULT 1
```

## How the challenge reaches the ringing screen

The challenge settings must ride along the whole alarm-fire chain, the same way
`snoozeMinutes` already does. New intent extras: `CHALLENGE`,
`CHALLENGE_DIFFICULTY`, `CHALLENGE_COUNT`.

- `AlarmScheduler.scheduleAlarm` → put the three extras.
- `AlarmReceiver.onReceive` → read them, pass into `ActiveAlarm`, and include
  them in `rescheduleNextOccurrence` so repeating alarms keep the challenge.
- `ActiveAlarmState.ActiveAlarm` → add the three fields.
- `AlarmService` → add `EXTRA_*` constants, put them in `startIntent`, read them
  back when building the `ActiveAlarm`.
- The challenge also carries through **snooze**, so a snoozed alarm still needs
  the challenge when it re-rings (otherwise the user could snooze once and then
  dismiss with a tap): thread the fields through `ActiveAlarmState.snooze` /
  `scheduleSnooze`, the snooze action extras in `AlarmService`'s notification,
  and `AlarmSnoozeReceiver`.

The **overlay itself owns the challenge UI**, so both places that show it
(`AlarmActivity` full-screen and the `ClockAppScreen` in-app fallback) get the
behavior with no change to those call sites.

## Ringing overlay behavior

In `AlarmRingingOverlay`:
- If `dismissChallenge == "NONE"` → unchanged (tap Dismiss).
- Otherwise, tapping **DISMISS** opens a challenge panel over the overlay.
  Snooze (swipe up) is unchanged and still works without the challenge.
- The panel runs `challengeCount` rounds. Each correct answer advances; a wrong
  answer shows a brief error and gives a new problem (no progress lost, but not
  advanced). A Cancel/close returns to the ringing screen (alarm keeps ringing).
- Only after all rounds pass do we call the real `onDismiss()`.

Challenge panels (new private composables in the overlay file):
- **Math** — renders `a × b = ?` etc., number keypad / numeric field + Submit.
  Difficulty controls operands and operations (Easy: +/− to 10; Medium: +/−/×
  to 20; Hard: ×/÷ larger).
- **Phrase** — shows a random phrase from a small built-in bank (word for Easy,
  sentence for Medium/Hard), text field, exact match after trim + case-fold.
- **Memory** — shows number tiles `1..N` shuffled; user taps them in ascending
  order. Wrong tap resets the round. `N` scales with difficulty.

## Alarm editor UI (AlarmsScreen)

Add two sections to `AlarmEditDialog`'s main pane:

1. **Snooze length** — quick chips (5/10/15/20/30) + a "Custom" numeric field
   (1–60). Pre-filled from the alarm's `snoozeMinutes` (or the Settings default
   for a new alarm).
2. **Dismiss challenge** — a row to pick None / Math / Phrase / Memory. When not
   None, reveal difficulty chips (Easy/Medium/Hard) and, for Math/Memory, a
   count stepper (1–10).

Thread these through:
- Extend the `onSave` callback signature to include `snoozeMinutes`,
  `dismissChallenge`, `challengeDifficulty`, `challengeCount`.
- `ClockViewModel.addAlarm` — accept the new params (snooze no longer forced to
  the global default when the user picked one).
- The edit path's `current.copy(...)` — set the four fields.

## Files to change

- `app/src/main/java/in/sreerajp/chronotune_smart_clock/data/Models.kt`
  — add 3 challenge fields to `Alarm`.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/data/AppDatabase.kt`
  — version 4 + `MIGRATION_3_4`.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmScheduler.kt`
  — put challenge extras.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/Receivers.kt`
  — `ActiveAlarm` fields; read extras; carry through reschedule + snooze.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmService.kt`
  — extra constants, `startIntent`, read-back, snooze notification extras.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmRingingOverlay.kt`
  — challenge gating + Math/Phrase/Memory panels.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmsScreen.kt`
  — snooze + challenge editor UI; extended `onSave`.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ClockViewModel.kt`
  — `addAlarm` accepts snooze + challenge.
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmActivity.kt`
  and `ClockAppScreen.kt` — pass challenge fields into their `scheduleSnooze`
  calls (so a snoozed alarm keeps its challenge).

## Not in scope

- No new global Settings toggle for challenges (per-alarm only for now).
- No barcode/QR, shake, or steps challenges (can be added later).
- Snooze itself stays swipe-up with no challenge required.

## Testing

- Build the app; confirm Room migration 3→4 runs cleanly (existing alarms keep
  ringing, challenge = None).
- Manually verify: create an alarm with each challenge type + custom snooze,
  let it ring, confirm Dismiss requires the challenge and Snooze uses the custom
  minutes; confirm a repeating alarm and a snoozed alarm still require the
  challenge.
```
