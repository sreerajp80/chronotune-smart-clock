package `in`.sreerajp.chronotune_smart_clock.data

import java.util.Calendar

/**
 * Shared next-fire-time computation for alarms and music schedules. This is the single source of
 * truth used by both the scheduler ([`in`.sreerajp.chronotune_smart_clock.ui.AlarmScheduler]) and
 * the edit screens (to show a "rings in X" toast on save).
 *
 * - When [repeatDays] is empty the item is a one-shot: today at hour:minute, or tomorrow if that
 *   time has already passed.
 * - When [repeatDays] is set (1=Monday … 7=Sunday, matching the model encoding) the result is the
 *   soonest selected weekday at hour:minute that is still in the future.
 * - When a pause window [pauseStartMillis]..[pauseEndMillis] is configured (alarms only), any
 *   candidate date inside it is skipped so firing resumes only after the window ends.
 * - When [skipEpochDay] is set (alarms only), the single occurrence on that epoch day is skipped
 *   so firing resumes on the next selected day — this is the "skip next alarm" toggle.
 * - When [holidayMode] is not ALL_DAYS (alarms only), days classified by [dayKind] are taken into
 *   account: HOLIDAY days are rejected, and in WORKDAYS_ONLY a WORKING_DAY is accepted even if
 *   its weekday is not selected (compensatory working Saturdays).
 *
 * [dayKind] is a plain lambda rather than a database handle so this stays a pure, easily tested
 * function; the scheduler passes [SpecialDayRegistry.kindOf].
 */
fun nextTriggerTime(
    hour: Int,
    minute: Int,
    repeatDays: List<Int>,
    pauseStartMillis: Long = 0L,
    pauseEndMillis: Long = 0L,
    skipEpochDay: Long = 0L,
    holidayMode: String = Alarm.HOLIDAY_MODE_ALL_DAYS,
    dayKind: (Long) -> String? = { null },
    startEpochDay: Long = 0L
): Calendar {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val holidayAware = holidayMode == Alarm.HOLIDAY_MODE_SKIP_HOLIDAYS ||
        holidayMode == Alarm.HOLIDAY_MODE_WORKDAYS_ONLY

    // A candidate date is unavailable if it is inside the pause window, is the skipped day,
    // or is a holiday the alarm has been told to avoid.
    fun isSkipped(cal: Calendar): Boolean {
        val day = Alarm.localCalendarToEpochDay(cal)
        val paused = pauseStartMillis > 0L && pauseEndMillis > 0L &&
            day in (pauseStartMillis / Alarm.MILLIS_PER_DAY)..(pauseEndMillis / Alarm.MILLIS_PER_DAY)
        val skippedNext = skipEpochDay > 0L && day == skipEpochDay
        val holiday = holidayAware && dayKind(day) == SpecialDay.KIND_HOLIDAY
        // The alarm has not begun yet. For a one-shot this is what pins it to its own date,
        // because every earlier day is rejected and the scan starts at the date itself.
        val beforeStart = startEpochDay > 0L && day < startEpochDay
        return paused || skippedNext || holiday || beforeStart
    }

    // Whether the alarm is willing to ring on this weekday at all. Normally that means the
    // weekday is one of the selected days; in WORKDAYS_ONLY a day explicitly marked as a
    // working day also qualifies, which is how a compensatory working Saturday still rings.
    fun isSelectedDay(cal: Calendar): Boolean {
        if (repeatDays.contains(toModelDay(cal.get(Calendar.DAY_OF_WEEK)))) return true
        return holidayMode == Alarm.HOLIDAY_MODE_WORKDAYS_ONLY &&
            dayKind(Alarm.localCalendarToEpochDay(cal)) == SpecialDay.KIND_WORKING_DAY
    }

    // Jump straight to the start date when it is still ahead, instead of walking towards it a
    // day at a time. This is what pins a dated one-shot to its own day, and it also keeps a
    // start date more than a year out working — the repeat scan below only looks 366 days.
    if (startEpochDay > 0L && startEpochDay > Alarm.localCalendarToEpochDay(calendar)) {
        applyEpochDay(calendar, startEpochDay)
    }

    if (repeatDays.isEmpty()) {
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DATE, 1)
        }
        // A one-shot landing inside the pause window (or on the skip day) is pushed forward.
        while (isSkipped(calendar)) {
            calendar.add(Calendar.DATE, 1)
        }
        return calendar
    }

    // Start from today; if today's time has already passed, begin scanning tomorrow.
    if (calendar.timeInMillis <= System.currentTimeMillis()) {
        calendar.add(Calendar.DATE, 1)
    }
    // Advance up to a year to land on the next selected weekday that isn't paused/skipped
    // (a pause window can span longer than a single week).
    repeat(366) {
        if (isSelectedDay(calendar) && !isSkipped(calendar)) {
            return calendar
        }
        calendar.add(Calendar.DATE, 1)
    }
    return calendar
}

