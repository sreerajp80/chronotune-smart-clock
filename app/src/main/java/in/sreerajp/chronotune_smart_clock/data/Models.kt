package `in`.sreerajp.chronotune_smart_clock.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar
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
            return String.format("%02d:%02d", hour, minute)
        }
        val amPm = if (hour >= 12) "PM" else "AM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format("%02d:%02d %s", displayHour, minute, amPm)
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
            return String.format("%02d:%02d", hour, minute)
        }
        val amPm = if (hour >= 12) "PM" else "AM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format("%02d:%02d %s", displayHour, minute, amPm)
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
            ambients.isEmpty() && files.isNotEmpty() -> "${files.size} files"
            else -> "${ambients.size} melodies + ${files.size} files"
        }
    }
}
