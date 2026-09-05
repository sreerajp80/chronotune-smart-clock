# Alarm event history

Implements `plans/20260829_074500_alarm-event-history.md`, and follows
`change_log/20260829_074420_alarm-reliability-fixes.md`.

The app now writes down what every alarm actually did. That answers the two questions that
could not be answered before — "did it ring at all?" and "did I dismiss it in my sleep?" — and
it is also the evidence that will show which of the ten failure causes from the reliability plan
is really happening on this phone.

## What changed

### The `alarm_events` table (`data/Models.kt`, `data/Daos.kt`, `data/AppDatabase.kt`)

New `AlarmEvent` entity, database version 9 -> 10, with indexes on `alarmId` and `actualAt`.
`MIGRATION_9_10` only creates the new table and its indexes — no existing table is touched, so
an upgrade cannot lose anything.

The migration's `CREATE TABLE` is copied verbatim from the statement Room generates for the
entity. Room compares the two on open and refuses to start if they differ, so a hand-written
approximation would have been a crash waiting for the first user who upgraded rather than
reinstalled.

Events recorded: `SCHEDULED`, `CANCELLED`, `FIRED`, `SUPPRESSED_PAUSE`, `SUPPRESSED_SKIP`,
`SUPPRESSED_HOLIDAY`, `RING_FAILED`, `QUEUED`, `SNOOZED`, `DISMISSED`, `AUTO_SILENCED`,
`MISSED`, `RESCHEDULED_BOOT`, `RESCHEDULED_WATCHDOG`.

Every row carries the alarm's **base** id, so a whole morning — the first ring, each snooze, the
final dismiss — groups under one alarm even though the snooze itself rings under a different id.

### Dismiss and snooze details

This is what makes the log answer the "in my sleep?" question rather than just "did it ring?".

- **How long it had been ringing.** `AlarmService` records when each ring starts and exposes
  `ringDurationMs()`. Deliberately not cleared on stop, because dismiss and snooze both ask the
  service to stop and *then* record what happened.
- **Where it was turned off.** `dismissSource` separates the full-screen alarm screen, the
  notification shade, and auto-silence.
- **Challenge detail.** `DismissChallengePanel` now counts every answer given, wrong ones
  included, and times how long the challenge took; `onSolved` reports both. Each of the three
  challenges gained an `onWrong` callback for the count. A math challenge solved first try in
  four seconds at 05:58 reads very differently from one that took six attempts.
- **Snooze detail.** Each `SNOOZED` row carries which snooze in the chain it was, the gap it
  actually used, the mode, the allowance, and when the re-ring is due.

`AlarmEvent.looksHalfAsleep()` flags the combination the user asked about: a dismiss within
fifteen seconds while the phone was still locked. The screen is obviously on at the moment of a
dismiss — the user just tapped something — so the keyguard is the signal, not the screen. It is
a judgement call, so the UI phrases it as a question rather than a statement.

### `AlarmEventLog` — one safe entry point (`data/AlarmEventLog.kt`)

Every call site goes through this. It fills in the timestamp and the device state (screen,
keyguard, Doze, whether exact alarms are allowed), writes on its own IO scope, and swallows
every error. Two rules hold everywhere: **logging must never be able to stop an alarm**, and
**ring first, log second**. It has its own scope on purpose, so a receiver's `goAsync()` window
or an activity finishing can never decide whether an event gets written.

### Where events are written

- `AlarmScheduler` — `SCHEDULED` on every arm (with the computed trigger time) and `CANCELLED`
  on disable or delete. A snoozed re-ring is not given its own arm row: the `SNOOZED` row
  already says when it is due.
- `AlarmReceiver` — `FIRED` or the matching `SUPPRESSED_*`, plus `RING_FAILED` when the
  foreground service is refused. A new `SCHEDULED_AT` intent extra carries the due time, so the
  difference between due and actual exposes Doze deferral.
- `AlarmService` — `AUTO_SILENCED` and `QUEUED`.
- `AlarmActivity`, `ClockAppScreen`, `AlarmDismissReceiver` — `DISMISSED` with the source and
  challenge detail.
- `ActiveAlarmState.scheduleSnooze` — `SNOOZED`, so all three snooze paths record identically.
- `BootReceiver` and `WatchdogReceiver` — the two re-arm events. The watchdog only writes a row
  when it actually repaired something; a clean check is not news and would bury the log.

### Missed-alarm detection (`data/AlarmEventAnalysis.kt`, `ui/ClockViewModel.kt`)

