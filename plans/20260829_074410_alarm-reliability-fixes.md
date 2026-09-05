# Alarm reliability fixes

**Status:** completed

## What the problem is

The user reports that the alarm sometimes does not ring. Reading the whole alarm path
(`AlarmScheduler` -> `AlarmReceiver` -> `AlarmService` -> `AlarmActivity`) turned up ten
separate ways a ring can be lost. None of them is proven from a device log yet, so this
plan fixes the causes we can see in the code, and the companion plan
(`20260829_074500_alarm-event-history.md`) adds the log that will prove which one is real.

### Cause A - nothing re-arms alarms except boot, edit and clock change

Alarms are armed in only three places:

- add / edit / toggle in `ClockViewModel` (lines 227-283),
- inside `AlarmReceiver.rescheduleNextOccurrence` right after a firing (Receivers.kt 396-458),
- `BootReceiver` on boot / package replace / time change (Receivers.kt 466-515).

`ClockViewModel.init` (line 143) does not re-arm anything, so simply opening the app does
not repair a lost alarm.

Android silently drops every pending alarm an app owns when the app is force-stopped, when
an OEM battery cleaner kills it, or when the system moves it to the "restricted" standby
bucket or hibernates it for being unused. After any of those the alarm is gone for good
until the user reboots or edits that alarm. This is the most likely cause of the reported
problem.

### Cause B - no battery-optimisation prompt

`SettingsScreen` (lines 131-141) checks notifications, exact-alarm and overlay permission,
but never asks the user to exempt the app from battery optimisation, and gives no hint
about OEM auto-start managers.

### Cause C - the `set()` fallback makes a weak alarm

`AlarmScheduler.kt` line 108: when `setAlarmClock` throws `SecurityException` the code falls
back to plain `alarmManager.set()`. That alarm is inexact and not Doze-exempt, so it can be
hours late. Worse, a broadcast delivered by plain `set()` carries no temporary allow-list, so
the `startForegroundService` call at Receivers.kt line 317 can throw
`ForegroundServiceStartNotAllowedException` and the ring dies with no sound at all.

### Cause D - music schedules never use the privileged path

`AlarmScheduler.scheduleMusic` (lines 158-162) always uses `setAndAllowWhileIdle`. In Doze
that is rate-limited to about one firing every nine minutes, so scheduled music can start
late or be skipped.

### Cause E - two rings at the same time, one is lost

`AlarmService` keeps a single static `currentAlarmId` / `currentAlarm` (lines 211-212) and
`ActiveAlarmState` holds one `_activeAlarm`. If two alarms, or an alarm and a timer, fire in
the same minute, the second `onStartCommand` overwrites the first: the audio of the first is
replaced and its notification is orphaned. To the user, one of the two alarms did not work.

### Cause F - snooze IDs collide with timer IDs after four snoozes

Each snooze builds a temporary alarm at `id + 50000` (Receivers.kt line 177), and snoozing a
snoozed alarm adds another 50000. Four snoozes of alarm 5 gives ID 200005, which is exactly
the timer ring space (`RING_ID_OFFSET = 200000`, Models.kt line 201). The two then share a
`PendingIntent` request code and a notification ID, so one cancels the other. With the
default unlimited snoozes this is reachable in one morning.

### Cause G - turning an alarm off does not cancel its pending snooze

`AlarmScheduler.cancelAlarm` (lines 117-129) cancels only the base ID. A snooze pending at
`id + 50000` survives, so a disabled alarm can still ring once more.

### Cause H - boot restore only happens after the first unlock

The manifest registers `LOCKED_BOOT_COMPLETED` but sets `android:directBootAware="false"`
(AndroidManifest.xml line 142). A receiver that is not direct-boot aware never gets that
broadcast, and the Room database is encrypted before first unlock anyway. So after a reboot
alarms are re-armed only once the phone is unlocked. Reboot at night, no alarm in the morning.

### Cause I - nothing reacts when exact-alarm permission is granted later

