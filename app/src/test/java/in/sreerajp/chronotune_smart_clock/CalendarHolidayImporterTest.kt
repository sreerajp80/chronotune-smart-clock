package `in`.sreerajp.chronotune_smart_clock

import `in`.sreerajp.chronotune_smart_clock.data.Alarm
import `in`.sreerajp.chronotune_smart_clock.data.CalendarHolidayImporter
import `in`.sreerajp.chronotune_smart_clock.data.CalendarHolidayImporter.RawEvent
import `in`.sreerajp.chronotune_smart_clock.data.SpecialDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for turning calendar events into holiday days.
 *
 * The mapping is deliberately a pure function over plain [RawEvent] values, so the tricky part
 * — the exclusive end date and multi-day events — can be checked here without a device, a
 * content provider, or a real calendar.
 */
class CalendarHolidayImporterTest {

    /** An all-day event as the provider reports it: UTC midnight, exclusive end. */
    private fun allDay(title: String, startDay: Long, dayCount: Int = 1) = RawEvent(
        title = title,
        beginMillis = startDay * Alarm.MILLIS_PER_DAY,
        // Exclusive: a one-day holiday on day N is reported as N -> N+1.
        endMillis = (startDay + dayCount) * Alarm.MILLIS_PER_DAY,
        isAllDay = true
    )

    @Test
    fun singleDayHoliday_becomesExactlyOneDay() {
        val result = CalendarHolidayImporter.toSpecialDays(listOf(allDay("Diwali", 20_000L)))

        assertEquals(1, result.size)
        assertEquals(20_000L, result[0].epochDay)
        assertEquals("Diwali", result[0].name)
        assertEquals(SpecialDay.KIND_HOLIDAY, result[0].kind)
        assertEquals(SpecialDay.SOURCE_CALENDAR, result[0].source)
    }

    @Test
    fun exclusiveEnd_doesNotLeakIntoTheFollowingDay() {
        // The regression that would matter most: a one-day holiday silencing two mornings.
        val result = CalendarHolidayImporter.toSpecialDays(listOf(allDay("Holiday", 20_000L)))
        assertTrue("must not mark the day after", result.none { it.epochDay == 20_001L })
    }

    @Test
    fun multiDayHoliday_becomesOneRowPerDay() {
        val result = CalendarHolidayImporter.toSpecialDays(
            listOf(allDay("Pooja holidays", 20_000L, dayCount = 3))
        )

        assertEquals(3, result.size)
        assertEquals(listOf(20_000L, 20_001L, 20_002L), result.map { it.epochDay })
    }

    @Test
    fun timedEvent_isIgnored() {
        // A 3 pm meeting is not a holiday and must never silence a morning alarm. This is what
        // makes it safe to point the import at a work calendar.
        val meeting = RawEvent(
            title = "Team sync",
            beginMillis = 20_000L * Alarm.MILLIS_PER_DAY + 15 * 3_600_000L,
            endMillis = 20_000L * Alarm.MILLIS_PER_DAY + 16 * 3_600_000L,
            isAllDay = false
        )
        assertTrue(CalendarHolidayImporter.toSpecialDays(listOf(meeting)).isEmpty())
    }

    @Test
    fun timedAndAllDayEventsMixed_onlyTheAllDayOneSurvives() {
        val meeting = RawEvent(
            "Standup",
            20_000L * Alarm.MILLIS_PER_DAY + 9 * 3_600_000L,
            20_000L * Alarm.MILLIS_PER_DAY + 10 * 3_600_000L,
            isAllDay = false
        )
        val result = CalendarHolidayImporter.toSpecialDays(
            listOf(meeting, allDay("Republic Day", 20_010L))
        )

        assertEquals(1, result.size)
        assertEquals(20_010L, result[0].epochDay)
    }

    @Test
    fun twoEventsOnTheSameDay_collapseToOneRow() {
        // epochDay is the primary key, so the mapping must not emit duplicates.
        val result = CalendarHolidayImporter.toSpecialDays(
            listOf(allDay("Holiday", 20_000L), allDay("Also a holiday", 20_000L))
        )
        assertEquals(1, result.size)
    }

    @Test
    fun malformedEventWithEqualStartAndEnd_stillYieldsItsOwnDay() {
        val odd = RawEvent(
            title = "Odd entry",
            beginMillis = 20_000L * Alarm.MILLIS_PER_DAY,
            endMillis = 20_000L * Alarm.MILLIS_PER_DAY,
            isAllDay = true
        )
        val result = CalendarHolidayImporter.toSpecialDays(listOf(odd))

        assertEquals(1, result.size)
        assertEquals(20_000L, result[0].epochDay)
    }

    @Test
    fun absurdlyLongEvent_isTruncated() {
        // A year-long all-day "banner" event would otherwise mark every day of that year as a
        // holiday and silence every alarm.
        val result = CalendarHolidayImporter.toSpecialDays(
            listOf(allDay("Sabbatical", 20_000L, dayCount = 400))
        )
        assertEquals(CalendarHolidayImporter.MAX_DAYS_PER_EVENT, result.size)
    }

    @Test
    fun importWindow_startsTodayAndRunsForward() {
        val (start, end) = CalendarHolidayImporter.importWindow()
        assertTrue("window must run forwards", end > start)

        val days = CalendarHolidayImporter.importWindowDays()
        assertTrue("window must include today", days.first <= Alarm.todayEpochDay())
        assertTrue("window must reach well into the future", days.last > Alarm.todayEpochDay() + 300)
    }
}