Because every arm writes a row, a missed alarm is an arm record whose time has passed with no
ring recorded near it. `findMissedArmRecords` is a pure function, and most of it is the cases
that must **not** be reported: a deliberate suppression, an alarm switched off before it fell
due, an arm record superseded by a later edit, and a miss already recorded once. Telling someone
their alarm failed when it did not would make the whole history worthless.

The check runs at start-up, records a `MISSED` row for each one found, and surfaces the newest
through `ClockViewModel.missedAlarm`. It also prunes rows past the 90-day retention window.

### The history screen (`HistoryScreen.kt`, `SettingsScreen.kt`)

Reached from Settings, using the same `openSection` pattern as `HolidaysScreen` so no navigation
library was needed.

- A last-7-days strip: rang / did not ring / snoozed / typical delay.
- Filter chips: **All** (default), Did not ring, Rang, Snoozed, Dismissed, Skipped, System.
  "All" really does mean all — the quieter system rows (`SCHEDULED`, `CANCELLED`, and the two
  re-arm events) are included, just styled more softly so the important rows still stand out.
- Rows grouped by day (TODAY / YESTERDAY / the date), each with a coloured dot, a plain-English
  one-liner, and the half-asleep flag where it applies.
- Tapping a row expands it into everything recorded: due time, delay, ring duration, dismiss
  source, all challenge fields, all snooze fields, phone state, and the note.
- Export writes a plain-text file through a save-as dialog (plain text on purpose — this file
  exists to be read by a person), and Clear empties the log behind a confirmation.

### The missed-alarm banner (`AlarmsScreen.kt`, `ClockAppScreen.kt`)

A red banner at the top of the alarms screen naming the alarm and the time it should have rung,
with "See why" (opens the history) and "Hide". Dismissing it records which miss was seen, so it
does not reappear for the same one. `SettingsScreen` gained a `startOnHistory` flag so "See why"
lands directly on the history page.

## Files changed

- `data/Models.kt` — `AlarmEvent` entity and its constants
- `data/Daos.kt` — `AlarmEventDao`
- `data/AppDatabase.kt` — version 10 and `MIGRATION_9_10`
- `data/AlarmEventLog.kt` (new)
- `data/AlarmEventAnalysis.kt` (new)
- `data/repository/ClockRepository.kt`
- `data/BackupManager.kt` — `formatAlarmEventLog`
- `ui/AlarmScheduler.kt`, `ui/Receivers.kt`, `ui/AlarmService.kt`, `ui/AlarmActivity.kt`
- `ui/ClockViewModel.kt`
- `DismissChallenge.kt`, `AlarmRingingOverlay.kt`
- `HistoryScreen.kt` (new), `SettingsScreen.kt`, `AlarmsScreen.kt`, `ClockAppScreen.kt`
- `AppPrefs.kt`
- `app/src/test/.../AlarmEventTest.kt` (new), `app/src/test/.../AlarmEventLogTest.kt` (new)

## Testing

`:app:testDebugUnitTest` — 72 tests, all passing. `:app:assembleDebug` succeeds.

- `AlarmEventTest` covers the missed rule (rang on time, rang a few minutes late, rang hours
  later, each suppression, cancelled before due, superseded by an edit, already reported, and
  one alarm's ring not covering another's miss), the half-asleep flag, the summary lines, and
  the duration formatting.
- `AlarmEventLogTest` (Robolectric) writes to a real Room database: an event is stored and
  stamped, recording never throws, and pruning drops only rows past the cutoff.

## Deviations from the plan

- **A `QUEUED` event was added** beyond the planned list, to record the second-alarm case the
  reliability change introduced. Without it a queued ring would appear in the log as a gap.
- **No new string resources.** As in the previous change, the screens this touches use
  hardcoded English throughout, so the new UI follows the surrounding code rather than being the
  only localised part of it. Worth one pass over the whole app later.
- **No migration test.** Testing a Room migration needs `androidx.room:room-testing`, which is
  not in the version catalogue, and the project keeps its dependency list trimmed. Instead the
  migration SQL is copied verbatim from Room's own generated statement, which removes the class
  of error a migration test would catch. Adding `room-testing` is worth considering separately.

## Not yet verified on a device

Everything is compile- and unit-tested only. What needs a real phone: upgrading an existing
install (the v9 -> v10 migration on a populated database), a full morning recorded end to end
(ring, several snoozes, a challenge dismiss), the export dialog, and a genuine missed alarm
producing the banner.
