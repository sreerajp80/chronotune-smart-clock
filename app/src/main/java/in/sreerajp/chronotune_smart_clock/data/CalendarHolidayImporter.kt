package `in`.sreerajp.chronotune_smart_clock.data

import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import java.util.Calendar

/**
 * Reads holidays out of a calendar on the device.
 *
 * Most phones already carry a subscribed holiday calendar ("Holidays in India" and the like),
 * so importing beats typing fifteen dates by hand and keeps working next year on its own.
 *
 * Everything here is read-only. Nothing is ever written back to the user's calendar.
 */
object CalendarHolidayImporter {

    private const val TAG = "CalendarHolidayImport"

    /** How far ahead to import. Far enough for next year's holidays, small enough to stay quick. */
    const val IMPORT_MONTHS_AHEAD = 18

    /**
     * A guard against one absurd event swamping the list. A real holiday runs a few days; a
     * year-long all-day event (people do use these as banners) would otherwise mark every day
     * of that year as a holiday and silence every alarm.
     */
    const val MAX_DAYS_PER_EVENT = 31

    /** A calendar the user can pick from. */
    data class CalendarInfo(
        val id: Long,
        val displayName: String,
        val accountName: String
    )

    /**
     * One all-day event as read from the provider, before it becomes [SpecialDay] rows.
     * Kept as a plain type so [toSpecialDays] can be tested without a real cursor.
     *
     * [beginMillis] and [endMillis] come straight from the provider. For an all-day event the
     * provider works in UTC midnight and the end is **exclusive** — a single-day holiday on the
     * 5th is reported as 5th → 6th, not 5th → 5th.
     */
    data class RawEvent(
        val title: String,
        val beginMillis: Long,
        val endMillis: Long,
        val isAllDay: Boolean
    )

    /**
     * Turns provider rows into holiday days.
     *
     * Rules, in order of how much they matter:
     *  - Timed events are dropped. A holiday is an all-day event; a 3 pm meeting is not, and
     *    must never silence a morning alarm. This is what makes it safe to point the import at
     *    a work calendar.
     *  - A multi-day event yields one row per day it covers, honouring the exclusive end.
     *  - An event covering more than [MAX_DAYS_PER_EVENT] days is truncated (see above).
     *  - Days are keyed by epoch day, so two events on one date collapse into a single row.
     */
    fun toSpecialDays(events: List<RawEvent>): List<SpecialDay> {
        val byDay = LinkedHashMap<Long, SpecialDay>()

        events.forEach { event ->
            if (!event.isAllDay) return@forEach

            val firstDay = event.beginMillis / Alarm.MILLIS_PER_DAY
            // The provider's end is exclusive, so the last covered day is one before it. A
            // malformed or equal end still yields the single starting day.
            val lastDayExclusive = event.endMillis / Alarm.MILLIS_PER_DAY
            val lastDay = (lastDayExclusive - 1).coerceAtLeast(firstDay)
            val cappedLastDay = minOf(lastDay, firstDay + MAX_DAYS_PER_EVENT - 1)

            for (day in firstDay..cappedLastDay) {
                byDay[day] = SpecialDay(
                    epochDay = day,
                    name = event.title.trim(),
                    kind = SpecialDay.KIND_HOLIDAY,
                    source = SpecialDay.SOURCE_CALENDAR
                )
            }
        }

        return byDay.values.sortedBy { it.epochDay }
    }

    /** The window an import covers: today through [IMPORT_MONTHS_AHEAD] months ahead. */
    fun importWindow(): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, IMPORT_MONTHS_AHEAD) }
        return start.timeInMillis to end.timeInMillis
    }

    /**
     * The epoch-day range the import covers, used to scope the replace step so an older import
     * outside the window is not silently erased.
     */
    fun importWindowDays(): LongRange {
        val (startMillis, endMillis) = importWindow()
        val utcStart = utcEpochDayOfLocalDate(startMillis)
        val utcEnd = utcEpochDayOfLocalDate(endMillis)
        return utcStart..utcEnd
    }

    // Local wall-clock date -> the UTC-midnight epoch day the app stores everywhere else.
    private fun utcEpochDayOfLocalDate(millis: Long): Long {
        val local = Calendar.getInstance().apply { timeInMillis = millis }
        return Alarm.localCalendarToEpochDay(local)
    }

    /**
     * Lists the calendars on the device. Caller must already hold READ_CALENDAR; without it
     * this returns an empty list rather than throwing.
     */
    fun listCalendars(context: Context): List<CalendarInfo> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME
        )
        val result = mutableListOf<CalendarInfo>()
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    result.add(
                        CalendarInfo(
                            id = cursor.getLong(0),
                            displayName = cursor.getStringOrEmpty(1).ifBlank { "(unnamed)" },
                            accountName = cursor.getStringOrEmpty(2)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // A missing permission surfaces as a SecurityException; treat any failure as
            // "no calendars" so the screen can say so instead of crashing.
            Log.e(TAG, "Could not list calendars: ${e.message}")
        }
        return result
    }

    /**
     * Reads all-day events from [calendarId] over the import window and maps them to holidays.
     *
     * Uses the Instances table rather than Events so a recurring holiday is expanded into its
     * actual occurrences instead of appearing once at its original date.
     */
    fun readHolidays(context: Context, calendarId: Long): List<SpecialDay> {
        val (windowStart, windowEnd) = importWindow()

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(windowStart.toString())
            .appendPath(windowEnd.toString())
            .build()

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY
        )

        val events = mutableListOf<RawEvent>()
        try {
            context.contentResolver.query(
                uri,
                projection,
                "${CalendarContract.Instances.CALENDAR_ID} = ?",
                arrayOf(calendarId.toString()),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    events.add(
                        RawEvent(
                            title = cursor.getStringOrEmpty(0),
                            beginMillis = cursor.getLong(1),
                            endMillis = cursor.getLong(2),
                            isAllDay = cursor.getInt(3) == 1
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not read calendar $calendarId: ${e.message}")
            return emptyList()
        }

        return toSpecialDays(events)
    }

    private fun Cursor.getStringOrEmpty(index: Int): String =
        if (isNull(index)) "" else getString(index) ?: ""
}
