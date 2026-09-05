# Dismissed alarm no longer rings again when the app is opened

Implements [plans/20260815_190238_dismissed-alarm-rings-again-in-app.md](../plans/20260815_190238_dismissed-alarm-rings-again-in-app.md).

## What was wrong

The app had a second alarm evaluator running inside `ClockViewModel`, on top of the real
AlarmManager path. Every 100 ms it rang any alarm or music schedule whose hour and minute
matched the clock. It kept no record of what had already rung, so after the user dismissed
an alarm from the full-screen screen, opening the app inside that same clock minute made it
ring all over again.

It also rang with default settings instead of the alarm's own — no dismiss challenge, wrong
snooze length and limit, no auto-silence, no foreground service or notification, and it
ignored holiday mode and start date.

## What changed

`app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ClockViewModel.kt`

- Removed the `checkInAppTriggers` function (the in-app evaluator).
- Removed its call from `startClocksTicker`, and removed the now-unused
  `lastTriggeredMinute` field and the `java.util.Calendar` import.
- Added a comment on `startClocksTicker` saying it drives the clock readout only, and why
  the ringing is left entirely to the scheduler.

No other file changed. Ringing, dismissing and snoozing all continue through
`AlarmManager` -> `AlarmReceiver` -> `AlarmService` -> `ActiveAlarmState`, which is the
path every alarm already used when the app was closed. That path uses `setAlarmClock`, so
it fires whether the app is open, backgrounded or killed.

## Checks done

- `./gradlew :app:compileDebugKotlin` passes.
- Not yet run on a device. Worth confirming by hand: ring an alarm, dismiss it from the
  full-screen screen, then open the app within the same minute and move between tabs — it
  must stay silent. Same for dismissing from the notification, for snooze (must still ring
  after the gap), for a music schedule, and for an alarm firing while the app is already in
  the foreground (must ring once, with its own tone and settings).
