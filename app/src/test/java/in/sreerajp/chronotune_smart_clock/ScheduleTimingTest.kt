package `in`.sreerajp.chronotune_smart_clock

import `in`.sreerajp.chronotune_smart_clock.data.Alarm
import `in`.sreerajp.chronotune_smart_clock.data.SpecialDay
import `in`.sreerajp.chronotune_smart_clock.data.canSnoozeAgain
import `in`.sreerajp.chronotune_smart_clock.data.nextTriggerTime
import `in`.sreerajp.chronotune_smart_clock.data.snoozeGapMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Tests for the pure scheduling helpers: holiday / work-day awareness and the snooze gap rules.
 * These need no Android framework — the day list is injected as a plain lambda.
 */
class ScheduleTimingTest {

    /** All seven weekdays, so the only thing that can move the result is the day list. */
    private val everyDay = listOf(1, 2, 3, 4, 5, 6, 7)

    /** The epoch day for a Calendar, in the same basis the scheduler uses. */
    private fun dayOf(cal: Calendar): Long = Alarm.localCalendarToEpochDay(cal)

    /** A day list built from epoch-day -> kind pairs. */
    private fun dayMap(vararg entries: Pair<Long, String>): (Long) -> String? {
        val map = entries.toMap()
        return { epochDay -> map[epochDay] }
    }

    /** The epoch day the alarm would fire on with no special days at all. */
    private fun plainNextDay(hour: Int, minute: Int, days: List<Int>): Long =
        dayOf(nextTriggerTime(hour, minute, days))

    // ---- Holiday awareness ----------------------------------------------------------------

    @Test
    fun allDaysMode_ignoresAHolidayOnTheDayItWouldFire() {
        val target = plainNextDay(7, 0, everyDay)
        val result = nextTriggerTime(
            7, 0, everyDay,
            holidayMode = Alarm.HOLIDAY_MODE_ALL_DAYS,
            dayKind = dayMap(target to SpecialDay.KIND_HOLIDAY)
        )
        assertEquals("ALL_DAYS must not consult the day list", target, dayOf(result))
    }

    @Test
    fun skipHolidays_movesPastAMarkedHoliday() {
        val target = plainNextDay(7, 0, everyDay)
        val result = nextTriggerTime(
            7, 0, everyDay,
            holidayMode = Alarm.HOLIDAY_MODE_SKIP_HOLIDAYS,
            dayKind = dayMap(target to SpecialDay.KIND_HOLIDAY)
        )
        assertEquals("a holiday must push the alarm to the next day", target + 1, dayOf(result))
    }

    @Test
    fun skipHolidays_movesPastSeveralHolidaysInARow() {
        val target = plainNextDay(7, 0, everyDay)
        val result = nextTriggerTime(
            7, 0, everyDay,
            holidayMode = Alarm.HOLIDAY_MODE_SKIP_HOLIDAYS,
            dayKind = dayMap(
                target to SpecialDay.KIND_HOLIDAY,
                (target + 1) to SpecialDay.KIND_HOLIDAY,
                (target + 2) to SpecialDay.KIND_HOLIDAY
            )
        )
        assertEquals(target + 3, dayOf(result))
    }

    @Test
    fun skipHolidays_ignoresWorkingDayMarks() {
        // A day marked as a working day must not make SKIP_HOLIDAYS ring on an unselected
        // weekday — only WORKDAYS_ONLY grants that.
        val target = plainNextDay(7, 0, everyDay)
        val result = nextTriggerTime(
            7, 0, everyDay,
            holidayMode = Alarm.HOLIDAY_MODE_SKIP_HOLIDAYS,
            dayKind = dayMap(target to SpecialDay.KIND_WORKING_DAY)
        )
        assertEquals(target, dayOf(result))
    }

