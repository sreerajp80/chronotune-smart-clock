# Import holidays from the device calendar

**Status:** completed

## The issue

The holiday list works, but every day has to be typed in by hand. Nobody will enter 15 public
holidays one at a time, so in practice the "Skip holidays" setting will sit unused.

Most people already have a holiday calendar on their phone — Google Calendar subscriptions
like "Holidays in India" are added by default on many devices. Those holidays are sitting
right there as all-day events. Reading them turns a chore into two taps, and it keeps working
next year without anyone touching it.

## Design

### What is read

The Android calendar provider exposes calendars (`CalendarContract.Calendars`) and events
(`CalendarContract.Events` / `Instances`). The import will:

1. List the calendars on the device so the user picks one — typically a holiday subscription,
   but it could equally be a work calendar with company shutdowns on it.
2. Read that calendar's **all-day events** over a date window.
3. Turn each one into a `SpecialDay` with `kind = HOLIDAY`, `source = CALENDAR`, and the event
   title as the name.

**Only all-day events.** A holiday is an all-day event; a 3pm meeting is not a holiday and must
never silence a morning alarm. This single filter is what keeps the import from turning a busy
work calendar into a wall of skipped alarms.

**Window: today to +18 months.** Long enough to cover next year's holidays once they are
published, short enough to keep the list and the query small.

### Repeat imports replace, not duplicate

This is what the `source` column was added for. On each import:

- every existing row with `source = CALENDAR` **inside the import window** is deleted,
- then the freshly-read days are inserted.

So re-importing after the calendar changes gives a clean result rather than a pile-up, while
rows the user typed in by hand (`source = MANUAL`) are never touched. Rows outside the window
are also left alone, so an old import is not silently erased.

`epochDay` being the primary key means a manual entry and a calendar entry for the same day
cannot both exist — the import overwrites it. That is the right way round: the calendar is the
more authoritative source for a public holiday, and the user can always edit the day afterwards.

### The permission

`READ_CALENDAR` is a runtime permission and a new one for this app. Handling:

- Declared in the manifest.
- Requested only when the user taps **Import from calendar** — never at start-up. Someone who
  never uses the feature never sees the prompt.
- If refused, the screen says so plainly and the manual list keeps working. No nagging, no
  repeat prompts; a second tap re-requests, which is the normal Android behaviour.

The existing `rememberLauncherForActivityResult(RequestPermission())` pattern in
`SettingsScreen.kt` is reused as-is.

### Reading off the main thread

The query runs in a coroutine on `Dispatchers.IO` inside the ViewModel. Calendar queries can be
slow on a device with large subscriptions, so the button shows a progress state while it runs
and the result is reported as a count ("Added 15 days from Holidays in India").

### After an import

Same rule as a manual edit, and for the same reason: reload `SpecialDayRegistry` and re-arm
every enabled holiday-aware alarm. An import that adds next Monday as a holiday must affect the
alarm already pending for that Monday. This reuses the existing `refreshDaysAndReschedule()`.

## Files to change

**New**

- `data/CalendarHolidayImporter.kt` — the calendar provider queries: list calendars, read
  all-day events in a window, map to `SpecialDay`. Kept free of UI and ViewModel code so the
  mapping can be unit-tested with a fake cursor.

**Changed**

- `AndroidManifest.xml` — `READ_CALENDAR`.
- `data/Daos.kt` — a delete-by-source-within-window query for the replace step.
- `data/repository/ClockRepository.kt` — expose it.
- `ui/ClockViewModel.kt` — `importHolidaysFromCalendar(calendarId)`, the available-calendars
  read, an import-status state for the UI, and the reschedule afterwards.
- `HolidaysScreen.kt` — an **Import from calendar** button, the permission request, a calendar
  picker dialog, a progress state and a result message. Calendar-sourced rows get a small
  "from calendar" marker so the user can tell them from their own entries.
- `res/values/strings.xml` — new labels.

**Tests** — mapping tests for the importer: an all-day event becomes a `SpecialDay` on the
right epoch day; a timed event is rejected; a multi-day all-day event produces one row per day;
and the timezone handling puts an event on the date the calendar shows, not a day either side.

## The part I am least sure about

**Multi-day all-day events and timezones.** The calendar provider reports all-day events in UTC
with an exclusive end. A three-day holiday is one event, not three, and off-by-one errors here
land the alarm on the wrong morning — which is exactly the failure the user would notice. The
mapping gets its own tests, and I would want this specific case checked on a real device with a
real holiday subscription before trusting it.

## Not in this plan

- Automatic background re-sync. This import is user-triggered only. A scheduled sync means a
  background job, another failure mode, and holidays changing without the user asking. Worth
  considering later, once the manual import has proved itself.
- Importing working days from a calendar. Compensatory work days are rarely on a subscribed
  calendar and stay manual.
- Writing anything back to the calendar. This is read-only, always.

## Order of work

1. Manifest permission + the importer with its mapping tests.
2. DAO replace-by-source query and repository/ViewModel wiring.
3. UI: permission request, calendar picker, progress and result.
4. The "from calendar" marker on rows.
