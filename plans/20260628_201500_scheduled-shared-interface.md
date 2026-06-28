# Shared interface for Alarm / MusicSchedule time helpers

**Status:** completed

## Issue

The task was twofold:

1. Replace the magic timer id `99999`.
2. Replace the duplicated `getFormattedTime` / `getRepeatDaysList` between `Alarm` and
   `MusicSchedule` in `Models.kt` with a shared interface.

Investigation findings:

- **Item 1 is already done.** `99999` no longer appears anywhere in source — it survives only
  in `change_log/` and `plans/` markdown. Timer ringing now uses `RING_ID_OFFSET = 200000`
  (see `TimerItem.Companion`, `Models.kt:118`).
- **Item 2 is NOT done.** `Alarm.getFormattedTime` / `Alarm.getRepeatDaysList`
  (`Models.kt:28-44`) and `MusicSchedule.getFormattedTime` / `MusicSchedule.getRepeatDaysList`
  (`Models.kt:154-170`) are identical, duplicated implementations.

## Plan for the fix

Introduce a Kotlin interface (default working name `Scheduled`) in `Models.kt` that declares the
three fields both data classes already expose (`hour`, `minute`, `daysOfWeek`) and provides the
two helpers as default interface methods:

```kotlin
interface Scheduled {
    val hour: Int
    val minute: Int
    val daysOfWeek: String

    fun getFormattedTime(is24Hour: Boolean = false): String { /* moved verbatim */ }
    fun getRepeatDaysList(): List<Int> { /* moved verbatim */ }
}
```

Then:

- `data class Alarm(...) : Scheduled` — delete its two duplicated methods (the constructor
  `val hour` / `val minute` / `val daysOfWeek` already satisfy the interface's abstract vals).
- `data class MusicSchedule(...) : Scheduled` — delete its two duplicated methods.

`MusicSchedule`'s other helpers (`getAmbientTracks`, `getCustomFiles`, `describeTracks`) stay on
the class; only the two shared methods move to the interface.

No call sites change: every existing `alarm.getFormattedTime(...)` /
`schedule.getRepeatDaysList()` etc. continues to resolve via the inherited default method. Verified
callers live in `AlarmsScreen.kt`, `MusicSchedulerScreen.kt`, `ClockViewModel.kt`,
`AlarmScheduler.kt`.

## Files to be changed

- `app/src/main/java/in/sreerajp/chronotune_smart_clock/data/Models.kt` — add `Scheduled`
  interface; make `Alarm` and `MusicSchedule` implement it; remove the two duplicated method
  bodies from each.

(No other files require edits — call sites are source-compatible.)

## Verification

- Build the module (`./gradlew :app:compileDebugKotlin` or assemble) to confirm the interface
  resolves and no call site breaks.

## Out of scope

- The `99999` magic id (already removed).
- Any change to the ring-id offset scheme.
