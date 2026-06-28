# Persist Stopwatch & Timer + real-alarm Timer — change log

Implements [plans/20260628_123627_persist-timer-stopwatch-real-alarm.md](../plans/20260628_123627_persist-timer-stopwatch-real-alarm.md).

## Summary

Two features delivered in one pass:

- **Feature A — persistence across process death.** The stopwatch and timers no longer live
  only in `ClockViewModel`'s memory. Both are backed by `SystemClock.elapsedRealtime()` bases
  stored durably (prefs for the stopwatch, Room for timers) and surfaced through a new
  foreground service that shows live, controllable notifications.
- **Feature B — the Timer is now a real alarm.** The hardcoded in-process beep
  (`triggerAlarm(99999, "Cosmic Shimmer", 0.8f)`) is gone. Each timer schedules an exact
  `setAlarmClock` alarm through `AlarmScheduler`, so it rings via the existing full-screen
  alarm stack even when backgrounded/Dozed/killed. Added user-selectable tone + volume,
  multiple concurrent timers, and named presets.

## Changes by file

### Data layer
- **`data/Models.kt`** — added `TimerItem` (durable timer with dual time bases: `endAtElapsed`
  for drift-free display, `fireAtWallClock` for the RTC ring; states IDLE/RUNNING/PAUSED/
  FINISHED; tone/volume; `RING_ID_OFFSET = 200000`) and `TimerPreset` entities.
- **`data/Daos.kt`** — added `TimerDao` and `TimerPresetDao`.
- **`data/AppDatabase.kt`** — registered the two entities, bumped DB `version 2 → 3`, added
  `MIGRATION_2_3` (creates both tables) and a shared `seedPresets` used by the migration and an
  `onCreate` callback (fresh installs / destructive fallback). Seeds "Tea" (3 m), "Power Nap"
  (20 m), "Workout" (25 m).
- **`data/repository/ClockRepository.kt`** — injected `TimerDao`/`TimerPresetDao`; exposed
  `allTimers`, `allTimerPresets` flows + timer/preset CRUD and `getAllTimersOnce`/`getTimerById`.

### New files
- **`StopwatchPrefs.kt`** — SharedPreferences-backed **and** observable (`StateFlow<Snapshot>`)
  hub for the single stopwatch. Mutations (`start/pause/reset/lap`) persist the elapsedRealtime
  base + laps and refresh the foreground service. Display is always derived from the base.
- **`ui/TimerEngine.kt`** — single source of truth for the timer state machine
  (`addAndStart/pause/resume/addMinute/markFinished/cancel/rescheduleAllAfterBoot`), shared by
  the ViewModel, the service and the receivers so a notification action behaves like an in-app tap.
- **`ui/ChronometerService.kt`** — `specialUse` foreground service. Alive whenever the stopwatch
  or any timer is RUNNING/PAUSED; stops itself otherwise. Posts a count-up chronometer
  notification for the stopwatch (Pause/Resume, Lap, Reset) and a count-down chronometer per
  timer (Pause/Resume, +1 min, Cancel). Reconciles/cancels its own notifications each refresh.

### Scheduling, receivers, service
- **`ui/AlarmScheduler.kt`** — added `scheduleTimer`/`cancelTimer` using the privileged
  `setAlarmClock` path with `TYPE="TIMER"` and the offset ring id.
- **`ui/Receivers.kt`** — `AlarmReceiver` now handles `TYPE="TIMER"` (rings via `AlarmService`,
  then marks the timer FINISHED + refreshes notifications); `BootReceiver` re-arms RUNNING timers
  after reboot via `TimerEngine.rescheduleAllAfterBoot`; updated the `ClockRepository`
  construction for the two new DAOs.
- **`ui/AlarmService.kt`** — notification titles are now timer-aware ("Timer Finished").

### ViewModel & UI
- **`ui/ClockViewModel.kt`** — stopwatch controls delegate to the `StopwatchPrefs` hub;
  `stopwatchState`/`laps` are now derived flows; added `timers`/`timerPresets` flows, a gated
  `nowElapsed` ticker, and timer/preset operations (`addTimer`, `pause/resume/addMinute/cancel/
  dismissTimer`, `startTimerFromPreset`, `addPreset`, `deletePreset`). **Deleted** the hardcoded
  timer-finish trigger and the old single-timer in-memory machinery.
- **`MainActivity.kt`** — redesigned `TimerScreen` for multiple timers (preset chips with start +
  delete, a live per-timer list with controls, empty state, "Add timer"); added `AddTimerSheet`
  (duration picker + label + reusable tone/volume picker reusing `TonePickerRow`/`ToneTabChip`/
  `loadSystemRingtones` + "save as preset"), plus `TimerRow`, `TimerPresetChip`, and duration/
  clock formatters. Updated the repository construction for the two new DAOs. `StopwatchScreen`
  is unchanged (its flows now persist underneath). Added `LazyRow` import.

### Manifest
- **`AndroidManifest.xml`** — added `FOREGROUND_SERVICE_SPECIAL_USE` permission and declared
  `ChronometerService` (`foregroundServiceType="specialUse"` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`).

## Verification
- `./gradlew :app:assembleDebug` — **BUILD SUCCESSFUL** (KSP regenerated the Room DAO impls for
  the new entities; the `2→3` migration compiled cleanly).
- Manual device testing (start/kill/reboot scenarios, notification controls, concurrent timers,
  presets) is recommended as the next step — see the plan's Verification section.

## Notes / known limitations
- `elapsedRealtime` resets on reboot; the display base is recomputed from the persisted RTC
  target on boot and the ring is guaranteed by the re-armed AlarmManager alarm.
- Multiple timers firing at the exact same moment collapse into a single ring (the alarm audio
  engine + `AlarmService` are singletons). Reliability is preserved; simultaneous multi-ring
  fidelity is out of scope.
