# Import holidays from the device calendar

Implements `plans/20260719_175513_holiday-calendar-import.md`.

## What this adds

An **Import from calendar** button on the Holidays screen. The user picks a calendar (usually
a subscribed holiday calendar like "Holidays in India"), and its all-day events become holiday
days. The holiday list no longer has to be typed in by hand.

## Files changed

**New**

- `data/CalendarHolidayImporter.kt` — lists calendars, reads all-day events over the import
  window, and maps them to `SpecialDay` rows. The mapping is a pure function over a plain
  `RawEvent` type, deliberately separate from the provider query so it can be tested on the
  host with no device.

**Changed**

- `AndroidManifest.xml` — `READ_CALENDAR`.
- `data/SpecialDay.kt` — `SOURCE_CALENDAR` constant.
- `data/Daos.kt` — `deleteBySourceInRange()` for the replace step.
- `data/repository/ClockRepository.kt` — `replaceSpecialDays()`.
- `ui/ClockViewModel.kt` — `availableCalendars()`, `importHolidaysFromCalendar()`, and an
  `ImportState` (Idle / Running / Done / Failed) for the screen.
- `HolidaysScreen.kt` — import button with progress state, permission request, calendar picker
  dialog, one-line result message, and a "from calendar" marker on imported rows.

**Tests** — `CalendarHolidayImporterTest.kt` (new), 9 tests. 29 across the project, all passing.

## The four rules that make this safe

**Only all-day events are imported.** A holiday is an all-day event; a 3 pm meeting is not.
This single filter is what lets someone point the import at a work calendar without every
meeting silencing a morning alarm. Tested both alone and mixed with all-day events.

**The exclusive end is handled.** The provider reports an all-day event's end as the *day
after* it finishes — a one-day holiday on the 5th comes back as 5th → 6th. Taking that at face
value would mark two days and silence an extra morning. A multi-day holiday is one event, not
several, and expands to one row per day. This was the part flagged as least certain in the
plan, and it has its own tests, including an explicit check that a one-day holiday does not
leak into the following day.

**Re-importing replaces instead of stacking.** Each import clears only rows with
`source = CALENDAR` *inside the import window*, then inserts the fresh ones. Hand-entered days
are never touched, and an older import outside the window is not silently erased. This is what
the `source` column was added for in the original holiday work.

**Runaway events are capped.** An all-day event longer than 31 days is truncated. People do use
year-long all-day events as banners, and without the cap one of those would mark every day of
the year as a holiday and silence every alarm.

## Other decisions

- **The permission is requested only when Import is tapped**, never at start-up. A user who
  never uses the feature never sees the prompt. If refused, the screen says so once, the manual
  list keeps working, and there is no repeat nagging.
- **The import re-arms alarms afterwards**, reusing `refreshDaysAndReschedule()` — an import
  that marks next Monday a holiday must reach the alarm already pending for that Monday.
- **The query runs on `Dispatchers.IO`** with a progress state on the button, since a device
  with large subscriptions can be slow to answer.
- **Read-only.** Nothing is ever written back to the user's calendar.
- Where a calendar day and a hand-entered day collide, the calendar wins (`epochDay` is the
  primary key, and the insert replaces). The calendar is the better authority for a public
  holiday, and the user can still edit the day afterwards.

## Not included

Automatic background re-sync. The import is user-triggered only — a scheduled sync means a
background job, another failure mode, and holidays changing without the user asking.

## Verification

`:app:testDebugUnitTest` (29 tests) and `:app:assembleDebug` both pass.

**Still not run on a device.** Two things specifically want checking there: the multi-day and
timezone behaviour against a real holiday subscription, and the `READ_CALENDAR` prompt flow
including the refusal path. The unit tests cover the mapping logic, but not what the real
provider actually returns.
