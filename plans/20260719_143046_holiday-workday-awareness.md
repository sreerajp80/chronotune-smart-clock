# Holiday / work-day awareness for alarms

**Status:** completed

## The issue

Alarms today only know about weekdays (`daysOfWeek`), a pause window, and a one-off
"skip next" day. There is no idea of a *holiday*. So a weekday alarm still rings on
Diwali, on a public holiday, or on a company shutdown day. The user must remember to
turn it off by hand every time.

The user wants this to be a **per-alarm** choice with three settings:

1. **All days** — ring on every selected day, ignore holidays. (Today's behaviour.)
2. **Skip holidays** — do not ring on a day marked as a holiday.
3. **Work days only** — do not ring on a holiday, *and* do ring on a day that is marked
   as a working day even if that weekday is not selected. This covers the Indian
   "compensatory working Saturday" case, where an office declares a normally-off day
   to be a work day.

## What already exists that we reuse

`nextTriggerTime()` in [ScheduleTiming.kt](../app/src/main/java/in/sreerajp/chronotune_smart_clock/data/ScheduleTiming.kt)
already has an `isSkipped(cal)` helper used by the pause window and the skip-next day.
A holiday is just one more reason a candidate day is not allowed. So the scheduling
change is small — most of this plan is the data and the UI.

## Design

### Day marks are stored in their own table

A new Room table, not tied to any alarm, so one list serves every alarm:

```kotlin
@Entity(tableName = "special_days")
data class SpecialDay(
    @PrimaryKey val epochDay: Long,  // same UTC-midnight basis as Alarm.localCalendarToEpochDay
    val name: String,                // "Diwali", "Office shutdown"
    val kind: String,                // "HOLIDAY" | "WORKING_DAY"
    val source: String = "MANUAL"    // room for a future calendar import
)
```

`epochDay` is the primary key, so adding the same date twice just replaces it.
`source` is not used yet; it is there so a later calendar-import feature can wipe and
re-add only its own rows without touching the user's manual entries.

### Per-alarm setting

One new column on `Alarm`:

```kotlin
val holidayMode: String = "ALL_DAYS"   // ALL_DAYS | SKIP_HOLIDAYS | WORKDAYS_ONLY
```

Default `ALL_DAYS` keeps every existing alarm behaving exactly as it does now.

### Scheduling

`nextTriggerTime()` gains two optional parameters and stays a pure function (no Room,
so it stays easy to unit test):

```kotlin
holidayMode: String = "ALL_DAYS",
dayKind: (Long) -> String? = { null }   // epochDay -> "HOLIDAY" | "WORKING_DAY" | null
```

Rules inside the day-scan loop:

- `ALL_DAYS` — unchanged.
- `SKIP_HOLIDAYS` — a candidate whose `dayKind` is `HOLIDAY` is rejected.
- `WORKDAYS_ONLY` — a candidate whose `dayKind` is `HOLIDAY` is rejected; a candidate
  whose `dayKind` is `WORKING_DAY` is accepted **even if its weekday is not in
  `repeatDays`**. This is the only rule that can make an alarm ring on an unselected
  weekday, and it only applies in this mode.

The existing 366-day scan limit already covers the worst case.

### Where the day map comes from at schedule time

`AlarmScheduler.scheduleAlarm()` is called from a broadcast receiver, where a blocking
Room read is not safe. Also, `AlarmReceiver` re-arms the next occurrence by rebuilding
an `Alarm` from intent extras — a whole holiday list cannot ride in an intent.

So we add a small in-memory singleton:

```kotlin
object SpecialDayRegistry {
    fun kindOf(epochDay: Long): String?   // reads a @Volatile Map<Long, String>
    suspend fun refresh(context: Context) // reloads the map from Room
}
```

- Loaded once at app start and in `BootReceiver` before rescheduling.
- Refreshed whenever the user edits the day list.
- `AlarmScheduler` reads it synchronously — no DB touch on the alarm path.
- If it has not loaded yet the map is empty, which degrades to "no holidays known",
  i.e. today's behaviour. Safe: an alarm may ring on a holiday, but will never be
  silently lost.

`holidayMode` is added to the intent extras so the receiver's re-arm keeps the mode.

### Re-scheduling when the day list changes

An alarm already scheduled for next Monday is a live `AlarmManager` pending intent.
If the user then marks that Monday as a holiday, the pending alarm would still fire.
So **every write to `special_days` must refresh the registry and then reschedule all
enabled alarms**, using the same loop `rescheduleAll()` already uses after boot.

As a backstop, `AlarmReceiver` gets a guard like the existing pause/skip guards: if an
alarm fires on a day that its mode says to skip, suppress the ring (the re-arm still
lands the next valid occurrence).

## Files to change

**New files**

- `data/SpecialDay.kt` — entity + `SpecialDayRegistry`.
- `HolidaysScreen.kt` — the manage-days UI (list, add via date picker, pick
  holiday/working-day, name, delete).

**Changed**

- `data/Models.kt` — add `holidayMode` to `Alarm`.
- `data/Daos.kt` — new `SpecialDayDao` (flow list, one-shot list, upsert, delete).
- `data/AppDatabase.kt` — register the entity + DAO, bump version 6 → 7, add
  `MIGRATION_6_7` (create `special_days`, add `holidayMode` column default `ALL_DAYS`).
- `data/ScheduleTiming.kt` — `holidayMode` + `dayKind` parameters and the rules above.
- `data/repository/ClockRepository.kt` — expose the special-day DAO.
- `ui/AlarmScheduler.kt` — pass `holidayMode` + `SpecialDayRegistry::kindOf` into
  `nextTriggerTime`; add the `HOLIDAY_MODE` intent extra.
- `ui/Receivers.kt` — read the extra on re-arm; refresh the registry in `BootReceiver`;
  add the fire-time suppression guard.
- `ui/ClockViewModel.kt` — special-day list flow, add/delete actions that refresh the
  registry and reschedule all alarms; load the registry on init.
- `AlarmsScreen.kt` — a three-choice "Holidays" selector in the add/edit alarm sheet,
  and a small badge on the alarm row when the mode is not `ALL_DAYS`.
- `SettingsScreen.kt` — entry point that opens the manage-days screen.
- `MainActivity.kt` — route to the new screen.
- `res/values/strings.xml` — new labels.
- `data/BackupManager.kt` — include `special_days` in backup/restore.
- `app/src/test/.../ExampleRobolectricTest.kt` — tests for the three modes against a
  fake `dayKind` map (holiday skipped, working Saturday picked up in `WORKDAYS_ONLY`
  but not in `SKIP_HOLIDAYS`, `ALL_DAYS` unchanged).

## Not in this plan

- **Importing holidays from the device calendar** (`READ_CALENDAR`, pick a calendar,
  scan all-day events). This is the feature that makes the list fill itself, but it
  needs a runtime permission and its own UI, so it belongs in a second plan. The
  `source` column above is the hook for it.
- Fetching holidays from a web API (needs network, region picker, API key, caching —
  not worth it once calendar import exists).
- Holiday awareness for music schedules (same mechanism could be applied later).

## Order of work

1. Entity, DAO, migration, registry.
2. `nextTriggerTime` rules + unit tests.
3. `holidayMode` column, scheduler and receiver wiring, reschedule-on-change.
4. Manage-days screen + per-alarm selector in the alarm editor.
5. Backup/restore.

Steps 1–3 are the working feature; 4 is what makes it usable.
