# Change log: snooze length and snooze tone

Implements plan `plans/20260718_093906_snooze-length-and-tone.md`.

## What was wrong

1. **Snooze length ignored.** All snooze paths called `scheduleSnooze()` without a
   `snoozeMinutes` argument, so it always used the hardcoded fallback of 5 minutes.
   The configured "Default snooze length" and per-alarm `snoozeMinutes` never reached
   the scheduler.
2. **Wrong tone after snooze.** The snooze re-scheduler set only `customToneName` and
   dropped `customToneUri`. Alarms using a custom sound (picked file / device ringtone)
   lost their URI on snooze and re-rang with the system default alarm sound.

## What was changed

### `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/Receivers.kt`
- Added `snoozeMinutes: Int = 5` to the `ActiveAlarmState.ActiveAlarm` data class.
- `scheduleSnooze()` now takes a `uri: String?` parameter and sets `customToneUri`
  (and `snoozeMinutes`) on the temporary snooze alarm.
- `AlarmReceiver.onReceive` reads the `SNOOZE_MIN` intent extra and passes it into the
  `ActiveAlarm` it builds.
- `AlarmSnoozeReceiver` (notification-action snooze) reads `SNOOZE_MIN` and `URI` and
  forwards both to `scheduleSnooze()`.
- The internal `snooze()` helper now forwards `current.uri`.

### `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmService.kt`
- Added `EXTRA_SNOOZE_MIN` and carried `SNOOZE_MIN` through the service `startIntent`
  and `onStartCommand`, so the `ActiveAlarm` the service builds knows its snooze length.
- Added `URI` and `SNOOZE_MIN` extras to the notification's snooze intent.

### `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmActivity.kt`
- Full-screen alarm `onSnooze` now passes `ring.uri` and `ring.snoozeMinutes` into
  `scheduleSnooze()`.

### `app/src/main/java/in/sreerajp/chronotune_smart_clock/ClockAppScreen.kt`
- In-app overlay `onSnooze` now passes `ring.uri` and `ring.snoozeMinutes` into
  `scheduleSnooze()`.

### `app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmRingingOverlay.kt`
- Snooze button label changed from the hardcoded "SNOOZE (5 MIN)" to
  "SNOOZE (${alarm.snoozeMinutes} MIN)" so it reflects the real length.

## Result

- Snooze now fires after the alarm's configured snooze length instead of always 5 min.
- Custom-tone alarms keep their own sound on snooze instead of falling back to the
  system default.
- Defaults remain 5 minutes, so alarms without a set value behave as before.

## Verification

- `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL.
- Still recommended on-device: create an alarm with a custom device ringtone and a
  non-default snooze length (e.g. 15 min), let it ring, snooze from the full-screen
  alarm, and confirm it re-rings after ~15 min with the same custom tone; repeat via
  the notification action and the in-app overlay.
