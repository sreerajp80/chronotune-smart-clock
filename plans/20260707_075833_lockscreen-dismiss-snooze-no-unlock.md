# Lock screen: Dismiss/Snooze without unlocking

**Status:** completed

## The issue

When an alarm rings and the phone is locked, the alarm screen (`AlarmActivity`)
shows on top of the lock screen. But when the user taps **Dismiss** (or Snooze),
the phone asks them to unlock (enter PIN / pattern / fingerprint) before the
action completes. The user wants Dismiss and Snooze to work directly from the
lock screen, with no unlock step.

## Root cause

In [AlarmActivity.kt](app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmActivity.kt),
`onCreate` calls `requestDismissKeyguard(...)`:

```kotlin
val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
km?.requestDismissKeyguard(this, null)
```

`requestDismissKeyguard` explicitly asks the system to remove the lock screen.
On a *secure* keyguard (PIN / pattern / fingerprint) that request can only be
satisfied by the user authenticating — so it pops the unlock prompt. The same
happens through the `FLAG_DISMISS_KEYGUARD` window flag used on older Android
versions.

We do not actually need to dismiss the keyguard. `setShowWhenLocked(true)`
(and the matching `android:showWhenLocked="true"` in the manifest) already lets
the activity draw over the lock screen, and the user can tap the buttons on it
without unlocking. Dismiss/Snooze just call the service and `finish()`; after
finishing, the user returns to the still-locked lock screen, which is the
correct and expected behaviour.

So the unlock prompt is caused by the keyguard-dismiss request, and removing it
fixes the problem while keeping the alarm UI fully usable over the lock screen.

## Files to change

- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmActivity.kt`

## The fix

1. Remove the `requestDismissKeyguard(...)` call (and the now-unused
   `KeyguardManager` lookup and its `Context` / `KeyguardManager` imports if they
   become unused).
2. Remove `FLAG_DISMISS_KEYGUARD` from the pre-`O_MR1` window-flags branch,
   keeping `FLAG_SHOW_WHEN_LOCKED`, `FLAG_TURN_SCREEN_ON`, and
   `FLAG_KEEP_SCREEN_ON`.

Result: the alarm screen still appears over the lock screen and turns the screen
on, but tapping Dismiss or Snooze no longer triggers an unlock prompt — the
action runs and the phone stays locked.

## Testing

- Set an alarm, lock the phone (with a secure PIN/pattern set), let it ring.
- Confirm the alarm screen appears over the lock screen.
- Tap **Dismiss** — alarm stops, no unlock prompt, phone returns to lock screen.
- Tap **Snooze** — alarm stops and re-schedules, no unlock prompt.
