# Dismissed alarm rings again when the app is opened

**Status:** completed

## The issue

Steps that show the bug:

1. An alarm is set for, say, 07:00.
2. At 07:00 it rings. The full-screen alarm screen (`AlarmActivity`) comes up.
3. The user taps DISMISS. Audio stops, the screen closes. Good so far.
4. Still inside the same clock minute (before 07:01), the user opens the app, or is
   already in the app and moves to the Alarms screen.
5. The alarm starts ringing again, with the in-app ringing overlay on top.

### Why it happens

`ClockViewModel` runs a second, independent alarm evaluator in the app process:

- [ClockViewModel.kt:185-198](app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ClockViewModel.kt#L185-L198) —
  a ticker that runs every 100 ms while the app is alive and calls `checkInAppTriggers(now)`.
- [ClockViewModel.kt:200-257](app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ClockViewModel.kt#L200-L257) —
  `checkInAppTriggers` walks every alarm and every music schedule and rings any whose
  hour + minute match the current clock time.

It has only two guards, and both fail in this case:

1. `if (minute == lastTriggeredMinute) return` — `lastTriggeredMinute` is written **only**
   when this in-app path itself rings something
   ([ClockViewModel.kt:230](app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ClockViewModel.kt#L230)).
   The real alarm came through `AlarmReceiver` -> `AlarmService`, so this field was never
   updated and still holds a stale minute. The guard lets the tick through.
2. `if (ActiveAlarmState.activeAlarm.value?.id != alarm.id)` — after the user dismisses,
   `ActiveAlarmState.dismiss()` sets `activeAlarm` to `null`
   ([Receivers.kt:104-111](app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/Receivers.kt#L104-L111)),
   so the id no longer matches and this guard also lets the tick through.

Result: the very next 100 ms tick inside that same minute calls
`ActiveAlarmState.triggerAlarm(...)` again, audio restarts, and the fallback overlay in
[ClockAppScreen.kt:155-193](app/src/main/java/in/sreerajp/chronotune_smart_clock/ClockAppScreen.kt#L155-L193)
appears. Nothing in the app records "this alarm already rang and was dismissed".

The same hole applies to music schedules ([ClockViewModel.kt:237-256](app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ClockViewModel.kt#L237-L256)).

### Extra problems with the same code path

Even when it is not double-ringing, this in-app evaluator rings alarms in a degraded way,
because it builds `ActiveAlarm` with default values and calls `triggerAlarm` directly
instead of going through `AlarmService`:

- No dismiss challenge is carried, so a challenge alarm can be dismissed with one tap.
- No `snoozeMinutes`, `maxSnoozeCount`, `snoozeMode`, `autoSilenceMinutes` — all fall back
  to defaults, so the user's per-alarm settings are ignored.
- No foreground service, no notification, no wake lock: if the app is closed while this
  ring plays, audio can be reaped with no way to stop it.
- It ignores `holidayMode` and `startEpochDay`, which `AlarmReceiver` does honour
  ([Receivers.kt:284-303](app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/Receivers.kt#L284-L303)).

## The fix

Remove the in-app trigger evaluator. It is a duplicate of the real alarm path and is the
direct cause of the re-ring.

The real path is already the reliable one: `AlarmScheduler` uses `setAlarmClock`
([AlarmScheduler.kt:78-97](app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmScheduler.kt#L78-L97)),
the privileged API that fires through Doze and battery restrictions, and falls back to
`setAndAllowWhileIdle` when exact alarms are not permitted. It fires whether the app is
open, backgrounded, or killed. The in-app copy adds no coverage the scheduler does not
already give, and it re-rings dismissed alarms with the wrong settings.

Concretely:

1. Delete `checkInAppTriggers` and its call from the clock ticker.
2. Delete the now-unused `lastTriggeredMinute` field.
3. Keep the ticker itself — it still drives `_currentTime` for the clock display.

Nothing else changes. Ringing, dismissing, snoozing, the full-screen screen, and the
in-app fallback overlay all keep working through `AlarmReceiver` -> `AlarmService` ->
`ActiveAlarmState`, exactly as they do now for every alarm that fires while the app is
closed.

### Alternative considered (not recommended)

Keep the evaluator and add a "already handled" record — e.g. remember `alarmId` plus
minute-of-day whenever any path rings or dismisses an alarm, and skip it here. This is
more code, needs the record to be shared across the receiver, the service and the view
model, and still leaves the degraded-settings problems listed above. It only makes sense
if the in-app evaluator is wanted as a genuine safety net; the plan above says it is not.

## Files to change

- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ClockViewModel.kt`
  - remove `lastTriggeredMinute` (line 65)
  - remove the `checkInAppTriggers(now)` call from `startClocksTicker` (line 193)
  - remove the `checkInAppTriggers` function (lines 200-257)

## How to test

1. Set an alarm one minute ahead. Let it ring. Dismiss from the full-screen screen.
   Immediately open the app and move between tabs — it must stay silent.
2. Same test, but dismiss from the notification's Dismiss action.
3. Same test, but snooze instead of dismiss — the snoozed alarm must still ring after the
   snooze gap and must not ring early.
4. Set a music schedule one minute ahead, let it play, dismiss, open the app — must stay
   silent.
5. With the app open in the foreground, let an alarm fire — it must still ring exactly
   once, with its own tone, challenge and snooze settings.
