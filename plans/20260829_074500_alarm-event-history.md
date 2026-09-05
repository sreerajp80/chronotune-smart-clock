# Alarm event history

**Status:** completed

## What the problem is

There is no record anywhere of what an alarm actually did. Nothing is written when an alarm
is scheduled, when it rings, when it is suppressed, when it is snoozed, or when it is
dismissed. The only trace is `Log.d`, which is gone as soon as the phone is rebooted and is
invisible to the user.

Two real questions cannot be answered today:

1. **Did the alarm ring at all?** A missed alarm and a normal quiet morning look identical.
2. **Did I dismiss it in deep sleep?** If the alarm did ring and was dismissed after two
   seconds at 05:58, the user has no way to know that.

This also blocks the reliability work: the companion plan
(`20260829_074410_alarm-reliability-fixes.md`) lists ten possible causes of a lost ring, and
without a log we cannot tell which one is actually happening on this phone.

## Files to be changed

| File | Change |
| --- | --- |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/data/Models.kt` | New `AlarmEvent` entity and its event-type constants |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/data/Daos.kt` | New `AlarmEventDao` |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/data/AppDatabase.kt` | Database version 9 -> 10, new entity, `MIGRATION_9_10` |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/data/repository/ClockRepository.kt` | Event flows, insert and prune helpers |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/data/AlarmEventLog.kt` | **New.** The single, always-safe entry point every caller uses to record an event |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmScheduler.kt` | Record `SCHEDULED` / `CANCELLED` |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/Receivers.kt` | Record `FIRED`, the three `SUPPRESSED_*` events, `SNOOZED`, `DISMISSED_NOTIFICATION`, `RESCHEDULED_BOOT`, `RESCHEDULED_WATCHDOG` |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmService.kt` | Record `AUTO_SILENCED` and `RING_FAILED`; carry the ring start time |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/AlarmActivity.kt` | Record `DISMISSED_APP` / `SNOOZED` with the details below |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmRingingOverlay.kt` | Report challenge attempts and how the dismiss happened |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/DismissChallenge.kt` | Report solved / failed attempt counts |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/HistoryScreen.kt` | **New.** The history UI |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/SettingsScreen.kt` | Entry point to the history screen, same pattern as `HolidaysScreen` |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/AlarmsScreen.kt` | "Missed alarm" banner at the top |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ClockViewModel.kt` | Event flow, missed-alarm detection, clear / export actions |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/AppPrefs.kt` | Retention length, and the last missed-alarm check time |
| `app/src/main/java/in/sreerajp/chronotune_smart_clock/data/BackupManager.kt` | Export the log to a text file |
| `app/src/main/res/values/strings.xml` | New strings |
| `app/src/main/res/values-ml/strings.xml` | Malayalam for the same strings |
| `app/src/test/java/in/sreerajp/chronotune_smart_clock/AlarmEventTest.kt` | **New.** Missed detection and formatting tests |

## The plan

### 1. The `alarm_events` table

```kotlin
@Entity(tableName = "alarm_events")
data class AlarmEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // Which schedule this belongs to. Always the ORIGINAL alarm id, never a snooze or
    // timer-ring offset id, so a whole morning groups together.
    val alarmId: Int,
    val type: String,          // ALARM | MUSIC | TIMER
    val label: String,

    val event: String,         // see the list below
    val scheduledAt: Long,     // when it was supposed to happen (0 when not applicable)
    val actualAt: Long,        // when it really happened (System.currentTimeMillis())

    // --- ring details ---
    // How long the alarm was sounding before this event. 0 for non-ring events.
    val ringDurationMs: Long = 0,

    // --- dismiss details ---
    // FULL_SCREEN | NOTIFICATION | AUTO_SILENCE | REPLACED | NONE
    val dismissSource: String = "NONE",
    val challengeType: String = "NONE",     // NONE | MATH | PHRASE | MEMORY
    val challengeDifficulty: String = "",
    val challengeRounds: Int = 0,           // rounds required
    val challengeAttempts: Int = 0,         // taps / answers made, including wrong ones
    val challengeSolvedMs: Long = 0,        // time from opening the challenge to solving it

    // --- snooze details ---
    val snoozeIndex: Int = 0,               // 1 = first snooze of this ring
    val snoozeGapMinutes: Int = 0,          // the gap this snooze used
    val snoozeMode: String = "",            // FIXED | PROGRESSIVE
    val snoozeLimit: Int = 0,               // 0 = unlimited
    val nextRingAt: Long = 0,               // when the snoozed ring is due

    // --- device state, for diagnosing a missed ring ---
    val screenOn: Boolean = false,
    val deviceLocked: Boolean = false,
    val dozeIdle: Boolean = false,
    val exactAllowed: Boolean = true,       // canScheduleExactAlarms() at that moment

    val detail: String = ""                 // free text: exception message, holiday name, ...
)
```

