# Alarm reliability fixes

Implements `plans/20260829_074410_alarm-reliability-fixes.md`.

The goal was to close every way a ring could be silently lost that could be seen in the code.
The companion history feature (`plans/20260829_074500_alarm-event-history.md`) will show which
of these was actually happening on the phone.

## What changed

### One id map (`data/Models.kt`)

New `AlarmIds` object holding every AlarmManager request code / notification id the app uses:
alarms, music, timer rings, snooze rings, and the notification action codes. It also has
`baseAlarmId()` and `isSnoozeRing()` helpers.

The snooze id used to be built by adding 50000 to whatever was ringing, so a snoozed snooze
climbed: id+50000, id+100000, id+150000. The fourth snooze of alarm N landed on N+200000 —
exactly a timer's ring id, where the two would cancel each other. Now every snooze is derived
from the original alarm (`AlarmIds.snoozeRing(baseId)`), so a chain of any length reuses one
slot.

To make that work, a `BASE_ID` extra is now carried through the whole chain: scheduler ->
receiver -> service -> notification actions -> ringing screen. `ActiveAlarm` gained a `baseId`
field for the same reason.

`TimerItem.RING_ID_OFFSET` keeps its value (200000) and is re-exported from `AlarmIds`, so
pending intents armed by the old build still match after an upgrade.

### Cancelling now clears the snooze too (`ui/AlarmScheduler.kt`)

`cancelAlarm` cancels the base id **and** the snooze slot. Switching an alarm off between a
snooze and its re-ring used to leave the re-ring armed, so a disabled alarm still went off once
more. `cancelTimer` / `cancelMusic` now share one `cancelByRequestCode` helper.

`cancelLegacySnoozes` clears anything left pending in the old +50000 / +100000 / +150000 space;
`BootReceiver` runs it once per alarm on package replace, so an upgrade cannot inherit a stray
re-ring.

### Alarms are re-armed when they go missing

This is the fix for the most likely cause of the reported problem. Android silently drops every
pending alarm an app owns when the app is force-stopped, killed by an OEM battery cleaner, or
hibernated for being unused. Nothing rebuilt them, so the alarm stayed lost until the user
rebooted or edited it.

Three new hooks, all built on `AlarmScheduler.isArmed()`, which uses `FLAG_NO_CREATE` to ask
the OS whether a pending broadcast still exists:

- **On app start** — `ClockViewModel.repairAlarmsOnStart()` re-arms only the alarms and music
  schedules whose pending intent has vanished, then arms the watchdog.
- **A periodic watchdog** — new `WatchdogReceiver`, armed roughly every 3 hours
  (`AppPrefs.WATCHDOG_INTERVAL_MS`). It re-arms itself first, then repairs whatever is missing.
  No WorkManager was added: the project keeps its dependency list trimmed, and a
  self-rescheduling `AlarmManager` broadcast does the same job.
- **On unlock** — `BootReceiver.registerUnlockWatch()` listens for `ACTION_USER_PRESENT`
  (registered at runtime from `MainActivity`, since it cannot be declared in the manifest).

An alarm that already has a snooze pending is left alone, so the repair never arms a base alarm
on top of a live snooze.

**Honest limit:** the runtime unlock watch and the watchdog both die with the process, so a
force-stop is not repaired until the app is next opened. Nothing an app can do survives a
force-stop; the start-up repair is the backstop for that case. `BootReceiver` also re-arms the
watchdog after a reboot, since AlarmManager alarms do not survive one.

### The weak fallback no longer loses the ring (`ui/AlarmScheduler.kt`)

When `setAlarmClock` throws `SecurityException` the code fell back to plain `alarmManager.set()`.
That alarm is inexact and not Doze-exempt, and — worse — a broadcast delivered by `set()` carries
no temporary allow-list, so the receiver's `startForegroundService` would be refused and the ring
lost with no sound at all. Both the alarm and timer paths now fall back to
`setAndAllowWhileIdle`.

`AlarmReceiver` also guards the `startForegroundService` call: if the OS refuses it anyway, the
audio is started in-process and the full-screen intent is sent directly, so the user still gets
a ringing screen. A ring that might be cut short beats silence.

### Music schedules use the privileged path (`ui/AlarmScheduler.kt`)

`scheduleMusic` always used `setAndAllowWhileIdle`, which Doze rate-limits to about one firing
every nine minutes. It now uses the same `canScheduleExactAlarms()` check and `setAlarmClock`
path as alarms, with while-idle as the fallback.

