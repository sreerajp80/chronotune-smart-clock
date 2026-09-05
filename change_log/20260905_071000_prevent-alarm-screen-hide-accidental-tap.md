# Prevent full-screen alarm from hiding on accidental taps

Implements plan [plans/20260905_065500_prevent-alarm-screen-hide-accidental-tap.md](../plans/20260905_065500_prevent-alarm-screen-hide-accidental-tap.md).

## What was wrong

When an alarm rang in the full-screen window (`AlarmActivity`), tapping slightly outside the **DISMISS** button caused the screen to disappear immediately. The alarm audio kept playing in the background, forcing the user to unlock the phone, launch the app, and find the Alarms screen to stop it.

## Cause

1. `AlarmActivity` drew edge-to-edge behind the system navigation bar (Back, Home, Recents buttons or gesture handle) without navigation bar insets padding for the Dismiss button.
2. The Dismiss button sat right against or over the system navigation buttons at the bottom of the screen, so accidental taps landed on the system Back or Home buttons.
3. `AlarmActivity` did not intercept back navigation, so touching Back immediately finished the activity.
4. The system navigation and status bars were visible rather than hidden in immersive sticky mode.
5. The window did not explicitly disable `setFinishOnTouchOutside(false)`, and `FLAG_KEEP_SCREEN_ON` was not set unconditionally on newer Android versions.
6. Empty background space in the overlay was not consuming tap gestures.

## What changed

### `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmActivity.kt`

- Configured immersive mode using `WindowInsetsControllerCompat` with `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` to hide system navigation and status bars. This stops accidental touches from hitting system buttons.
- Re-applied system bar hiding in `onResume()` so the screen stays immersive if system bars were briefly revealed.
- Added a back-press callback via `onBackPressedDispatcher.addCallback` to ignore back buttons and back gestures while the alarm is ringing.
- Set `setFinishOnTouchOutside(false)` on the window to prevent touches outside window bounds from finishing the screen.
- Added `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` across all Android versions to keep the screen on while ringing.

### `app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmRingingOverlay.kt`

- Added `BackHandler(enabled = true)` so back gestures/buttons are safely ignored (or return from a dismiss challenge to the ringing screen).
- Added pointer tap consumption (`pointerInput` with `detectTapGestures { }`) on the root container so background taps outside buttons do not fall through.
- Added `.systemBarsPadding()` to maintain safe spacing from screen edges and navigation zones.

### `app/src/main/java/in/sreerajp/chronotune_smart_clock/DismissChallenge.kt`

- Added `.systemBarsPadding()` and pointer tap consumption on the challenge panel.

## Verification

- Ran `./gradlew testDebugUnitTest` — build and all tests passed with 0 errors.
- Verified back-press interception, immersive bar hiding, and tap absorption.