Event names, as constants on a companion object:

| Event | When it is written |
| --- | --- |
| `SCHEDULED` | every successful arm, with the computed trigger time in `scheduledAt` |
| `CANCELLED` | alarm disabled or deleted |
| `FIRED` | `AlarmReceiver` decided this ring goes ahead |
| `SUPPRESSED_PAUSE` | inside the pause window |
| `SUPPRESSED_SKIP` | the skip-next day |
| `SUPPRESSED_HOLIDAY` | holiday mode said no |
| `RING_FAILED` | the foreground service or the full-screen intent could not start |
| `SNOOZED` | user snoozed, from the screen or the notification |
| `DISMISSED_APP` | dismissed on the full-screen alarm UI |
| `DISMISSED_NOTIFICATION` | dismissed from the notification action |
| `AUTO_SILENCED` | the auto-silence timer stopped the ring |
| `MISSED` | written by the check in step 5 |
| `RESCHEDULED_BOOT` | re-armed by `BootReceiver` |
| `RESCHEDULED_WATCHDOG` | re-armed by the watchdog from the reliability plan |

Indexes on `alarmId` and on `actualAt`, because the screen sorts by time and the missed
check queries by alarm.

Migration `MIGRATION_9_10` is a plain `CREATE TABLE IF NOT EXISTS` plus the two indexes. No
existing table is touched, so nothing can be lost on upgrade.

### 2. `AlarmEventLog` - one safe entry point

A small object with `fun record(context: Context, event: AlarmEvent)`. It:

- fills in `actualAt`, `screenOn`, `deviceLocked`, `dozeIdle` and `exactAllowed` itself, so
  no caller has to remember them,
- writes on `Dispatchers.IO` through `AppDatabase.getDatabase(...)`,
- swallows every exception.

**Logging must never be able to stop an alarm.** So the rule for every call site is: fire the
ring first, log afterwards, and never let a log write hold up `goAsync()` beyond what the
receiver already does.

### 3. Where each event is written

- `AlarmScheduler.scheduleAlarm` / `scheduleMusic` / `scheduleTimer` -> `SCHEDULED`, with the
  computed `calendar.timeInMillis` as `scheduledAt`. This is what makes missed detection
  possible, and it also shows an alarm armed for the wrong day *before* it goes wrong.
- `AlarmScheduler.cancelAlarm` / `cancelMusic` -> `CANCELLED`.
- `AlarmReceiver.handleFiring` -> `FIRED` or the matching `SUPPRESSED_*`, written before the
  hand-off to `AlarmService`. The `scheduledAt` value rides in a new intent extra put there
  by the scheduler, so `actualAt - scheduledAt` gives the real delay caused by Doze.
- `AlarmService` -> `AUTO_SILENCED` (with `ringDurationMs`), and `RING_FAILED` when both
  `startActivity` and `fsi.send()` throw. The service records the ring start time so every
  later event can report how long the alarm had been sounding.
- `AlarmDismissReceiver` -> `DISMISSED_NOTIFICATION`, `dismissSource = NOTIFICATION`.
- `AlarmActivity` dismiss -> `DISMISSED_APP`, `dismissSource = FULL_SCREEN`, plus the
  challenge fields.
- `AlarmSnoozeReceiver` and the snooze path in `AlarmActivity` -> `SNOOZED`, with
  `snoozeIndex`, `snoozeGapMinutes`, `snoozeMode`, `snoozeLimit` and `nextRingAt`.
- `BootReceiver` -> `RESCHEDULED_BOOT`; the watchdog -> `RESCHEDULED_WATCHDOG`.

All of these use the **base** alarm id. `AlarmService` and the receivers already carry enough
to work it out; where they do not, the `BASE_ID` extra added in the reliability plan supplies
it. If that plan is not implemented first, this plan adds the same extra itself.

### 4. Dismiss and snooze details in more depth

This is the part that answers "did I dismiss it in my sleep?".

- **Ring duration.** `AlarmService` stores the moment the ring started. Every dismiss or
  snooze event carries `ringDurationMs`. A dismiss two seconds into a ring, on a locked
  screen, at 05:58, is a very different story from a dismiss after four minutes.