There is no receiver for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`. An alarm
saved while the permission was off stays on the weak inexact path even after the user grants
the permission.

### Cause J - alarm stream volume can be left stuck at maximum

`AudioEngine` raises `STREAM_ALARM` to maximum and restores it on stop (AudioEngine.kt 41-55).
If the process is killed mid-ring the saved value is never put back.

## Files to be changed

| File | Change |
| --- | --- |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/data/Models.kt` | New `AlarmIds` object holding every ID offset in one place |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmScheduler.kt` | Stronger fallback, `isArmed()` check, cancel snooze chain, music on the privileged path, watchdog arming |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/Receivers.kt` | Fixed snooze ID, `BASE_ID` extra, new `WatchdogReceiver` and `ExactAlarmPermissionReceiver`, `USER_PRESENT` re-arm |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmService.kt` | Queue a second ring instead of overwriting the first |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ClockViewModel.kt` | Re-arm every enabled alarm on start-up |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/audio/AudioEngine.kt` | Persist the saved alarm stream volume so it survives a process kill |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/AppPrefs.kt` | Keys for the saved stream volume and the watchdog interval |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/SettingsScreen.kt` | Battery-optimisation row in the health check, plus an OEM auto-start hint |
| `app/src/main/AndroidManifest.xml` | Register the two new receivers, add `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |
| `app/src/main/res/values/strings.xml` | New strings |
| `app/src/main/res/values-ml/strings.xml` | Malayalam for the same strings |
| `app/src/test/java/in/sreerajp/chronotune_smart_clock/AlarmIdsTest.kt` | New test for the ID map |
| `app/src/test/java/in/sreerajp/chronotune_smart_clock/ScheduleTimingTest.kt` | Extra cases for the snooze chain |

## The plan for the fix

### 1. One ID map (fixes F)

Add to `Models.kt`:

```kotlin
object AlarmIds {
    const val MUSIC_OFFSET       = 10_000
    const val TIMER_RING_OFFSET  = 200_000   // existing TimerItem.RING_ID_OFFSET, kept
    const val SNOOZE_OFFSET      = 500_000
    const val SNOOZE_ACTION_PI   = 600_000
    const val ADD_MINUTE_PI      = 700_000
    const val DISMISS_ACTION_PI  = 800_000
}
```

The snooze ring becomes `baseId + SNOOZE_OFFSET`, always computed from the **original** alarm
ID, never from the previous snooze ID. To make that possible the intent carries a new
`BASE_ID` extra that every snooze passes through unchanged. The result: a snooze chain of any
length reuses one slot, so it can never grow into the timer space.

`TimerItem.RING_ID_OFFSET` keeps its current value and is re-exported from `AlarmIds`, so
pending intents made by the old build still match.

On upgrade there may be one stale snooze pending in the old `+50000` space. `BootReceiver`
already runs on `MY_PACKAGE_REPLACED`; it will additionally cancel `id + 50000`,
`id + 100000` and `id + 150000` for every alarm, best-effort, so nothing is left behind.

### 2. Cancel the snooze chain on disable / delete (fixes G)

`AlarmScheduler.cancelAlarm` also cancels `baseId + SNOOZE_OFFSET`. Same in the delete path.

### 3. Re-arm on app start and on unlock (fixes A and H)

- `ClockViewModel.init` gains `rearmAllOnStart()`: for every enabled alarm and music schedule,
  check whether its `PendingIntent` still exists (`FLAG_NO_CREATE` returns `null` when the OS
  has dropped it) and re-arm only the missing ones, so we do not churn hundreds of pending
  intents on every launch. Wrapped in try/catch and run on `Dispatchers.IO`.
- `BootReceiver` also handles `ACTION_USER_PRESENT`, registered at runtime from the
  application context because `USER_PRESENT` cannot be declared in the manifest since
  Android 8. This covers the reboot-at-night case: alarms are re-armed the moment the phone
  is first unlocked.
