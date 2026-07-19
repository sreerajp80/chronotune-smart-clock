# Change log: side-by-side ring buttons, tap Dismiss, swipe-up Snooze

Implements plan `plans/20260719_112834_ring-buttons-side-swipe-snooze.md`.

## What changed

File: `app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmRingingOverlay.kt`

- The bottom action area for real alarms (`type == "ALARM"`) is now a
  **Row** with two controls side by side instead of the old stacked
  layout:
  - **Left: Dismiss** — a red `Button3D` that stops the alarm on a
    single tap (unchanged behaviour, same test tag
    `dismiss_ring_overlay_button`).
  - **Right: Snooze** — a new `SwipeUpSnoozeButton` that only fires
    `onSnooze` on a deliberate **upward swipe** past a ~90.dp threshold.
    A plain tap does nothing, so the user can no longer snooze by
    accident. It shows an up-arrow icon, the "SNOOZE (n MIN)" label and
    a small "Swipe up" hint, and it follows the finger while dragging,
    snapping back if released before the threshold. Same test tag
    `snooze_ring_overlay_button`.
- For music playback (`type != "ALARM"`) the screen keeps a single
  full-width **Dismiss** (tap) button, as before.
- Added a private composable `SwipeUpSnoozeButton` implementing the
  swipe gesture using `detectVerticalDragGestures` plus an `Animatable`
  offset for the follow/snap-back feedback.
- Added imports: `detectVerticalDragGestures`, `clip`, `pointerInput`,
  `LocalDensity`, `IntOffset`, `kotlinx.coroutines.launch`,
  `kotlin.math.roundToInt`.

## Verification

- `./gradlew :app:compileDebugKotlin` — BUILD OK.

## Notes

- Swipe threshold (~90.dp) can be tuned after on-device testing.
