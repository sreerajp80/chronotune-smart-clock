# Increase gap between DISMISS and SNOOZE buttons

**Status:** completed

## Issue
On the alarm ringing overlay, the DISMISS and SNOOZE buttons sit too close
together. This makes it easy to hit the wrong one when half-awake. We want a
larger gap between them.

## Files to change
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmRingingOverlay.kt`

## Plan for the fix
- At line 139, the `Spacer` between the DISMISS button and the SNOOZE button
  currently has a height of `16.dp`.
- Increase it to `32.dp` to add more visual separation between the two buttons.

No other layout or logic changes.
