# Enforce Dismiss Challenge on the Notification Action

**Status:** completed

> Note: during implementation a second notification builder
> (`buildSilentNotification`, the low-importance variant shown while the alarm
> screen is up) was found to have the same Dismiss bypass. Both builders were
> fixed with the same guard so the shade bypass is fully closed.

## The issue

When an alarm has a dismiss challenge set, the full-screen ringing overlay
enforces it — but the alarm's **notification** has its own "Dismiss" action
(reachable if the user swipes the full-screen alarm away to the notification
shade). That action goes straight to `AlarmDismissReceiver`, which stops the
alarm with no challenge. So a half-asleep user can defeat the challenge from the
shade.

## The fix

Make the notification "Dismiss" action **reopen the full-screen alarm** when a
challenge is set, instead of stopping the alarm directly. The full-screen screen
already enforces the challenge, so the only way to actually turn the alarm off
stays "complete the challenge".

When there is **no** challenge, the action keeps its current behavior (stop the
service directly) — no extra tap for those users.

This is done entirely inside `AlarmService.buildNotification`:
- Compute `hasChallenge = alarm.type == "ALARM" && alarm.dismissChallenge != "NONE"`.
- For the Dismiss action's `PendingIntent`, use the existing
  `fullScreenPendingIntent` (which opens `AlarmActivity`) when `hasChallenge`,
  otherwise the existing `dismissPending` (the `AlarmDismissReceiver` path).

No new intents, receivers, or extras are needed — `fullScreenPendingIntent` is
already built and passed into `buildNotification`, and it is the same intent used
for tapping the notification body.

## Files to change

- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmService.kt`
  — route the Dismiss action based on `hasChallenge`.

## Not in scope

- Snooze action is unchanged (it already re-arms with the challenge intact).
- Music/Timer notifications are unaffected (they are never `type == "ALARM"`
  with a challenge).

## Testing

- Build `:app:compileDebugKotlin`.
- Manual: set an alarm with a challenge, let it ring, swipe the full-screen
  alarm away, tap Dismiss in the shade → the full-screen alarm should come back
  and require the challenge. Set an alarm with challenge = None → Dismiss in the
  shade still stops it immediately.
