# Timer / Stopwatch / Backup Features

**Status:** completed

## What the user asked for

From the earlier feature-gap review, implement:

1. Timer sound: a "loop-until-dismissed vs. one-shot" choice.
2. "Add a minute" from the notification for a finished timer.
3. Stopwatch: share / export of lap times.
4. Backup / restore (export–import) of alarms & schedules.
5. World-clock city search.

## What I verified in the code first

- **City search already exists.** `WorldClockScreen.LocationSearchDialog` already has a
  search box that filters `viewModel.availableCities` by city name and region. So item 5
  is **already done** — no work needed. (If the real problem is "too few cities in the
  list", that is a different request; tell me and I will handle it separately.)
- **Timer ring already loops forever.** When a timer fires, `AlarmService` calls
  `AudioEngine.playAudio(..., durationMs = null, ...)`, which sets `isLooping = true` with
  no timeout. Same for alarms. So today the sound **never auto-stops** — there is no choice.
  The missing piece is an option to auto-silence (the "one-shot / stop after a while" side).
- **"+1 min" already exists — but only while the timer is running.**
  `ChronometerService.buildTimerNotification` shows a `+1 min` action. The **finished-timer
  ring** notification is built by `AlarmService.buildNotification` and only has `Dismiss`.
  So "+1 min from the ring" is the real gap.

## The plan

### Feature A — Ring auto-silence (loop vs. one-shot choice) — **per-alarm + global default**

Add an **"Auto-silence after"** choice with values *Never (ring until dismissed)*, *1 min*,
*5 min*, *10 min*, *15 min*. "Never" keeps today's behavior; any other value makes the ring
stop on its own after that time (the "one-shot" side of the choice).

Two levels, following the exact pattern the app already uses for **default snooze length**
and **default tone** (a global default that seeds new items; each item then keeps its own
value):

- **Per alarm** — each alarm stores its own auto-silence value and the user edits it in the
  alarm sheet.
- **Global default** — a setting that pre-fills the value when a *new* alarm is created.
  Changing the global default later does **not** touch existing alarms (same as snooze/tone
  today).
- **Timers** use the global default value at ring time (timers have no per-item editor for
  this, to keep scope in check). Music schedules are unaffected — they already stop after
  their configured duration.

Implementation:

- `data/Models.kt` — add `autoSilenceMinutes: Int = 0` to the `Alarm` entity (`0` = Never).
- `data/AppDatabase.kt` — bump DB version `4 → 5` and add `MIGRATION_4_5`:
  `ALTER TABLE alarms ADD COLUMN autoSilenceMinutes INTEGER NOT NULL DEFAULT 0`
  (existing alarms keep "Never", preserving today's behavior).
- `AppPrefs.kt` — new global pref `defaultAutoSilenceMinutes` (default `0`) with `StateFlow`,
  getter/setter, a cold-read `getDefaultAutoSilenceMinutes(context)` helper, and an
  `AUTO_SILENCE_CHOICES` list (mirrors `SNOOZE_CHOICES`).
- `AlarmsScreen.kt` — the alarm edit sheet gets an **"Auto-silence after"** chip row
  (Never / 1 / 5 / 10 / 15 min), initialized from the alarm's own value when editing, or from
  `defaultAutoSilenceMinutes` for a new alarm; saved onto the `Alarm`.
- `SettingsScreen.kt` — a new **"Default auto-silence"** card on the **Alarm** settings tab
  (chip row like the snooze card) that sets the global default, with a one-line note: applies
  to new alarms and to timers; existing alarms keep their own value.
- Plumb the value through the firing path (it must survive a cold process / reboot / snooze):
  - `AlarmScheduler.scheduleAlarm` — `putExtra("AUTO_SILENCE_MIN", alarm.autoSilenceMinutes)`.
  - `AlarmScheduler.scheduleTimer` — `putExtra("AUTO_SILENCE_MIN", getDefaultAutoSilenceMinutes)`.
  - `Receivers.kt` `AlarmReceiver` — read the extra, carry it into `ActiveAlarm`, and include
    it when it rebuilds the `Alarm` in `rescheduleNextOccurrence` (repeating alarms).
  - `ActiveAlarmState.ActiveAlarm` — new `autoSilenceMinutes` field; `snooze()` /
    `scheduleSnooze()` carry it onto the snoozed re-ring; `AlarmSnoozeReceiver` and the
    `AlarmService` snooze extras carry it too.
  - `AlarmService` — new `EXTRA_AUTO_SILENCE`; in `onStartCommand`, after `startForeground`,
    if the value is > 0 post a delayed self-stop
    (`Handler(mainLooper).postDelayed({ stopAlarmAndSelf() }, minutes*60_000)`), cancelling
    it in `stopAlarmAndSelf`/`onDestroy`. This tears down audio + notification + foreground
    state together. Best-effort: if the OS kills the process first, playback stops anyway.

### Feature B — "+1 min" on the finished-timer ring

- `AlarmService.buildNotification` — when `alarm.type == "TIMER"`, add a **"+1 min"** action
  (next to Dismiss) that broadcasts to a new `TimerAddMinuteReceiver`.
- `Receivers.kt` — new `TimerAddMinuteReceiver`: stops the current ring
  (`AlarmService.stopIntent`), then (using `goAsync` + repo) restarts that timer running for
  60 seconds via a new `TimerEngine.restartForOneMinute(repo, context, id)` helper (sets the
  FINISHED timer back to RUNNING with a 60s target and re-arms the AlarmManager ring +
  refreshes the live notification). The timer id is carried on the ring intent so the
  receiver knows which one.
- `AlarmService` — carry the underlying `timerId` (the pre-offset id) through
  `startIntent`/extras so the action can target the right timer. `AlarmReceiver` already has
  `TIMER_ID`; thread it into the `ActiveAlarm` start path.
- `TimerEngine.kt` — add `restartForOneMinute(...)`.

### Feature C — Stopwatch: share / export laps

- `StopwatchScreen.kt` — add a **Share** icon button in the header (next to Settings),
  enabled only when `laps` is not empty. On tap, build a plain-text summary (title, total
  time, and each `Lap N — elapsed (+delta)` line using the same formatting already in the
  screen) and fire a `Intent.ACTION_SEND` chooser (`type = "text/plain"`). No new
  permissions needed. This covers "share to any app" and "export" (user can send to Files,
  email, notes, etc.).

### Feature D — Backup & restore (export / import)

A user-triggered JSON backup of all app data, written to / read from a file the user picks
via the system document picker (Storage Access Framework — no storage permission needed).

- New file `data/BackupManager.kt`:
  - `exportToJson(repo): String` — reads alarms, world clocks, music schedules, timer presets
    (one-shot reads via `...Once`/`first()`) into a versioned JSON object
    (`{ "version": 1, "exportedAt": ..., "alarms": [...], "worldClocks": [...],
    "musicSchedules": [...], "timerPresets": [...] }`). Running timers are intentionally
    **not** exported (they are transient countdowns).
  - `importFromJson(repo, json, mode): ImportResult` — parses and inserts rows. Mode
    **Merge** (add to existing) or **Replace** (clear those tables first, then insert).
    Ignores primary-key ids on insert so Room auto-assigns fresh ones and there are no
    collisions. Returns counts for a confirmation toast.
  - After a successful import, re-arm everything by calling `AlarmScheduler` for each enabled
    alarm / music schedule (same pattern as `BootReceiver.rescheduleAll`).
- `ClockRepository.kt` — add any missing one-shot read helpers needed (e.g.
  `getAllAlarmsOnce`, `getAllWorldClocksOnce`, `getAllMusicSchedulesOnce`,
  `getAllTimerPresetsOnce`) and simple `deleteAll*` calls used by Replace. Add matching DAO
  methods in `Daos.kt`.
- `ClockViewModel.kt` — `exportBackup(uri)` and `importBackup(uri, mode)` that do the file
  read/write on `Dispatchers.IO` via `contentResolver.openInputStream/openOutputStream`, and
  expose a small result callback / `StateFlow` for the UI toast.
- `SettingsScreen.kt` — a new **"Backup & restore"** card. Because Settings currently only
  gets `isDark`/`onToggleTheme`, I will pass the `ClockViewModel` (already created in
  `MainActivity`) down through `ClockAppScreen` → `SettingsScreen` so the buttons can call
  the export/import functions. Two buttons:
  - **Export** → `ActivityResultContracts.CreateDocument("application/json")`, default name
    like `chronotune-backup-YYYYMMDD.json`.
  - **Import** → `ActivityResultContracts.OpenDocument(["application/json"])`, then a small
    dialog to choose **Merge** or **Replace** before applying.
  A one-line note: what is included (alarms, world clocks, music schedules, timer presets)
  and that running timers are not part of a backup.

## Files to be changed / added

**Changed**
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/data/Models.kt` (A — new `Alarm` column)
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/data/AppDatabase.kt` (A — v4→v5 migration)
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/AppPrefs.kt` (A — global default pref)
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmsScreen.kt` (A — per-alarm chip)
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmScheduler.kt` (A — carry extra)
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmService.kt` (A, B)
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/Receivers.kt` (A, B)
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/TimerEngine.kt` (B)
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/StopwatchScreen.kt` (C)
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/SettingsScreen.kt` (A, D)
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ClockAppScreen.kt` (D — pass ViewModel)
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ClockViewModel.kt` (D)
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/data/repository/ClockRepository.kt` (A backup helpers, D)
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/data/Daos.kt` (D)
- `app/src/main/AndroidManifest.xml` (B — register `TimerAddMinuteReceiver`)

**Added**
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/data/BackupManager.kt` (D)

## Notes / decisions

- No new Android permissions. SAF document picker handles file access; backup file is chosen
  by the user each time.
- **Room schema change:** DB version `4 → 5` with a non-destructive `MIGRATION_4_5` that adds
  one `alarms` column (existing data preserved). The backup JSON gains the new field too; an
  older backup that lacks it imports fine (missing = `0` = Never).
- Item 5 (city search) is already implemented; nothing to do unless you meant "add more
  cities".

## Out of scope (unless you ask)
- Per-timer (rather than global-default) auto-silence.
- Cloud / automatic scheduled backups.
- Exporting running timers or stopwatch state.
