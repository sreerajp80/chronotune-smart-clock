# Ringing screen: side-by-side buttons, tap Dismiss, swipe-up Snooze

**Status:** completed

## The issue

On the alarm ringing screen the two actions are stacked vertically:
a big **DISMISS** button on top and a **SNOOZE** button just below it.
Because they sit close together and both work with a single tap, it is
easy to hit the wrong one. The user reports that when they mean to act,
it can end up snoozing. We want a clearer, harder-to-mistake layout.

## Desired behaviour

- Put the two buttons **side by side**: **Dismiss on the left**,
  **Snooze on the right** (instead of stacked top/bottom).
- **Dismiss = single tap** (easy, as now).
- **Snooze = swipe up** on the Snooze button. A plain tap on Snooze
  must NOT snooze. Only a deliberate upward swipe past a threshold
  triggers the snooze. This stops accidental snoozing.
- Show a small hint on the Snooze side (an up-arrow + "Swipe up" text)
  so the user knows the gesture.
- This two-button row only applies to real alarms (`type == "ALARM"`).
  For music playback (`type != "ALARM"`) there is no snooze, so keep
  a single full-width **Dismiss** (tap) button as today.

## Files to change

1. `app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmRingingOverlay.kt`
   - Replace the bottom action `Column` (stacked Dismiss + Snooze) with:
     - For `type == "ALARM"`: a `Row` holding
       - left: **Dismiss** `Button3D` (tap → `onDismiss`), and
       - right: a **Snooze** control that only fires `onSnooze` on an
         upward swipe.
     - For other types: the existing single full-width Dismiss button.
   - Add a new private composable `SwipeUpSnoozeButton` in the same file
     that:
     - looks like the current outlined snooze button (rounded, white
       border, "SNOOZE (n MIN)" label) plus an up-arrow icon and a
       small "Swipe up" hint,
     - uses `pointerInput` + `detectVerticalDragGestures` to accumulate
       vertical drag; when the total upward drag passes a threshold
       (about 90.dp worth of pixels) it calls `onSnooze` once,
     - gives light visual feedback by moving the button up with the
       finger (via an animated vertical offset) and snapping back if the
       swipe is released before the threshold,
     - does nothing on a simple tap.

No other files need changes. `AlarmActivity.kt` already passes
`onDismiss` / `onSnooze`; their meaning is unchanged.

## Notes / trade-offs

- Threshold is chosen so a real swipe is needed but it is not tiring.
  We can tune the distance after testing.
- Keeping the Dismiss button as a plain tap matches the user's request;
  the swipe guard is only on Snooze.
- Layout uses a `Row` with the two controls sharing width (`weight(1f)`
  each) with a gap between them, replacing the old vertical spacer.

## Test plan

- Build the app.
- Trigger an alarm; confirm the ringing screen shows Dismiss (left) and
  Snooze (right) side by side.
- Tap Dismiss → alarm stops (no snooze).
- Tap Snooze (no swipe) → nothing happens.
- Swipe up on Snooze → alarm snoozes for the configured minutes.
- Trigger music playback; confirm only a single Dismiss button shows and
  a tap dismisses it.
