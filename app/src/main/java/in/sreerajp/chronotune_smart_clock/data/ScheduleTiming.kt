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
 */
fun nextTriggerTime(
    hour: Int,
    minute: Int,
    repeatDays: List<Int>,
    pauseStartMillis: Long = 0L,
    pauseEndMillis: Long = 0L
): Calendar {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    fun isPaused(cal: Calendar): Boolean {
        if (pauseStartMillis <= 0L || pauseEndMillis <= 0L) return false
        val day = Alarm.localCalendarToEpochDay(cal)
        return day in (pauseStartMillis / Alarm.MILLIS_PER_DAY)..(pauseEndMillis / Alarm.MILLIS_PER_DAY)
    }

    if (repeatDays.isEmpty()) {
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DATE, 1)
        }
        // A one-shot landing inside the pause window is pushed to the day after it ends.
        while (isPaused(calendar)) {
            calendar.add(Calendar.DATE, 1)
        }
        return calendar
    }

    // Start from today; if today's time has already passed, begin scanning tomorrow.
    if (calendar.timeInMillis <= System.currentTimeMillis()) {
        calendar.add(Calendar.DATE, 1)
    }
    // Advance up to a year to land on the next selected weekday that isn't paused
    // (a pause window can span longer than a single week).
    repeat(366) {
        if (repeatDays.contains(toModelDay(calendar.get(Calendar.DAY_OF_WEEK))) && !isPaused(calendar)) {
            return calendar
        }
        calendar.add(Calendar.DATE, 1)
    }
    return calendar
}

// Converts Calendar.DAY_OF_WEEK (Sunday=1 … Saturday=7) to the model encoding (Monday=1 … Sunday=7).
private fun toModelDay(calendarDayOfWeek: Int): Int =
    if (calendarDayOfWeek == Calendar.SUNDAY) 7 else calendarDayOfWeek - 1

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