/**
 * Moves [calendar] to the calendar date named by [epochDay], keeping its time of day.
 *
 * Epoch days are stored on a UTC-midnight basis (see [Alarm.localCalendarToEpochDay]), so the
 * date fields are read back in UTC and re-applied locally. That way the alarm lands on the date
 * the user picked in any timezone, rather than sliding a day either side of the date line.
 */
private fun applyEpochDay(calendar: Calendar, epochDay: Long) {
    val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = epochDay * Alarm.MILLIS_PER_DAY
    }
    calendar.set(Calendar.YEAR, utc.get(Calendar.YEAR))
    calendar.set(Calendar.MONTH, utc.get(Calendar.MONTH))
    calendar.set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
}

// Converts Calendar.DAY_OF_WEEK (Sunday=1 … Saturday=7) to the model encoding (Monday=1 … Sunday=7).
private fun toModelDay(calendarDayOfWeek: Int): Int =
    if (calendarDayOfWeek == Calendar.SUNDAY) 7 else calendarDayOfWeek - 1

/**
 * How many minutes the next snooze should wait.
 *
 * [baseMinutes] is the alarm's own snoozeMinutes and [snoozeCount] is how many snoozes have
 * already happened (so the first snooze passes 0).
 *
 * FIXED always returns [baseMinutes]. PROGRESSIVE divides by the snooze number, so each snooze
 * is shorter than the last and the gap tapers instead of collapsing to nothing — for a 10 minute
 * base the sequence is 10, 5, 3, 3, 2, 2, 1... The result is never below 1 minute.
 */
fun snoozeGapMinutes(baseMinutes: Int, mode: String, snoozeCount: Int): Int {
    val base = baseMinutes.coerceAtLeast(1)
    if (mode != Alarm.SNOOZE_MODE_PROGRESSIVE) return base
    val n = snoozeCount.coerceAtLeast(0) + 1
    return Math.round(base.toFloat() / n).coerceAtLeast(1)
}

/**
 * True when the alarm may still be snoozed. [maxSnoozeCount] of 0 means unlimited.
 * [snoozeCount] is how many snoozes have already happened.
 */
fun canSnoozeAgain(maxSnoozeCount: Int, snoozeCount: Int): Boolean =
    maxSnoozeCount <= 0 || snoozeCount < maxSnoozeCount

/**
 * Human-readable time-until string for a future [targetMillis], e.g. "1 day 2 h", "8 h 30 m",
 * "45 m", or "less than a minute". Past/now targets collapse to "less than a minute".
 */
fun formatTimeUntil(targetMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val deltaMs = targetMillis - nowMillis
    if (deltaMs < 60_000L) return "less than a minute"

    val totalMinutes = deltaMs / 60_000L
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60

    return when {
        days > 0 -> {
            val d = if (days == 1L) "1 day" else "$days days"
            if (hours > 0) "$d $hours h" else d
        }
        hours > 0 -> if (minutes > 0) "$hours h $minutes m" else "$hours h"
        else -> "$minutes m"
    }
}