### A second simultaneous ring is no longer lost (`ui/AlarmService.kt`)

The service kept a single `currentAlarm`, so a second alarm firing in the same minute
overwrote the first: its audio stopped and its notification was orphaned. There is now a
`waitingRings` queue. The first ring keeps the audio and the screen; a second one gets a quiet
"Alarm waiting" notification and starts as soon as the active ring is dismissed or snoozed.
The service stops only when the queue is empty. `onDestroy` clears the queue and its notes.

The ringing logic moved out of `onStartCommand` into `startRinging()` so the queue can start
the next alarm directly.

### Snoozing from the alarm screen now behaves like the notification (`ui/AlarmActivity.kt`)

The screen's Snooze passed only the tone and challenge fields, leaving `maxSnoozeCount`,
`snoozeMode`, `autoSilenceMinutes` and `snoozeCount` at their defaults — which handed the user
unlimited snoozes on an alarm with a limit, and reset the progressive gap each time. All of them
are carried through now. This was next to the snooze-id work and part of the same defect, so it
was fixed here.

### Exact-alarm permission granted later (`ui/Receivers.kt`, manifest)

New `ExactAlarmPermissionReceiver` for
`SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`. An alarm saved while the permission was off
used to keep the weaker scheduling for ever; now every alarm is re-armed onto the exact path as
soon as the user grants it.

### Battery optimisation (`SettingsScreen.kt`, manifest)

A third row in the permissions health check: "Unrestricted Battery Usage", read with
`PowerManager.isIgnoringBatteryOptimizations`, opening
`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (falling back to the list screen when a build
hides the direct dialog). Below it, a note naming the OEMs whose auto-start / app-cleaner
settings also have to be changed, because those cannot be read or opened from code.
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` was added to the manifest.

### Alarm volume can no longer stay stuck at maximum (`audio/AudioEngine.kt`, `AppPrefs.kt`)

The engine raises the device alarm stream to maximum while ringing and restores it on stop. If
the process was killed mid-ring, the original level was never put back. The saved level is now
mirrored into `AppPrefs`, and `recoverAlarmStream()` puts it back at the next start-up. It is
called from the ViewModel only when nothing is ringing, so it can never quieten a live alarm.

## Files changed

- `app/src/main/java/in/sreerajp/chronotune_smart_clock/data/Models.kt`
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmScheduler.kt`
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/Receivers.kt`
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmService.kt`
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmActivity.kt`
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ClockViewModel.kt`
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/audio/AudioEngine.kt`
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/AppPrefs.kt`
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/SettingsScreen.kt`
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/MainActivity.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/test/java/in/sreerajp/chronotune_smart_clock/AlarmIdsTest.kt` (new)

## Testing

`:app:testDebugUnitTest` — 57 tests, all passing. `:app:assembleDebug` succeeds.

The new `AlarmIdsTest` checks that a ten-deep snooze chain never leaves its slot, that snooze
and timer ids never collide, that no two id spaces overlap for realistic row ids, and that the
timer offset keeps its historical value.

That last test caught a real problem while it was being written: the watchdog request code was
first set to 900001, which is exactly the "+1 min" action code of timer id 1. The two use
different receivers so they would not actually have clashed, but the code was moved to 1500000
— above every id any row can reach — rather than depend on that.

## Deviations from the plan

- **No new string resources.** The plan listed `strings.xml` and `values-ml/strings.xml`. The
  whole permissions section of `SettingsScreen` is hardcoded English already, so the new row
  follows the surrounding code instead of being the only localised item in it. Same for the
  "Alarm waiting" notification, which sits beside other hardcoded notification text in
  `AlarmService`. Worth doing as one pass over that section later, not piecemeal here.
- **No extra cases in `ScheduleTimingTest`.** The snooze-chain behaviour is now id arithmetic,
  so it is covered by `AlarmIdsTest` instead. The existing gap and limit tests still pass
  unchanged.
- **`directBootAware` left as `false`**, as the plan stated. Worth noting why this matters less
  than it first appeared: on encrypted devices Android delivers `BOOT_COMPLETED` after the first
  unlock anyway, so the reboot-overnight case was already being handled at unlock time.

## Not yet verified on a device

Everything here is compile- and unit-tested only. The behaviours that need a real phone are:
force-stop then reopen, two alarms one minute apart, a snooze taken four times, and the
battery-optimisation dialog on an OEM build.
