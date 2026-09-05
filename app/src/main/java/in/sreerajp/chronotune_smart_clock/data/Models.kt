package `in`.sreerajp.chronotune_smart_clock.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Shared behaviour for time-of-day, optionally-repeating schedules (alarms, music schedules).
 * Implementers expose [hour]/[minute]/[daysOfWeek]; the formatting helpers are derived from them.
 */
interface Scheduled {
    val hour: Int           // 0-23
    val minute: Int         // 0-59
    val daysOfWeek: String  // "1,2,3,4,5,6,7" (1=Monday...7=Sunday), empty = once

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
}

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    override val hour: Int,                  // 0-23
    override val minute: Int,                // 0-59
    val label: String = "",
    val isEnabled: Boolean = true,
    override val daysOfWeek: String = "",    // "1,2,3,4,5,6,7" (1=Monday, 7=Sunday), empty = once
    val customToneName: String = "Morning Breeze", // Built-in melody name
    val customToneUri: String = "", // User chosen URI (if any)
    val volume: Float = 0.8f,
    val snoozeMinutes: Int = 5,
    val isVibrate: Boolean = true,
    // Dismiss challenge that must be completed before the alarm can be turned off.
    // NONE (default) = plain tap Dismiss. MATH | PHRASE | MEMORY = a wake-up task.
    val dismissChallenge: String = "NONE",
    // EASY | MEDIUM | HARD. Scales the chosen challenge (number size for MATH,
    // phrase length for PHRASE, tile count for MEMORY).
    val challengeDifficulty: String = "EASY",
    // How many challenge rounds must be solved in a row (1-10).
    val challengeCount: Int = 1,
    // Pause window: while today falls within [pauseStartMillis, pauseEndMillis] the alarm is
    // suppressed. Both are UTC-midnight millis (as produced by Material3 DateRangePicker);
    // 0 means "no pause". Compared by epoch day to avoid timezone drift.
    val pauseStartMillis: Long = 0L,
    val pauseEndMillis: Long = 0L,
    // How long the alarm keeps ringing before it silences itself. 0 = Never (ring until
    // dismissed, the historical behavior); any other value stops the ring after that many
    // minutes. New alarms seed this from the global default (AppPrefs.defaultAutoSilenceMinutes).
    val autoSilenceMinutes: Int = 0,
    // Skip-next-occurrence: the epoch day (same UTC-midnight basis as the pause window) of a
    // single upcoming firing to skip. 0 = not skipping. Because every check compares this for
    // exact epoch-day equality, a value only ever matches once; once that day has passed it can
    // never equal a future candidate again, so a consumed value is permanently inert and never
    // needs clearing (the UI only treats it as active while it is today-or-later).
    val skipNextEpochDay: Long = 0L,
    // How this alarm treats days marked in the shared `special_days` list.
    // ALL_DAYS (default) = ignore the list entirely, i.e. the historical behavior.
    // SKIP_HOLIDAYS = never ring on a day marked HOLIDAY.
    // WORKDAYS_ONLY = never ring on a HOLIDAY, and additionally ring on a day marked
    //   WORKING_DAY even when that weekday is not in daysOfWeek (compensatory work days).
    val holidayMode: String = HOLIDAY_MODE_ALL_DAYS,
    // Largest number of times this alarm may be snoozed before Snooze stops being offered.
    // 0 = unlimited, the historical behavior. New alarms seed this from the global default
    // (AppPrefs.defaultMaxSnoozeCount).
    val maxSnoozeCount: Int = 0,
    // FIXED = every snooze waits snoozeMinutes. PROGRESSIVE = each snooze is shorter than the
    // last (see snoozeGapMinutes). New alarms seed this from AppPrefs.defaultSnoozeMode.
    val snoozeMode: String = SNOOZE_MODE_FIXED,
    // Earliest day this alarm is allowed to ring (same UTC-midnight epoch-day basis as the
    // pause window). 0 = no start date, the historical behavior.
    //
    // What it means depends on whether the alarm repeats, which is why one field covers both
    // features and no extra mode flag is needed:
    //  - with repeat days: "don't begin yet" — the alarm skips every day before this one and
    //    then follows its normal weekly pattern.
    //  - with no repeat days (a one-shot): "ring on exactly this day" — this is the
    //    future-dated one-time alarm.
    //
    // Like skipNextEpochDay, a value in the past is permanently inert: no future candidate day
    // can ever be earlier than it, so it never needs clearing.
    val startEpochDay: Long = 0L
) : Scheduled {

    /** True when a start date has been set at all (past or future). */
    fun hasStartDate(): Boolean = startEpochDay > 0L

    /** True while the start date still lies ahead, i.e. the alarm has not begun yet. */
    fun hasFutureStartDate(): Boolean = startEpochDay > todayEpochDay()

    /**
     * True when this alarm is a one-shot pinned to a specific date, rather than a repeating
     * alarm that merely starts later. This is the "ring once on 3 March" case.
     */
    fun isDatedOneShot(): Boolean = hasStartDate() && daysOfWeek.isBlank()
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

    /** True when the given epoch day is the one upcoming firing marked to be skipped. */
    fun isSkippedOnEpochDay(epochDay: Long): Boolean =
        skipNextEpochDay > 0L && epochDay == skipNextEpochDay

    /**
     * True while a skip is still pending (today or a future day). A skip day that has already
     * passed is treated as inactive — the UI shows nothing and the stored value is inert.
     */
    fun isSkippingActive(): Boolean = skipNextEpochDay >= todayEpochDay()

    companion object {
        const val MILLIS_PER_DAY: Long = 86_400_000L

        const val HOLIDAY_MODE_ALL_DAYS = "ALL_DAYS"
        const val HOLIDAY_MODE_SKIP_HOLIDAYS = "SKIP_HOLIDAYS"
        const val HOLIDAY_MODE_WORKDAYS_ONLY = "WORKDAYS_ONLY"

        const val SNOOZE_MODE_FIXED = "FIXED"
        const val SNOOZE_MODE_PROGRESSIVE = "PROGRESSIVE"

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
 * Every AlarmManager request code / notification id the app uses, in one place.
 *
 * These offsets have to stay apart from each other: two ids that collide share a
 * [android.app.PendingIntent] and a notification id, so arming one silently cancels the other.
 *
 * The snooze space in particular used to be computed by adding 50000 to whatever id was
 * ringing, which meant a snoozed snooze grew: id+50000, id+100000, id+150000... and the fourth
 * snooze of alarm N landed on N+200000 — exactly a timer's ring id. Now every snooze is
 * derived from the *original* alarm id ([snoozeRing]), so a chain of any length reuses one slot.
 */
object AlarmIds {
    /** Alarms use their row id as-is; everything else is offset away from that space. */
    const val MUSIC_OFFSET = 10_000

    /** Kept at its historical value so pending intents armed by older builds still match. */
    const val TIMER_RING_OFFSET = TimerItem.RING_ID_OFFSET   // 200_000

    const val SNOOZE_RING_OFFSET = 500_000
    const val SNOOZE_ACTION_OFFSET = 600_000
    const val ADD_MINUTE_ACTION_OFFSET = 700_000
    const val DISMISS_ACTION_OFFSET = 800_000

    /**
     * Fixed request code for the periodic re-arm watchdog (see AlarmScheduler.scheduleWatchdog).
     *
     * Kept above every derived id: the largest one an id can reach is the dismiss action of a
     * timer ring, id + 200_000 + 800_000, so anything past ~1.01 million is safely out of reach.
     */
    const val WATCHDOG_REQUEST_CODE = 1_500_000

    /**
     * Offsets used by the pre-fix snooze chain. Nothing arms these any more, but a device
     * upgrading from an older build can still have one pending, so they are cancelled once on
     * package replace.
     */
    val LEGACY_SNOOZE_OFFSETS = listOf(50_000, 100_000, 150_000)

    fun musicRing(scheduleId: Int): Int = scheduleId + MUSIC_OFFSET
    fun timerRing(timerId: Int): Int = timerId + TIMER_RING_OFFSET
    fun snoozeRing(baseAlarmId: Int): Int = baseAlarmId + SNOOZE_RING_OFFSET
    fun snoozeAction(baseAlarmId: Int): Int = baseAlarmId + SNOOZE_ACTION_OFFSET
    fun addMinuteAction(ringId: Int): Int = ringId + ADD_MINUTE_ACTION_OFFSET
    fun dismissAction(ringId: Int): Int = ringId + DISMISS_ACTION_OFFSET

    /**
     * The original alarm id behind a ringing id. A snooze ring reports the base alarm it came
     * from, so a whole morning — first ring, every snooze, the final dismiss — groups under one
     * id in the event history.
     */
    fun baseAlarmId(ringId: Int): Int =
        if (ringId >= SNOOZE_RING_OFFSET && ringId < SNOOZE_ACTION_OFFSET) {
            ringId - SNOOZE_RING_OFFSET
        } else {
            ringId
        }

    /** True when [ringId] is a snoozed re-ring rather than a scheduled firing. */
    fun isSnoozeRing(ringId: Int): Boolean =
        ringId >= SNOOZE_RING_OFFSET && ringId < SNOOZE_ACTION_OFFSET
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
         *  keeping it distinct from alarm (id), music and snooze ids. See [AlarmIds], which
         *  owns the full id map and re-exports this value. */
        const val RING_ID_OFFSET = 200000
    }
}

/**
 * One thing that happened to an alarm: it was armed, it rang, it was suppressed, it was
 * snoozed, it was dismissed, or it never rang at all.
 *
 * The app kept no record of any of this. A missed alarm and a quiet morning looked identical,
 * and an alarm dismissed half-asleep at 05:58 left no trace either. Every row here answers one
 * of those two questions, and together they show which of the ways a ring can be lost is
 * actually happening on this phone.
 */
@Entity(
    tableName = "alarm_events",
    indices = [Index("alarmId"), Index("actualAt")]
)
data class AlarmEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // The alarm row this belongs to. Always the ORIGINAL alarm id, never a snooze or timer-ring
    // offset id, so a whole morning — first ring, every snooze, the final dismiss — groups
    // under one alarm.
    val alarmId: Int,
    val type: String = TYPE_ALARM,     // ALARM | MUSIC | TIMER
    val label: String = "",

    val event: String,
    // When this was meant to happen, and when it really did. The gap between them is the delay
    // the OS added, which is what exposes Doze deferral.
    val scheduledAt: Long = 0L,
    val actualAt: Long = 0L,

    // --- ring details ---
    // How long the alarm had been sounding when this happened. 0 for non-ring events.
    val ringDurationMs: Long = 0L,

    // --- dismiss details ---
    val dismissSource: String = SOURCE_NONE,
    val challengeType: String = "NONE",     // NONE | MATH | PHRASE | MEMORY
    val challengeDifficulty: String = "",
    val challengeRounds: Int = 0,           // rounds the alarm required
    val challengeAttempts: Int = 0,         // answers given, wrong ones included
    val challengeSolvedMs: Long = 0L,       // opening the challenge to solving it

    // --- snooze details ---
    val snoozeIndex: Int = 0,               // 1 = the first snooze of this ring
    val snoozeGapMinutes: Int = 0,          // the gap this snooze actually used
    val snoozeMode: String = "",            // FIXED | PROGRESSIVE
    val snoozeLimit: Int = 0,               // 0 = unlimited
    val nextRingAt: Long = 0L,              // when the snoozed re-ring is due

    // --- device state, for working out why a ring was late or missing ---
    val screenOn: Boolean = false,
    val deviceLocked: Boolean = false,
    val dozeIdle: Boolean = false,
    val exactAllowed: Boolean = true,

    // Free text: an exception message, which suppression rule applied, and so on.
    val detail: String = ""
) {
    /** True for the events that represent an alarm actually making a noise. */
    fun isRing(): Boolean = event == FIRED

    companion object {
        const val TYPE_ALARM = "ALARM"
        const val TYPE_MUSIC = "MUSIC"
        const val TYPE_TIMER = "TIMER"

        // --- events ---
        /** Armed with AlarmManager. [scheduledAt] carries the trigger time it was armed for. */
        const val SCHEDULED = "SCHEDULED"
        /** The alarm was switched off or deleted, so its pending firing was cancelled. */
        const val CANCELLED = "CANCELLED"
        /** The receiver decided this ring goes ahead. */
        const val FIRED = "FIRED"
        const val SUPPRESSED_PAUSE = "SUPPRESSED_PAUSE"
        const val SUPPRESSED_SKIP = "SUPPRESSED_SKIP"
        const val SUPPRESSED_HOLIDAY = "SUPPRESSED_HOLIDAY"
        /** The ring could not be started at all (foreground service refused, and so on). */
        const val RING_FAILED = "RING_FAILED"
        /** A second alarm fired while another was ringing and had to wait its turn. */
        const val QUEUED = "QUEUED"
        const val SNOOZED = "SNOOZED"
        const val DISMISSED = "DISMISSED"
        /** The auto-silence timer stopped the ring; nobody dismissed it. */
        const val AUTO_SILENCED = "AUTO_SILENCED"
        /** Worked out afterwards: it was armed for a past time and never rang. */
        const val MISSED = "MISSED"
        const val RESCHEDULED_BOOT = "RESCHEDULED_BOOT"
        const val RESCHEDULED_WATCHDOG = "RESCHEDULED_WATCHDOG"

        // --- how a dismiss happened ---
        const val SOURCE_NONE = "NONE"
        /** Dismissed on the full-screen alarm screen. */
        const val SOURCE_FULL_SCREEN = "FULL_SCREEN"
        /** Dismissed from the notification shade, without opening the alarm screen. */
        const val SOURCE_NOTIFICATION = "NOTIFICATION"
        const val SOURCE_AUTO_SILENCE = "AUTO_SILENCE"

        /** Events that mean the ring definitely happened, for the missed-alarm check. */
        val RANG_OR_HANDLED = setOf(
            FIRED, SUPPRESSED_PAUSE, SUPPRESSED_SKIP, SUPPRESSED_HOLIDAY,
            RING_FAILED, QUEUED, CANCELLED, MISSED
        )

        /** Quieter events: real, always listed, but not what the user is usually looking for. */
        val SYSTEM_EVENTS = setOf(
            SCHEDULED, CANCELLED, RESCHEDULED_BOOT, RESCHEDULED_WATCHDOG
        )
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
    override val hour: Int,
    override val minute: Int,
    val durationMinutes: Int = 30,
    val label: String = "",
    val isEnabled: Boolean = true,
    override val daysOfWeek: String = "",    // "1,2,3,4,5,6,7" (1=Monday...7=Sunday)
    val musicTrackName: String = "Lo-Fi Beats", // Newline-separated ambient melody names
    val customFileUri: String = "", // Newline-separated "uri\tDisplayName" entries
    val volume: Float = 0.6f
) : Scheduled {
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
