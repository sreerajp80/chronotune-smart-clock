package `in`.sreerajp.chronotune_smart_clock.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,                  // 0-23
    val minute: Int,                // 0-59
    val label: String = "",
    val isEnabled: Boolean = true,
    val daysOfWeek: String = "",    // "1,2,3,4,5,6,7" (1=Monday, 7=Sunday), empty = once
    val customToneName: String = "Morning Breeze", // Built-in melody name
    val customToneUri: String = "", // User chosen URI (if any)
    val volume: Float = 0.8f,
    val snoozeMinutes: Int = 5,
    val isVibrate: Boolean = true,
    // Pause window: while today falls within [pauseStartMillis, pauseEndMillis] the alarm is
    // suppressed. Both are UTC-midnight millis (as produced by Material3 DateRangePicker);
    // 0 means "no pause". Compared by epoch day to avoid timezone drift.
    val pauseStartMillis: Long = 0L,
    val pauseEndMillis: Long = 0L
) {
    fun getFormattedTime(is24Hour: Boolean = false): String {
        if (is24Hour) {
            return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        }
        val amPm = if (hour >= 12) "PM" else "AM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format(Locale.getDefault(), "%02d:%02d %s", displayHour, minute, amPm)
    }

    fun getRepeatDaysList(): List<Int> {
        if (daysOfWeek.isBlank()) return emptyList()
        return daysOfWeek.split(",").mapNotNull { it.toIntOrNull() }
    }

    /** True when a pause window has been configured (both endpoints set). */
    fun isPauseConfigured(): Boolean = pauseStartMillis > 0L && pauseEndMillis > 0L

    /** True when the given epoch day falls inside the configured pause window (inclusive). */
    fun isPausedOnEpochDay(epochDay: Long): Boolean {
        if (!isPauseConfigured()) return false
        val startDay = pauseStartMillis / MILLIS_PER_DAY
        val endDay = pauseEndMillis / MILLIS_PER_DAY
        return epochDay in startDay..endDay
    }

    /** True when today (local date) falls inside the configured pause window. */
    fun isPausedNow(): Boolean = isPausedOnEpochDay(todayEpochDay())

    companion object {
        const val MILLIS_PER_DAY: Long = 86_400_000L

        /**
         * Epoch day for a local calendar date, using the same basis as Material3's
         * DateRangePicker (which represents a calendar date as UTC midnight). We read the
         * local Y/M/D and re-anchor it at UTC midnight so comparisons line up exactly.
         */
        fun localCalendarToEpochDay(cal: Calendar): Long {
            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            }
            return utc.timeInMillis / MILLIS_PER_DAY
        }

        fun todayEpochDay(): Long = localCalendarToEpochDay(Calendar.getInstance())
    }
}

/**
 * A single (possibly concurrent) countdown timer. Persisted so a running timer survives
 * process death / screen-off.
 *
 * Two independent time bases are stored on purpose:
 * - [endAtElapsed] uses [android.os.SystemClock.elapsedRealtime] (monotonic, immune to
 *   wall-clock changes) and drives the smooth on-screen + notification countdown. It resets
 *   on reboot, so it is recomputed from [remainingMs] when the process/boot restarts.
 * - [fireAtWallClock] uses RTC ([System.currentTimeMillis]) and is what AlarmManager fires on,
 *   so the ring survives backgrounding/Doze/reboot.
 */
@Entity(tableName = "timers")
data class TimerItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String = "",
    val totalDurationMs: Long,          // original configured duration
    val remainingMs: Long,              // remaining when PAUSED/IDLE; last-known otherwise
    val endAtElapsed: Long = 0L,        // SystemClock.elapsedRealtime() target while RUNNING
    val fireAtWallClock: Long = 0L,     // System.currentTimeMillis() target while RUNNING
    val state: String = STATE_IDLE,     // IDLE | RUNNING | PAUSED | FINISHED
    val toneName: String = "Cosmic Shimmer",
    val toneUri: String = "",
    val volume: Float = 0.8f,
    val createdAt: Long = 0L
) {
    /** Remaining ms right now: derived from the elapsedRealtime base while RUNNING. */
    fun currentRemaining(nowElapsed: Long): Long =
        if (state == STATE_RUNNING) (endAtElapsed - nowElapsed).coerceAtLeast(0L)
        else remainingMs.coerceAtLeast(0L)

    companion object {
        const val STATE_IDLE = "IDLE"
        const val STATE_RUNNING = "RUNNING"
        const val STATE_PAUSED = "PAUSED"
        const val STATE_FINISHED = "FINISHED"

        /** Offset applied to a timer id when used as an AlarmManager request code / ring ID,
         *  keeping it distinct from alarm (id), music (id+10000) and snooze (id+50000/+100000). */
        const val RING_ID_OFFSET = 200000
    }
}

/** A reusable named timer configuration the user can start with one tap (e.g. "Tea 3 min"). */
@Entity(tableName = "timer_presets")
data class TimerPreset(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val durationMs: Long,
    val toneName: String = "Cosmic Shimmer",
    val toneUri: String = "",
    val volume: Float = 0.8f,
    val sortOrder: Int = 0
)

@Entity(tableName = "world_clocks")
data class WorldClock(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cityName: String,
    val timezoneId: String // e.g., "America/New_York", "Europe/London", "Asia/Kolkata"
)

@Entity(tableName = "music_schedules")
data class MusicSchedule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val durationMinutes: Int = 30,
    val label: String = "",
    val isEnabled: Boolean = true,
    val daysOfWeek: String = "",    // "1,2,3,4,5,6,7" (1=Monday...7=Sunday)
    val musicTrackName: String = "Lo-Fi Beats", // Newline-separated ambient melody names
    val customFileUri: String = "", // Newline-separated "uri\tDisplayName" entries
    val volume: Float = 0.6f
) {
    fun getFormattedTime(is24Hour: Boolean = false): String {
        if (is24Hour) {
            return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        }
        val amPm = if (hour >= 12) "PM" else "AM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format(Locale.getDefault(), "%02d:%02d %s", displayHour, minute, amPm)
    }

    fun getRepeatDaysList(): List<Int> {
        if (daysOfWeek.isBlank()) return emptyList()
        return daysOfWeek.split(",").mapNotNull { it.toIntOrNull() }
    }

    fun getAmbientTracks(): List<String> =
        if (musicTrackName.isBlank()) emptyList()
        else musicTrackName.split("\n").map { it.trim() }.filter { it.isNotBlank() }

    // Returns list of (uri, displayName)
    fun getCustomFiles(): List<Pair<String, String>> =
        if (customFileUri.isBlank()) emptyList()
        else customFileUri.split("\n").mapNotNull { line ->
            val parts = line.split("\t", limit = 2)
            val uri = parts.getOrNull(0)?.trim().orEmpty()
            if (uri.isBlank()) null
            else uri to (parts.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() } ?: "Custom audio")
        }

    fun describeTracks(): String {
        val ambients = getAmbientTracks()
        val files = getCustomFiles()
        return when {
            ambients.isEmpty() && files.isEmpty() -> "No tracks"
            ambients.size == 1 && files.isEmpty() -> ambients.first()
            ambients.isEmpty() && files.size == 1 -> files.first().second
            ambients.isNotEmpty() && files.isEmpty() -> "${ambients.size} melodies"
            ambients.isEmpty() -> "${files.size} files"
            else -> "${ambients.size} melodies + ${files.size} files"
        }
    }
}