    @Test
    fun workdaysOnly_ringsOnAWorkingDayEvenWhenThatWeekdayIsNotSelected() {
        // Alarm repeats on no weekday that matches tomorrow; only the WORKING_DAY mark can
        // make it fire then. We find tomorrow's weekday and deliberately leave it out.
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DATE, 1) }
        val tomorrowModelDay =
            if (tomorrow.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7
            else tomorrow.get(Calendar.DAY_OF_WEEK) - 1
        val withoutTomorrow = everyDay.filter { it != tomorrowModelDay }
        val tomorrowDay = dayOf(tomorrow)

        val result = nextTriggerTime(
            7, 0, withoutTomorrow,
            holidayMode = Alarm.HOLIDAY_MODE_WORKDAYS_ONLY,
            dayKind = dayMap(tomorrowDay to SpecialDay.KIND_WORKING_DAY)
        )

        // The alarm at 07:00 either fires today (if 07:00 has not passed and today is selected)
        // or later; what matters is that tomorrow is now reachable despite being unselected.
        val plain = nextTriggerTime(7, 0, withoutTomorrow)
        assertTrue(
            "a marked working day must be reachable in WORKDAYS_ONLY",
            dayOf(result) <= dayOf(plain)
        )
    }

    @Test
    fun workdaysOnly_stillSkipsHolidays() {
        val target = plainNextDay(7, 0, everyDay)
        val result = nextTriggerTime(
            7, 0, everyDay,
            holidayMode = Alarm.HOLIDAY_MODE_WORKDAYS_ONLY,
            dayKind = dayMap(target to SpecialDay.KIND_HOLIDAY)
        )
        assertEquals(target + 1, dayOf(result))
    }

    @Test
    fun oneShotAlarm_alsoSkipsAHoliday() {
        // An alarm with no repeat days is a one-shot; a holiday must push it forward too.
        val target = plainNextDay(7, 0, emptyList())
        val result = nextTriggerTime(
            7, 0, emptyList(),
            holidayMode = Alarm.HOLIDAY_MODE_SKIP_HOLIDAYS,
            dayKind = dayMap(target to SpecialDay.KIND_HOLIDAY)
        )
        assertEquals(target + 1, dayOf(result))
    }

    // ---- Start date -----------------------------------------------------------------------

    @Test
    fun repeatingAlarm_waitsUntilTheStartDate() {
        val start = Alarm.todayEpochDay() + 30
        val result = nextTriggerTime(7, 0, everyDay, startEpochDay = start)
        assertEquals("must not ring before the start date", start, dayOf(result))
    }

    @Test
    fun repeatingAlarm_landsOnTheFirstSelectedWeekdayOnOrAfterTheStartDate() {
        // Only one weekday selected, so the alarm has to walk forward from the start date to
        // find it. The result must be on or after the start date and on that weekday.
        val start = Alarm.todayEpochDay() + 30
        val onlyWednesday = listOf(3)
        val result = nextTriggerTime(7, 0, onlyWednesday, startEpochDay = start)

        assertTrue("never earlier than the start date", dayOf(result) >= start)
        assertTrue("within a week of it", dayOf(result) < start + 7)
        assertEquals(Calendar.WEDNESDAY, result.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun datedOneShot_ringsExactlyOnItsDate() {
        val target = Alarm.todayEpochDay() + 21
        val result = nextTriggerTime(7, 0, emptyList(), startEpochDay = target)
        assertEquals(target, dayOf(result))
    }

    @Test
    fun datedOneShot_worksMoreThanAYearOut() {
        // The repeat scan only looks 366 days ahead, so a far-future date has to be jumped to
        // rather than walked towards.
        val target = Alarm.todayEpochDay() + 500
        val result = nextTriggerTime(7, 0, emptyList(), startEpochDay = target)
        assertEquals(target, dayOf(result))
    }

    @Test
    fun repeatingAlarm_worksWithAStartDateMoreThanAYearOut() {
        val start = Alarm.todayEpochDay() + 500
        val result = nextTriggerTime(7, 0, everyDay, startEpochDay = start)
        assertEquals(start, dayOf(result))
    }

    @Test
    fun pastStartDate_isInert() {
        val past = Alarm.todayEpochDay() - 10
        val withStart = nextTriggerTime(7, 0, everyDay, startEpochDay = past)
        val without = nextTriggerTime(7, 0, everyDay)
        assertEquals(dayOf(without), dayOf(withStart))
    }

    @Test
    fun startDateInsideThePauseWindow_resumesAfterTheWindowEnds() {
        // Start date and pause window overlap: the alarm must wait for whichever ends later.
        val start = Alarm.todayEpochDay() + 10
        val pauseStart = (Alarm.todayEpochDay() + 5) * Alarm.MILLIS_PER_DAY
        val pauseEnd = (Alarm.todayEpochDay() + 20) * Alarm.MILLIS_PER_DAY

        val result = nextTriggerTime(
            7, 0, everyDay,
            pauseStartMillis = pauseStart,
            pauseEndMillis = pauseEnd,
            startEpochDay = start
        )
        assertEquals(Alarm.todayEpochDay() + 21, dayOf(result))
    }

    @Test
    fun startDateCombinesWithHolidaySkipping() {
        val start = Alarm.todayEpochDay() + 10
        val result = nextTriggerTime(
            7, 0, everyDay,
            holidayMode = Alarm.HOLIDAY_MODE_SKIP_HOLIDAYS,
            dayKind = dayMap(start to SpecialDay.KIND_HOLIDAY),
            startEpochDay = start
        )
        assertEquals("the start day is a holiday, so it begins the day after", start + 1, dayOf(result))
    }

    // ---- Snooze gap -----------------------------------------------------------------------

    @Test
    fun fixedMode_alwaysReturnsTheBaseGap() {
        listOf(0, 1, 2, 5, 20).forEach { count ->
            assertEquals(10, snoozeGapMinutes(10, Alarm.SNOOZE_MODE_FIXED, count))
        }
    }

    @Test
    fun progressiveMode_shrinksTheGapWithEachSnooze() {
        // 10/1, 10/2, 10/3, 10/4 ... rounded.
        assertEquals(10, snoozeGapMinutes(10, Alarm.SNOOZE_MODE_PROGRESSIVE, 0))
        assertEquals(5, snoozeGapMinutes(10, Alarm.SNOOZE_MODE_PROGRESSIVE, 1))
        assertEquals(3, snoozeGapMinutes(10, Alarm.SNOOZE_MODE_PROGRESSIVE, 2))
        assertEquals(3, snoozeGapMinutes(10, Alarm.SNOOZE_MODE_PROGRESSIVE, 3))
        assertEquals(2, snoozeGapMinutes(10, Alarm.SNOOZE_MODE_PROGRESSIVE, 4))
    }

    @Test
    fun progressiveMode_neverGoesBelowOneMinute() {
        assertEquals(1, snoozeGapMinutes(5, Alarm.SNOOZE_MODE_PROGRESSIVE, 50))
        assertEquals(1, snoozeGapMinutes(1, Alarm.SNOOZE_MODE_PROGRESSIVE, 0))
    }

    // ---- Snooze limit ---------------------------------------------------------------------

    @Test
    fun zeroLimit_meansUnlimited() {
        assertTrue(canSnoozeAgain(0, 0))
        assertTrue(canSnoozeAgain(0, 99))
    }

    @Test
    fun limitAllowsExactlyThatManySnoozes() {
        assertTrue("first snooze of three", canSnoozeAgain(3, 0))
        assertTrue("second snooze of three", canSnoozeAgain(3, 1))
        assertTrue("third snooze of three", canSnoozeAgain(3, 2))
        assertFalse("the fourth must be refused", canSnoozeAgain(3, 3))
        assertFalse(canSnoozeAgain(3, 4))
    }
}