- Keep `directBootAware="false"`. Making the database direct-boot aware is a much larger
  change (a second Room instance on device-encrypted storage) and is out of scope here; the
  `USER_PRESENT` hook plus the watchdog below covers the practical gap.

### 4. A self-rescheduling watchdog (fixes A, the general case)

A new `WatchdogReceiver`, armed by `AlarmScheduler.scheduleWatchdog()` with
`setAndAllowWhileIdle` roughly every 3 hours (interval in `AppPrefs`). Every firing:

1. re-arms itself for the next slot,
2. loads all enabled alarms, checks each `PendingIntent` with `FLAG_NO_CREATE`, and re-arms
   any that have vanished,
3. writes a `RESCHEDULED_WATCHDOG` event once the history plan lands.

We deliberately do **not** add WorkManager. The project keeps its dependency list trimmed
(see the commented-out entries in `app/build.gradle.kts`), and a self-rescheduling
`AlarmManager` broadcast does the same job with no new library. The trade-off is that the
watchdog itself is dropped when the app is force-stopped - which is why the app-start and
unlock hooks in step 3 exist as well. Between the three, a force-stop is repaired at the
next unlock at the latest.

### 5. Make the weak fallback safe (fixes C and D)

- In `scheduleAlarm`, replace the `SecurityException` fallback `alarmManager.set(...)` with
  `setAndAllowWhileIdle(...)`, which is Doze-exempt and grants the short allow-list the
  foreground service start needs. Same in `scheduleTimer`.
- In `scheduleMusic`, use the same `canScheduleExactAlarms()` check as alarms and prefer
  `setAlarmClock` when allowed, falling back to `setAndAllowWhileIdle`.
- Wrap the `startForegroundService` call in `AlarmReceiver` in a try/catch. On
  `ForegroundServiceStartNotAllowedException`, fall back to sending the full-screen
  `PendingIntent` directly so the user still gets a ringing screen, and record the failure.

### 6. Do not lose a second, simultaneous ring (fixes E)

`AlarmService` keeps a small ordered map of pending rings instead of a single
`currentAlarm`. The first ring owns the audio; a second one that arrives while the first is
still active is added to the queue and gets its own silent notification saying it is waiting.
When the active ring is dismissed or snoozed, `stopAlarmAndSelf` starts the next queued ring
instead of stopping the service. The service stops only when the queue is empty.

### 7. Battery-optimisation prompt (fixes B)

Add a fourth row to the Settings health check: "Ignore battery optimisation", read with
`PowerManager.isIgnoringBatteryOptimizations`, with a button that opens
`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Below it, a short static note telling the user
to allow auto-start and to remove the app from the battery cleaner on OEM phones, since that
setting cannot be read or opened reliably from code.

### 8. React to exact-alarm permission being granted (fixes I)

A new `ExactAlarmPermissionReceiver` for
`AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` (Android 12+). It re-arms
every enabled alarm so they all move up to `setAlarmClock`.

### 9. Restore the alarm stream volume reliably (fixes J)

`AudioEngine` writes the saved `STREAM_ALARM` level into `AppPrefs` before raising it, and
clears the key after restoring. On the next `AudioEngine` construction, if the key is still
set (meaning the process died mid-ring), restore it first and clear it.

## Testing

- Unit tests for `AlarmIds`: a snooze chain of ten snoozes never leaves the snooze space and
  never touches the timer space.
- Unit tests for the snooze gap and the base-ID carry-through in `ScheduleTimingTest`.
- Manual checks: force-stop the app then reopen it (alarm must be re-armed); disable a snoozed
  alarm (no further ring); set two alarms one minute apart (both must ring in turn); revoke
  and re-grant exact-alarm permission (alarms must upgrade).

## Order of work

Steps 1, 2, 5 and 9 are small and independent. Steps 3 and 4 are the ones that actually fix
the reported problem and should land together. Steps 6, 7 and 8 follow. The history plan
should land after this one, so the new events (watchdog re-arm, ring failure, queued ring)
can be recorded from the start.
