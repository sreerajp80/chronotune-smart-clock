# Prevent full-screen alarm from hiding on accidental taps

**Status:** completed

## The issue

When an alarm rings in the full-screen window (`AlarmActivity`), the user can accidentally tap slightly outside the **DISMISS** button. When this happens, the screen disappears immediately, leaving the alarm ringing in the background. The user then has to unlock the phone, find the app, open it, and go to the Alarms tab to stop the sound.

The screen should only close when the user deliberately taps **DISMISS** or completes **SNOOZE**.

## Root cause

Several factors combine to cause the alarm screen to hide on accidental taps:

1. **System navigation bar overlap / proximity:**
   `AlarmActivity` uses `enableEdgeToEdge()`, drawing content all the way to the screen edges. In `AlarmRingingOverlay.kt`, the Dismiss and Snooze buttons have only 24 dp bottom padding and no window insets padding (`systemBarsPadding` or `navigationBarsPadding`). On devices with 3-button navigation (Back, Home, Recents) or gesture navigation handles, the Dismiss button sits directly against or overlapping the navigation bar. A tap landing slightly low or off-center hits the system Back, Home, or Recents control instead of Dismiss.

2. **Unintercepted Back navigation:**
   `AlarmActivity` does not register a back press handler (`onBackPressedDispatcher`). When the user taps the Back button or triggers an edge back gesture, `AlarmActivity` finishes itself immediately. The foreground `AlarmService` continues playing audio, but the visual screen is gone.

3. **Visible system bars without immersive sticky mode:**
   Standard Android clock apps hide the navigation and status bars in immersive sticky mode (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`). Without this, the system navigation buttons remain active and easily tapped by mistake when reaching for Dismiss.

4. **Missing touch and screen-on safety flags:**
   - `FLAG_KEEP_SCREEN_ON` was only set on Android versions older than 8.1 (`O_MR1`). On Android 8.1 and higher, the screen could dim or turn off while ringing.
   - `setFinishOnTouchOutside(false)` was not explicitly configured on the Activity window.
   - Background taps on the ringing overlay were not consumed, allowing touches to pass through.

## Files to change

- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmActivity.kt`
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmRingingOverlay.kt`

## The fix

1. **In `AlarmActivity.kt`:**
   - Add a back-press callback via `onBackPressedDispatcher.addCallback` that ignores back events while the alarm is ringing. Back presses will no longer close the screen.
   - Configure immersive mode via `WindowInsetsControllerCompat` with `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` to hide system navigation and status bars. Accidental taps near the bottom cannot trigger Home or Back.
   - Re-hide system bars in `onResume()` to ensure the screen stays immersive if transient bars were revealed.
   - Call `setFinishOnTouchOutside(false)` on the window.
   - Add `FLAG_KEEP_SCREEN_ON` unconditionally so the screen stays awake while the alarm rings on all Android versions.

2. **In `AlarmRingingOverlay.kt`:**
   - Add `Modifier.navigationBarsPadding()` / `Modifier.systemBarsPadding()` to give the Dismiss and Snooze buttons safe spacing from the bottom edge so they never sit on top of navigation zones.
   - Add a tap consumer on the background container so taps landing anywhere on the screen background are consumed safely and do not trigger unexpected actions or fall through.
   - Include `BackHandler(enabled = true) { /* no-op */ }` so back navigation is blocked both in `AlarmActivity` and in the fallback overlay rendered within the main app.

## Testing

1. Run unit tests (`./gradlew testDebugUnitTest`) to verify all existing tests continue passing.
2. Trigger an alarm to display the full-screen ringing UI:
   - Tap outside the Dismiss button on empty space: verify the screen stays visible and does not hide.
   - Tap near the bottom edge / navigation bar area: verify the screen does not close.
   - Press or swipe Back: verify the screen stays visible.
   - Tap Dismiss: verify the alarm stops and the screen closes.
   - Swipe Snooze: verify the alarm snoozes and the screen closes.
