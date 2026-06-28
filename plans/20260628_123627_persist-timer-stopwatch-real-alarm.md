# Persist Stopwatch & Timer + make the Timer a real alarm

**Status:** completed

## The issues

Both the stopwatch and the timer live **only as in-memory state inside
[ClockViewModel.kt](app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ClockViewModel.kt)**:

- Stopwatch: `stopwatchBaseTime`/`stopwatchAccumulated` + a `viewModelScope` ticker, all
  keyed off `System.currentTimeMillis()` (wall clock — drifts if the user changes the time).
- Timer: `timerJob` counts down off `System.currentTimeMillis()`, and at zero it calls
  `ActiveAlarmState.triggerAlarm(...)` directly with a **hardcoded** alarm:
  [ClockViewModel.kt:370](app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ClockViewModel.kt#L370)
  — magic id `99999`, fixed tone `"Cosmic Shimmer"`, fixed volume `0.8`.

Consequences:
1. **Process death / screen-off loses everything.** If the OS reaps the process (or the
   `viewModelScope` is torn down), a running timer/stopwatch silently vanishes. There is no
   foreground service, no persisted base, no notification.
2. **The timer only rings if the app is still alive.** Because the alert is fired in-process
   from the countdown coroutine, a backgrounded/Dozed app never rings. It also ignores the
   user's tone/volume entirely.
3. **Only one timer at a time**, no presets.

## Goals (from the request)

**Feature A — Persist Stopwatch & Timer across process death.**
Back them with a foreground service + `SystemClock.elapsedRealtime()` base stored in prefs,
and show a live notification with controls.

**Feature B — Make the Timer a real alarm.**
Schedule the timer ring through `AlarmScheduler` (exact `setAlarmClock`) so it rings even
when backgrounded/Dozed/killed, let the user pick the timer tone + volume, and support
**multiple concurrent timers** plus **named presets** (e.g. "Tea 3 min", "Workout").

## Design overview

### Time base (Feature A core)
- **Stopwatch & on-screen countdown** use `SystemClock.elapsedRealtime()` (monotonic, immune
  to wall-clock changes) as the base, persisted in prefs/DB. Display is always *derived* from
  the stored base, so a restored process recomputes the correct value instantly.
- **Timer ring scheduling** additionally stores a wall-clock fire time
  (`System.currentTimeMillis() + remaining`) because `AlarmManager` works in RTC and because
  `elapsedRealtime` resets on reboot. The on-screen countdown uses `elapsedRealtime`; the
  alarm uses RTC.

### Foreground service: `ChronometerService`
A new foreground service that is alive whenever the stopwatch is RUNNING **or** any timer is
RUNNING, and stops itself when neither is true. It:
- Posts a **live ongoing notification** for the stopwatch (count-up via
  `NotificationCompat` chronometer using a `setWhen` anchored to `elapsedRealtime`) with
  actions **Pause/Resume**, **Lap**, **Reset**.
- Posts a **live ongoing notification per running timer** (count-down chronometer via
  `setChronometerCountDown(true)`) with actions **Pause/Resume**, **Cancel**, **+1 min**.
- Uses the system chronometer so we don't need a high-frequency ticker just to keep the
  notification fresh (a light ~1s refresh only to react to state changes is enough).
- Reacts to action intents (`ACTION_*`) that mutate the persisted state and reschedule the
  AlarmManager fire time for timers, then re-posts notifications.

Foreground service type: `specialUse` (clock/timer is not one of the predefined types),
declared in the manifest with the matching `FOREGROUND_SERVICE_SPECIAL_USE` permission.

### Timer ring path (Feature B core)
A running timer schedules an **exact alarm** through `AlarmScheduler.scheduleTimer(timer)`
(using `setAlarmClock`, the same privileged path alarms use). When it fires,
`AlarmReceiver` (extended for `TYPE = "TIMER"`) hands off to the existing `AlarmService`
(full-screen ring UI + `AudioEngine` with the timer's chosen tone/volume), marks the timer
`FINISHED` in the DB, and refreshes the `ChronometerService` notifications. This reuses the
entire existing alarm ringing/dismiss/notification stack — no second audio path.

Request-code namespacing (existing offsets: alarm `id`, music `id+10000`, snooze
`id+50000`/`id+100000`): timers use `id + 200000` for both the AlarmManager PendingIntent and
the `ID` carried into `AlarmService` (keeps the foreground-notification id unique).

### Persistence storage
- **Stopwatch** → a small `StopwatchPrefs` (SharedPreferences): state, `baseElapsed`,
  `accumulatedMs`, serialized laps. (Single instance, no need for Room.)
- **Timers & presets** → Room (consistent with Alarm/MusicSchedule), DB version `2 → 3`:
  - `TimerItem`: `id`, `label`, `totalDurationMs`, `remainingMs`, `endAtElapsed` (Long,
    SystemClock base), `fireAtWallClock` (Long, RTC), `state` (IDLE/RUNNING/PAUSED/FINISHED),
    `toneName`, `toneUri`, `volume`, `createdAt`.
  - `TimerPreset`: `id`, `label`, `durationMs`, `toneName`, `toneUri`, `volume`, `sortOrder`.
  - Migration `2→3` creates both tables and seeds default presets ("Tea" 3 min, "Workout"
    25 min, "Power Nap" 20 min). Existing `fallbackToDestructiveMigration` stays as backstop.

## Files to be changed / created

### Data layer
1. **`data/Models.kt`** — add `TimerItem` and `TimerPreset` `@Entity` data classes (+ small
   helpers: derive `remaining` from `endAtElapsed`, formatted label/duration).
2. **`data/Daos.kt`** — add `TimerDao` (CRUD + `getAll()` Flow + `getById`) and
   `TimerPresetDao` (CRUD + `getAll()` Flow).
3. **`data/AppDatabase.kt`** — register the two entities, bump `version = 3`, add
   `MIGRATION_2_3` (create tables + seed presets), expose `timerDao()` / `timerPresetDao()`.
4. **`data/repository/ClockRepository.kt`** — inject the two new DAOs; expose `allTimers`,
   `allTimerPresets` flows and CRUD/`getTimerById` suspend functions. Update the two
   construction sites:
   [MainActivity.kt:152](app/src/main/java/in/sreerajp/chronotune_smart_clock/MainActivity.kt#L152)
   and [Receivers.kt:251](app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/Receivers.kt#L251).

### Persistence helper
5. **`StopwatchPrefs.kt`** (new) — SharedPreferences-backed stopwatch state with
   save/restore of state, `baseElapsed`, `accumulatedMs`, and laps (simple serialization).

### Service + scheduling + receivers
6. **`ui/ChronometerService.kt`** (new) — the foreground service described above: builds the
   stopwatch + per-timer notifications with action buttons, owns its notification channel(s),
   handles control-action intents, and starts/stops itself based on persisted state. Provides
   `companion` `startIntent`/control-intent builders + a `refresh(context)` entry point.
7. **`ui/AlarmScheduler.kt`** — add `scheduleTimer(timer)` (exact `setAlarmClock`, carrying
   `TYPE="TIMER"`, `ID=id+200000`, label, tone, uri, volume) and `cancelTimer(timer)`.
8. **`ui/Receivers.kt`**:
   - `AlarmReceiver`: handle `TYPE == "TIMER"` — ring via `AlarmService` (already generic),
     then mark the timer `FINISHED` in DB and call `ChronometerService.refresh(...)`. Timers
     do **not** re-arm (one-shot).
   - `BootReceiver`: also re-arm RUNNING timers — recompute `fireAtWallClock`, reschedule, and
     restart `ChronometerService` (note `elapsedRealtime` resets on reboot, so display base is
     recomputed from the persisted remaining/RTC target).

### ViewModel
9. **`ui/ClockViewModel.kt`**:
   - **Stopwatch**: rewrite `start/pause/reset/recordLap` to read/write `StopwatchPrefs` using
     `elapsedRealtime`, keep a UI ticker that *derives* display from the stored base, and
     start/stop `ChronometerService`. Restore on `init`.
   - **Timer**: replace the single in-memory timer with a `timers` `StateFlow` sourced from
     the repository. Add `addTimer(durationMs, label, tone, uri, volume)`,
     `startTimer(id)`/`pauseTimer(id)`/`resumeTimer(id)`/`addMinute(id)`/`cancelTimer(id)`/
     `dismissFinishedTimer(id)`; each mutates the DB, (re)schedules/cancels via
     `AlarmScheduler`, and refreshes `ChronometerService`. **Delete the hardcoded
     `triggerAlarm(99999, "Cosmic Shimmer", …)` block** — ringing now goes through the alarm
     path. Keep a single UI ticker that recomputes each running timer's remaining from
     `endAtElapsed`.
   - **Presets**: expose `timerPresets` flow + `addPreset`, `deletePreset`,
     `startTimerFromPreset(preset)`.

### UI
10. **`MainActivity.kt`**:
    - **`TimerScreen`** redesigned for multiple timers: a **presets row** (chips: tap to
      start, "＋" to save current/new preset, long-press a chip to delete), a **list of
      active/finished timers** (each: countdown ring/row + Pause/Resume, +1 min, Cancel; a
      finished timer shows Dismiss), and an **"Add timer"** entry opening a setup sheet
      (H:M:S picker + label + **tone + volume picker** + optional "Save as preset").
    - **Tone + volume picker**: extract a compact reusable `ToneVolumePickerSheet` composable
      that reuses the existing low-level helpers `loadSystemRingtones`, `ToneItem`,
      `TonePickerRow`, `ToneTabChip` (built-in / ringtones / from-file + preview + a volume
      slider). Used by the timer setup sheet. (AddAlarmDialog left untouched to limit blast
      radius.)
    - **`StopwatchScreen`**: unchanged UI; it already binds to the ViewModel flows, which now
      persist underneath.
    - Ensure `POST_NOTIFICATIONS` is requested (already handled at
      [MainActivity.kt:182](app/src/main/java/in/sreerajp/chronotune_smart_clock/MainActivity.kt#L182)).

### Manifest
11. **`AndroidManifest.xml`** — add
    `FOREGROUND_SERVICE_SPECIAL_USE` permission, declare `ChronometerService`
    (`foregroundServiceType="specialUse"` + the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` meta-data),
    and declare any new control `BroadcastReceiver`s (if controls are routed via receivers
    rather than `Service.onStartCommand`).

## Edge cases & decisions
- **Reboot**: `elapsedRealtime` resets, so on boot the display base is recomputed from the
  persisted `remaining` + RTC target; the ring is guaranteed by the re-armed AlarmManager
  alarm. Stopwatch base is similarly re-anchored from `accumulatedMs` (a stopwatch that was
  RUNNING across a reboot resumes counting from its accumulated value).
- **Concurrent rings**: timer ids are offset by `200000` so the `AlarmService` foreground
  notification id and PendingIntents never collide with alarms/music/snooze.
- **DB growth**: FINISHED/idle timers are deletable from the UI; `addTimer` for an immediate
  run can also auto-clean dismissed entries. Presets are durable.
- **Permissions**: exact-alarm capability already handled by the existing
  `canScheduleExactAlarms()` fallback in `AlarmScheduler`.

## Verification
- Build: `./gradlew assembleDebug` (KSP must regenerate the Room DAO impls for the new
  entities + migration).
- Manual:
  1. Start stopwatch → background the app / turn screen off → notification keeps live count;
     Pause/Lap/Reset from the notification work; reopen app → UI matches.
  2. Start a 1-min timer → force-stop the app from settings → it still rings at zero with the
     chosen tone/volume via the full-screen alarm UI; Dismiss/Snooze stack works.
  3. Run two timers concurrently → both count down and ring independently.
  4. Save "Tea 3 min" preset, start it from the chip; delete a preset.
- Confirm no remaining reference to the hardcoded id `99999` / inline `"Cosmic Shimmer"`
  trigger.

## Rollout note
This is a sizable, multi-layer change (DB migration + new service + scheduler + ViewModel +
a redesigned Timer screen). If you'd prefer, I can implement it in two approval-separated
phases — **Phase 1: Feature A** (persistence + foreground service for the *existing* single
timer & stopwatch) and **Phase 2: Feature B** (real-alarm ring + multiple timers + presets +
tone/volume picker) — but the plan above covers the full scope in one pass.
