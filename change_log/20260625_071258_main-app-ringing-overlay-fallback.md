# Change log: main-app ringing overlay fallback

Implements plan `plans/20260625_070723_main-app-ringing-overlay-fallback.md`.

## Problem

Alarm audio (owned by `AlarmService` / `AudioEngine` via `ActiveAlarmState`) could only be
stopped from the standalone full-screen `AlarmActivity` or the notification's Dismiss
action. When `AlarmActivity` failed to come to the foreground (secured lock screen, missing
full-screen-intent / "display over other apps" permission, or OEM background-launch
restrictions), the user opened the main app and found the alarm still sounding with no
on-screen way to stop it — forcing a force-kill of the app.

## Changes

- `app/src/main/java/in/sreerajp/chronotune_smart_clock/MainActivity.kt`
  - Added import for `in.sreerajp.chronotune_smart_clock.ui.AlarmService`.
  - In `ClockAppScreen`, replaced the comment-only no-op at the end of the root `Box` with
    a fallback that observes `ActiveAlarmState.activeAlarm` via
    `collectAsStateWithLifecycle()` and renders `AlarmRingingOverlay` whenever an alarm is
    active. Dismiss/Snooze are wired to the same service-based teardown
    (`AlarmService.stopIntent`, with `ActiveAlarmState.dismiss` fallback) and
    `ActiveAlarmState.scheduleSnooze` that `AlarmActivity` already uses.

## Behavior

- Whenever an alarm is sounding, there is now always a visible Dismiss/Snooze screen — in
  the main app as well as the dedicated `AlarmActivity`.
- Happy path unchanged: when `AlarmActivity` handles the alarm, `activeAlarm` becomes null
  and the main-app overlay never appears.
- No changes to audio engine ownership, the notification Dismiss action, or the
  lock-screen / full-screen-intent launch path.

## Verification

- `./gradlew :app:compileDebugKotlin` succeeds (warnings only, no errors).
- Manual device/emulator verification of dismiss + snooze from the main-app overlay still
  recommended.
