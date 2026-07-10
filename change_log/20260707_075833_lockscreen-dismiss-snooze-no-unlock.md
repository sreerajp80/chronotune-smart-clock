# Lock screen: Dismiss/Snooze without unlocking

Implements plan
[plans/20260707_075833_lockscreen-dismiss-snooze-no-unlock.md](../plans/20260707_075833_lockscreen-dismiss-snooze-no-unlock.md).

## What was wrong

When an alarm rang while the phone was locked, tapping **Dismiss** or **Snooze**
on the alarm screen made the phone ask the user to unlock (PIN / pattern /
fingerprint) before the action completed.

## Cause

`AlarmActivity.onCreate` called `KeyguardManager.requestDismissKeyguard(...)`,
which asks Android to remove the lock screen. On a secure keyguard that can only
be satisfied by the user authenticating, so it triggered the unlock prompt. The
older-Android code path did the same with the `FLAG_DISMISS_KEYGUARD` window
flag.

## What changed

File: `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmActivity.kt`

- Removed the `KeyguardManager` lookup and the `requestDismissKeyguard(this, null)`
  call.
- Removed `FLAG_DISMISS_KEYGUARD` from the pre-`O_MR1` window-flags branch,
  keeping `FLAG_SHOW_WHEN_LOCKED`, `FLAG_TURN_SCREEN_ON`, and
  `FLAG_KEEP_SCREEN_ON`.
- Removed the now-unused `android.app.KeyguardManager` and
  `android.content.Context` imports.
- Added a comment explaining why the keyguard is intentionally not dismissed.

The alarm screen still shows over the lock screen and turns the screen on
(via `setShowWhenLocked` / `setTurnScreenOn` and the manifest's
`android:showWhenLocked` / `android:turnScreenOn`). Now Dismiss and Snooze run
directly from the lock screen with no unlock prompt; afterwards the phone
returns to the still-locked lock screen.

## Verification

- `./gradlew :app:compileDebugKotlin` compiles cleanly (only unrelated JVM
  native-access warnings).
- Manual test to confirm on-device: set an alarm, lock the phone with a secure
  PIN/pattern, let it ring, and verify Dismiss and Snooze both work without an
  unlock prompt.
