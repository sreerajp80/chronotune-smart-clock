# Show the ringing/dismiss overlay in the main app as a fallback

## The issue

The alarm audio is started by the foreground `AlarmService` and owned by `AudioEngine`
inside the global `ActiveAlarmState`. The only UIs that can stop that audio are:

1. the standalone full-screen `AlarmActivity` (`AlarmRingingOverlay`), and
2. the alarm notification's **Dismiss** action.

`MainActivity` / `ClockAppScreen` intentionally renders **nothing** for an active alarm
(see comment at `MainActivity.kt:367-369`).

When `AlarmActivity` fails to come to the foreground — secured lock screen (PIN/pattern),
`USE_FULL_SCREEN_INTENT` not granted, "Display over other apps" off, or OEM
background-launch restrictions — the user unlocks and opens the **main app**, which shows
the normal clock UI with **no way to stop the still-playing alarm**. The only escape is
force-killing the app (which kills the service process and the audio with it).

Reported symptoms, all explained by this gap:
- "To see the alarm I have to unlock the phone."
- "Even if I dismiss after unlocking and opening the app, the tone keeps playing — no
  screen to stop it."
- "I have to kill the app."
- "The alarm sound should not play without the alarm screen."

## Files to change

- `app/src/main/java/in/sreerajp/chronotune_smart_clock/MainActivity.kt`
  - In `ClockAppScreen` (the root `Box`), observe `ActiveAlarmState.activeAlarm` with
    `collectAsStateWithLifecycle()`.
  - When it is non-null, render `AlarmRingingOverlay` on top of everything else (inside the
    existing root `Box`, after the `Scaffold`), wired to the same teardown used by
    `AlarmActivity`:
    - `onDismiss`: `startService(AlarmService.stopIntent(context))` with a
      `ActiveAlarmState.dismiss(context)` fallback in a `try/catch`.
    - `onSnooze`: capture id/label/tone/volume, `startService(AlarmService.stopIntent(...))`
      (fallback `dismiss`), then `ActiveAlarmState.scheduleSnooze(...)`.
  - Needs `LocalContext`, the `AlarmService` import, and `ActiveAlarmState` (already
    imported at `MainActivity.kt:65`).
  - The overlay already fills the screen with a near-opaque background, so it visually
    takes over the app surface while ringing — matching "no sound without a stop screen."

## Why this is the right fix

- It guarantees that whenever audio is playing, there is an in-app screen to stop it,
  regardless of how the user reaches the app or whether the dedicated `AlarmActivity`
  managed to launch.
- It reuses the existing `AlarmRingingOverlay` composable and the existing
  service-based teardown — no new audio/lifecycle logic, no behavioral change to the
  happy path (when `AlarmActivity` does appear, the user dismisses there and
  `activeAlarm` becomes null, so the main-app overlay never shows).
- It does not remove or weaken the `AlarmActivity` path; it is purely an additive
  fallback on the main surface.

## Out of scope / not changing

- The lock-screen / full-screen-intent launch path itself (that is a permissions/OEM
  matter; `MainActivity` already prompts for FSI and overlay permissions).
- The notification Dismiss action (still works as-is).
- Audio engine ownership/lifecycle.

## Verification

- Build the app.
- Simulate: trigger an alarm, then from the main app confirm the overlay appears and
  **Dismiss** stops the audio and clears the overlay; **Snooze** stops audio, clears the
  overlay, and re-arms.
- Confirm the normal `AlarmActivity` flow still dismisses correctly and the main-app
  overlay does not flash when `AlarmActivity` handled it.