- **How it was dismissed.** `dismissSource` separates the full-screen screen from the
  notification action and from auto-silence. Reaching for the notification shade is a
  different act from tapping the big button on the alarm screen.
- **Challenge detail.** `DismissChallenge` currently only calls `onSolved`. It gains an
  `onProgress(attempts: Int)` style callback so the overlay can count how many answers were
  given and how long the challenge took. A challenge solved on the first try in four seconds
  at 05:58 is strong evidence of a half-asleep dismiss; the same challenge with six wrong
  attempts is not.
- **Snooze chain.** Each `SNOOZED` row carries which snooze in the chain it was, the gap it
  used, the mode, the limit, and when the next ring is due. The history screen can then show
  a morning as one story: rang 06:00, snoozed 4 times, dismissed 06:47 after 12 seconds.

### 5. Missed-alarm detection

Because every arm writes a `SCHEDULED` row, a missed alarm is a `SCHEDULED` row whose
`scheduledAt` is in the past with no `FIRED`, `SUPPRESSED_*` or `CANCELLED` row for the same
alarm within a small window (say 5 minutes) around it.

The check runs:

- on app start, from `ClockViewModel.init`,
- and again from the watchdog once the reliability plan lands.

Every miss it finds gets a `MISSED` row written, so it is found only once, and
`AppPrefs` keeps the time of the last check.

The result surfaces in two places:

- a dismissible banner at the top of `AlarmsScreen`: *"Yesterday 06:00 - Wake up - did not
  ring. Tap to see why."*, opening the history screen,
- the history screen itself, with the device-state fields on the missed row showing whether
  the phone was in Doze and whether exact alarms were allowed.

### 6. The history screen

A new `HistoryScreen.kt`, opened from Settings using the same `openSection` pattern that
`HolidaysScreen` already uses (SettingsScreen.kt line 545), so no navigation library is
needed.

Layout:

- **Summary strip at the top**, last 7 days: rang / missed / snoozed / average delay between
  scheduled and actual.
- **Filter chips**: All, Missed, Rang, Snoozed, Dismissed, Suppressed, System. "All" is the
  default and shows **every** event type in the table above, including `SCHEDULED`,
  `CANCELLED`, `RESCHEDULED_BOOT` and `RESCHEDULED_WATCHDOG`, so nothing is hidden from the
  user.
- **Grouped by day**, newest first, with a sticky date header.
- **One row per event**: time, alarm label, an icon and colour for the outcome, and a one
  line summary, for example:
  - `06:00  Wake up   Rang  (3 s late)`
  - `06:05  Wake up   Snoozed 1 of 3, 5 min, next 06:10`
  - `06:47  Wake up   Dismissed on alarm screen after 12 s - math challenge, 1 attempt, 4 s`
  - `06:00  Wake up   Did not ring - phone was in Doze, exact alarms not allowed`
- **Tap a row to expand** it into the full detail: scheduled time, actual time, delay, ring
  duration, dismiss source, all challenge fields, all snooze fields, device state and the
  `detail` text.
- **Overflow menu**: "Export log" (a plain text file written through `BackupManager`, so a bad
  night can be shared) and "Clear history".

Rows for suppressed and system events are shown in a quieter style, so the important ones
still stand out while everything remains visible.

### 7. Retention

Keep 90 days by default, with the length in `AppPrefs`. Pruning runs at app start next to the
existing `pruneSpecialDaysBefore` call, using the same best-effort try/catch style.

## Testing

- Unit tests for the missed-alarm rule: a `SCHEDULED` with a matching `FIRED` is not missed; a
  `SCHEDULED` with a `SUPPRESSED_HOLIDAY` is not missed; a lone past `SCHEDULED` is missed;
  the same miss is not reported twice.
- Unit tests for the one-line summary text of each event type.
- Robolectric test that `AlarmEventLog.record` swallows a database failure and does not throw.
- Manual: ring an alarm, snooze it three times, dismiss it with a math challenge, then check
  the history screen shows the whole chain with the right durations and attempt counts.

## Order of work

Steps 1, 2 and 3 first, so events start being collected. Then step 4 (the extra detail
fields), then 5 and 6 (missed detection and the screen), then 7. The screen is worth building
only once a few days of real events exist, so the first three steps should ship first even if
the UI follows later.

## Note on dependencies

This plan assumes the reliability plan lands first, mainly for the `BASE_ID` extra and the
watchdog. If it does not, this plan still works: it adds the `BASE_ID` extra itself and simply
never writes `RESCHEDULED_WATCHDOG` rows.
