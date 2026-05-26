package `in`.sreerajp.chronotune_smart_clock.data

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val isVibrate: Boolean = true
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
