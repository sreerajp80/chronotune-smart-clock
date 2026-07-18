# Increase gap between DISMISS and SNOOZE buttons

Implements plan `plans/20260718_100000_dismiss-snooze-gap.md`.

## What changed
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmRingingOverlay.kt`
  - Increased the height of the `Spacer` between the DISMISS button and the
    SNOOZE button from `16.dp` to `32.dp`, giving more visual separation so the
    two buttons are harder to mix up.

No logic or behavior changes.
