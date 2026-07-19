# Change Log — Enforce Dismiss Challenge on the Notification Action

Implements plan `plans/20260719_120200_notification-dismiss-challenge.md`.

## What changed

The alarm notification's "Dismiss" action no longer bypasses a dismiss
challenge. When an alarm has a challenge set, tapping Dismiss in the
notification shade now **reopens the full-screen alarm** (where the challenge is
enforced) instead of stopping the alarm directly. With no challenge set, the
action still stops the alarm immediately — no change for those users.

The fix was applied to both notification builders in `AlarmService`:
- `buildNotification` (the heads-up alarm notification) — routes the Dismiss
  action to the existing `fullScreenPendingIntent` when a challenge is set.
- `buildSilentNotification` (the low-importance variant shown while the alarm
  screen is up) — had the same bypass; routes Dismiss to its existing
  `openPending` (which opens the full-screen alarm) when a challenge is set.

In both cases the guard is
`hasChallenge = alarm.type == "ALARM" && alarm.dismissChallenge != "NONE"`.
No new intents, receivers, or extras were needed.

## Files changed

- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmService.kt`
  — challenge-aware routing of the Dismiss action in both notification builders.

## Verification

- `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL.
