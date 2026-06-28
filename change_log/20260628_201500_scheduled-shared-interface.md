# Shared `Scheduled` interface for Alarm / MusicSchedule

Implements plan `plans/20260628_201500_scheduled-shared-interface.md`.

## What changed

`app/src/main/java/in/sreerajp/chronotune_smart_clock/data/Models.kt`:

- Added a `Scheduled` interface declaring `hour`, `minute`, `daysOfWeek` and providing
  `getFormattedTime(is24Hour)` and `getRepeatDaysList()` as default methods (moved verbatim from
  the previous duplicated implementations).
- `Alarm` now implements `Scheduled`; its constructor `hour` / `minute` / `daysOfWeek` are marked
  `override val`, and its two duplicated helper methods were removed.
- `MusicSchedule` now implements `Scheduled`; same `override val` change, and its two duplicated
  helper methods were removed. Its other helpers (`getAmbientTracks`, `getCustomFiles`,
  `describeTracks`) are unchanged.

No call sites changed — `getFormattedTime` / `getRepeatDaysList` resolve via the inherited default
methods in `AlarmsScreen`, `MusicSchedulerScreen`, `ClockViewModel`, and `AlarmScheduler`.

## Note on the magic timer id

The plan's second item (magic timer id `99999`) required no work: it had already been removed in a
prior change. Timer ringing uses `TimerItem.RING_ID_OFFSET = 200000`; `99999` survives only in
historical plan/change-log markdown.

## Verification

- `./gradlew :app:compileDebugKotlin` — succeeds (only unrelated JVM native-access warnings).
