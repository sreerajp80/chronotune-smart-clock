package `in`.sreerajp.chronotune_smart_clock.ui

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import `in`.sreerajp.chronotune_smart_clock.audio.AudioEngine
import `in`.sreerajp.chronotune_smart_clock.data.AppDatabase
import `in`.sreerajp.chronotune_smart_clock.data.repository.ClockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

// Live React State that your MainActivity and screens can observe
object ActiveAlarmState {
    data class ActiveAlarm(
        val id: Int,
        val type: String, // "ALARM" or "MUSIC"
        val label: String,
        val tone: String,
        val volume: Float,
        val durationMin: Int = 0,
        val uri: String? = null,
        val snoozeMinutes: Int = 5,
        val dismissChallenge: String = "NONE",
        val challengeDifficulty: String = "EASY",
        val challengeCount: Int = 1,
        // Minutes the ring keeps sounding before it silences itself; 0 = ring until dismissed.
        val autoSilenceMinutes: Int = 0,
        // Largest number of snoozes allowed; 0 = unlimited.
        val maxSnoozeCount: Int = 0,
        // FIXED or PROGRESSIVE — how the gap changes from one snooze to the next.
        val snoozeMode: String = "FIXED",
        // How many times this alarm has already been snoozed. Carried through the snooze
        // intent chain rather than stored, so it resets by itself when the alarm next fires
        // from its own schedule.
        val snoozeCount: Int = 0
    ) {
        /** True while the user may still snooze this ring. */
        fun canSnooze(): Boolean =
            `in`.sreerajp.chronotune_smart_clock.data.canSnoozeAgain(maxSnoozeCount, snoozeCount)

        /** Minutes the next snooze will wait, honoring the progressive mode. */
        fun nextSnoozeGapMinutes(): Int =
            `in`.sreerajp.chronotune_smart_clock.data.snoozeGapMinutes(
                snoozeMinutes, snoozeMode, snoozeCount
            )

        /** Snoozes still available, or null when unlimited. */
        fun snoozesRemaining(): Int? =
            if (maxSnoozeCount <= 0) null else (maxSnoozeCount - snoozeCount).coerceAtLeast(0)
    }

    private val _activeAlarm = MutableStateFlow<ActiveAlarm?>(null)
    val activeAlarm = _activeAlarm.asStateFlow()

    // Safe to hold statically: AudioEngine only ever stores the application context (see its
    // constructor), which lives for the whole process — so there is no Activity/Context leak.
    @SuppressLint("StaticFieldLeak")
    private var audioEngine: AudioEngine? = null

    fun triggerAlarm(context: Context, alarm: ActiveAlarm) {
        _activeAlarm.value = alarm
        if (audioEngine == null) {
            audioEngine = AudioEngine(context.applicationContext)
        }

        if (alarm.type == "MUSIC") {
            val durationMs = alarm.durationMin * 60 * 1000L
            val ambients = alarm.tone.split("\n").map { it.trim() }.filter { it.isNotBlank() }
            val files = (alarm.uri ?: "").split("\n").mapNotNull { line ->
                val parts = line.split("\t", limit = 2)
                val uri = parts.getOrNull(0)?.trim().orEmpty()
                if (uri.isBlank()) null else uri
            }
            val playlist = buildList {
                ambients.forEach { add(AudioEngine.PlaylistItem(it, null)) }
                files.forEach { add(AudioEngine.PlaylistItem("Custom", it)) }
            }
            if (playlist.isEmpty()) {
                audioEngine?.playAudio(alarm.tone, alarm.uri, alarm.volume, durationMs)
            } else {
                // Music schedules honor the configurable crossfade settings (read straight from prefs
                // so they're correct even in a cold process). getCrossfadeMs() returns 0 when disabled.
                val xfMs = `in`.sreerajp.chronotune_smart_clock.AppPrefs.getCrossfadeMs(context)
                val curve = `in`.sreerajp.chronotune_smart_clock.AppPrefs.getCrossfadeCurve(context)
                val normalize = `in`.sreerajp.chronotune_smart_clock.AppPrefs.isLoudnessNormalize(context)
                audioEngine?.playPlaylist(playlist, alarm.volume, durationMs, xfMs, curve, normalize)
            }
        } else {
            // Alarms honor the global fade-in setting; read straight from prefs so it's correct
            // even when we're firing in a cold process. Music (above) stays at flat volume.
            val fadeInMs = `in`.sreerajp.chronotune_smart_clock.AppPrefs.getFadeInMs(context)
            audioEngine?.playAudio(alarm.tone, alarm.uri, alarm.volume, null, fadeInMs)
        }
    }

    fun dismiss(context: Context? = null) {
        val current = _activeAlarm.value
        audioEngine?.stop()
        _activeAlarm.value = null
        if (context != null && current != null) {
            cancelNotification(context, current.id)
        }
    }

    fun snooze(context: Context, snoozeMinutes: Int = 5) {
        val current = _activeAlarm.value ?: return
        // The limit is enforced here as well as in the UI, so a stale notification action or a
        // race can never buy an extra snooze past the configured maximum.
        if (!current.canSnooze()) {
            Log.d("ActiveAlarmState", "Snooze limit reached for ${current.id} — ignoring snooze")
            return
        }
        audioEngine?.stop()
        _activeAlarm.value = null
        cancelNotification(context, current.id)
        scheduleSnooze(
            context = context,
            id = current.id,
            label = current.label,
            tone = current.tone,
            uri = current.uri,
            volume = current.volume,
            snoozeMinutes = snoozeMinutes,
            dismissChallenge = current.dismissChallenge,
            challengeDifficulty = current.challengeDifficulty,
            challengeCount = current.challengeCount,
            autoSilenceMinutes = current.autoSilenceMinutes,
            maxSnoozeCount = current.maxSnoozeCount,
            snoozeMode = current.snoozeMode,
            snoozeCount = current.snoozeCount
        )
    }

    // Re-arms a one-shot alarm a snooze-gap from now. Safe to call from a BroadcastReceiver
    // even after the process was killed, because it doesn't depend on _activeAlarm being set.
    //
    // [snoozeCount] is how many snoozes already happened; this call is number snoozeCount + 1.
    // Returns false and does nothing when the alarm has run out of snoozes.
    fun scheduleSnooze(
        context: Context,
        id: Int,
        label: String,
        tone: String,
        uri: String? = null,
        volume: Float,
        snoozeMinutes: Int = 5,
        dismissChallenge: String = "NONE",
        challengeDifficulty: String = "EASY",
        challengeCount: Int = 1,
        autoSilenceMinutes: Int = 0,
        maxSnoozeCount: Int = 0,
        snoozeMode: String = "FIXED",
        snoozeCount: Int = 0
    ): Boolean {
        if (!`in`.sreerajp.chronotune_smart_clock.data.canSnoozeAgain(maxSnoozeCount, snoozeCount)) {
            Log.d("ActiveAlarmState", "Snooze limit ($maxSnoozeCount) reached for $id — not re-arming")
            return false
        }
        // The gap shrinks with each snooze in PROGRESSIVE mode. snoozeMinutes stays the base
        // value on the re-armed alarm so the next gap is still computed from the original.
        val gapMinutes = `in`.sreerajp.chronotune_smart_clock.data.snoozeGapMinutes(
            snoozeMinutes, snoozeMode, snoozeCount
        )
        val scheduler = AlarmScheduler(context)
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.MINUTE, gapMinutes)

        val tempAlarm = `in`.sreerajp.chronotune_smart_clock.data.Alarm(
            id = id + 50000, // Safe offset for temporary snooze alarm
            hour = cal.get(java.util.Calendar.HOUR_OF_DAY),
            minute = cal.get(java.util.Calendar.MINUTE),
            label = "$label (Snoozed)",
            isEnabled = true,
            customToneName = tone,
            // Carry the custom tone URI through so a picked-file / device-ringtone alarm
            // keeps its own sound on snooze instead of falling back to the system default.
            customToneUri = uri ?: "",
            volume = volume,
            snoozeMinutes = snoozeMinutes,
            // Keep the challenge on the snoozed re-ring, so a user can't snooze once and
            // then dismiss with a single tap.
            dismissChallenge = dismissChallenge,
            challengeDifficulty = challengeDifficulty,
            challengeCount = challengeCount,
            // Keep the same auto-silence behavior on the snoozed re-ring.
            autoSilenceMinutes = autoSilenceMinutes,
            // Keep the limit and style so the re-ring knows how many snoozes are left.
            maxSnoozeCount = maxSnoozeCount,
            snoozeMode = snoozeMode
        )
        scheduler.scheduleAlarm(tempAlarm, snoozeCount = snoozeCount + 1)
        return true
    }

    private fun cancelNotification(context: Context, id: Int) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(id)
        } catch (_: Exception) { /* ignore */ }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val holidayMode = intent.getStringExtra("HOLIDAY_MODE")
            ?: `in`.sreerajp.chronotune_smart_clock.data.Alarm.HOLIDAY_MODE_ALL_DAYS

        // A holiday-aware alarm needs the day list before it can decide whether to ring and
        // where to re-arm. In a cold process (first firing after a reboot) the registry is
        // still empty, so load it first and only then handle the firing. Everything else
        // takes the plain inline path.
        if (holidayMode != `in`.sreerajp.chronotune_smart_clock.data.Alarm.HOLIDAY_MODE_ALL_DAYS &&
            !`in`.sreerajp.chronotune_smart_clock.data.SpecialDayRegistry.isLoaded
        ) {
            val pending = goAsync()
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    `in`.sreerajp.chronotune_smart_clock.data.SpecialDayRegistry.refresh(appContext)
                } catch (e: Exception) {
                    // Failing to load leaves the map empty, which means "no holidays known".
                    // The alarm then rings as it always did — never silently lost.
                    Log.e("AlarmReceiver", "Could not load special days: ${e.message}")
                }
                try {
                    handleFiring(appContext, intent, canGoAsync = false)
                } finally {
                    pending.finish()
                }
            }
            return
        }

        handleFiring(context, intent, canGoAsync = true)
    }

    // [canGoAsync] is false when we are already running inside a goAsync() block, because
    // goAsync() may only be called while onReceive is still on the stack.
    private fun handleFiring(context: Context, intent: Intent, canGoAsync: Boolean) {
        val id = intent.getIntExtra("ID", -1)
        val type = intent.getStringExtra("TYPE") ?: "ALARM"
        val label = intent.getStringExtra("LABEL") ?: "Alarm Ringing"
        val tone = intent.getStringExtra("TONE") ?: "Morning Breeze"
        val uri = intent.getStringExtra("URI") ?: ""
        val volume = intent.getFloatExtra("VOLUME", 0.8f)
        val durationMin = intent.getIntExtra("DURATION_MIN", 0)
        val snoozeMin = intent.getIntExtra("SNOOZE_MIN", 5)
        val challenge = intent.getStringExtra("CHALLENGE") ?: "NONE"
        val challengeDifficulty = intent.getStringExtra("CHALLENGE_DIFFICULTY") ?: "EASY"
        val challengeCount = intent.getIntExtra("CHALLENGE_COUNT", 1)
        val autoSilenceMin = intent.getIntExtra("AUTO_SILENCE_MIN", 0)
        val days = intent.getStringExtra("DAYS") ?: ""
        val pauseStart = intent.getLongExtra("PAUSE_START", 0L)
        val pauseEnd = intent.getLongExtra("PAUSE_END", 0L)
        val skipEpochDay = intent.getLongExtra("SKIP_EPOCH_DAY", 0L)
        val holidayMode = intent.getStringExtra("HOLIDAY_MODE")
            ?: `in`.sreerajp.chronotune_smart_clock.data.Alarm.HOLIDAY_MODE_ALL_DAYS
        val maxSnoozeCount = intent.getIntExtra("MAX_SNOOZE_COUNT", 0)
        val snoozeMode = intent.getStringExtra("SNOOZE_MODE") ?: "FIXED"
        val snoozeCount = intent.getIntExtra("SNOOZE_COUNT", 0)
        val startEpochDay = intent.getLongExtra("START_EPOCH_DAY", 0L)

        Log.d("AlarmReceiver", "Alarm occurred! Type: $type, Label: $label, ID: $id")

        // AlarmManager one-shots don't repeat. For a repeating alarm/schedule we must re-arm
        // the next occurrence ourselves now that this one has fired — otherwise it rings once
        // and never again. (One-shot alarms with no selected days are intentionally left to
        // simply not repeat.)
        if (days.isNotBlank()) {
            rescheduleNextOccurrence(context, intent, type, id, label, tone, uri, volume, durationMin, days, pauseStart, pauseEnd, skipEpochDay, holidayMode, startEpochDay)
        }

        // Safety guard: pause-aware scheduling should already keep this from firing during the
        // pause window, but if a stale alarm slips through, suppress the ring (the re-arm above
        // still lands the next occurrence after the window).
        if (type == "ALARM" && isPausedNow(pauseStart, pauseEnd)) {
            Log.d("AlarmReceiver", "Alarm $id is within its pause window — suppressing ring")
            return
        }

        // Safety guard: skip-aware scheduling should already keep this from firing on the skipped
        // day, but if a stale alarm slips through, suppress the ring for that one occurrence.
        if (type == "ALARM" && skipEpochDay > 0L &&
            skipEpochDay == `in`.sreerajp.chronotune_smart_clock.data.Alarm.todayEpochDay()) {
            Log.d("AlarmReceiver", "Alarm $id is on its skipped day — suppressing ring")
            return
        }

        // Safety guard: holiday-aware scheduling should already avoid landing on a marked
        // holiday, but a pending alarm armed before the day was marked would still fire.
        // Suppress it here (the re-arm above has already found the next allowed day).
        if (type == "ALARM" && isHolidayToday(holidayMode)) {
            Log.d("AlarmReceiver", "Alarm $id falls on a holiday — suppressing ring")
            return
        }

        // Hand off to a foreground service. The service owns the audio + notification +
        // activity launch so the process can't be reaped mid-alarm, dismiss reliably stops
        // playback, and the OS grants BAL exemption to launch the full-screen UI.
        // For a TIMER the carried id is already offset (timer.id + RING_ID_OFFSET) so the
        // AlarmService foreground-notification id can't collide with alarms/music.
        val alarm = ActiveAlarmState.ActiveAlarm(
            id, type, label, tone, volume, durationMin, uri, snoozeMin,
            challenge, challengeDifficulty, challengeCount, autoSilenceMin,
            maxSnoozeCount, snoozeMode, snoozeCount
        )
        ContextCompat.startForegroundService(context, AlarmService.startIntent(context, alarm))

        // A one-shot pinned to a date has now done its single job, so switch the row off —
        // otherwise it would sit in the list looking armed weeks after its date. A one-shot
        // with no date keeps the old behaviour and is left alone.
        //
        // This runs only after the ring has been handed to the service above, and any failure
        // is swallowed: the worst case is a row that stays lit and is turned off by hand. The
        // alarm must never depend on this write.
        if (type == "ALARM" && days.isBlank() && startEpochDay > 0L) {
            val pending = if (canGoAsync) goAsync() else null
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(appContext)
                    db.alarmDao().getAlarmById(id)?.let { row ->
                        db.alarmDao().updateAlarm(row.copy(isEnabled = false))
                    }
                } catch (e: Exception) {
                    Log.e("AlarmReceiver", "Could not switch off dated one-shot $id: ${e.message}")
                } finally {
                    pending?.finish()
                }
            }
        }

        // A fired timer is a one-shot: mark it FINISHED in the DB and refresh the live
        // stopwatch/timer notifications. The ring itself is handled by AlarmService above.
        if (type == "TIMER") {
            val timerId = intent.getIntExtra("TIMER_ID", -1)
            if (timerId >= 0) {
                // goAsync() is only legal while onReceive is still on the stack. A timer never
                // takes the holiday pre-load path, so canGoAsync is always true here; the null
                // fallback just keeps this correct if that ever changes.
                val pending = if (canGoAsync) goAsync() else null
                val appContext = context.applicationContext
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getDatabase(appContext)
                        val repo = ClockRepository(
                            db.alarmDao(), db.worldClockDao(), db.musicScheduleDao(),
                            db.timerDao(), db.timerPresetDao()
                        )
                        TimerEngine.markFinished(repo, appContext, timerId)
                    } catch (e: Exception) {
                        Log.e("AlarmReceiver", "Failed to mark timer finished: ${e.message}")
                    } finally {
                        pending?.finish()
                    }
                }
            }
        }
    }

    private fun isPausedNow(pauseStartMillis: Long, pauseEndMillis: Long): Boolean {
        if (pauseStartMillis <= 0L || pauseEndMillis <= 0L) return false
        val today = `in`.sreerajp.chronotune_smart_clock.data.Alarm.todayEpochDay()
        val perDay = `in`.sreerajp.chronotune_smart_clock.data.Alarm.MILLIS_PER_DAY
        return today in (pauseStartMillis / perDay)..(pauseEndMillis / perDay)
    }

    /**
     * True when the alarm cares about holidays and today is marked as one. Reads the in-memory
     * registry, which the caller has already made sure is loaded.
     */
    private fun isHolidayToday(holidayMode: String): Boolean {
        val alarmType = `in`.sreerajp.chronotune_smart_clock.data.Alarm
        if (holidayMode == alarmType.HOLIDAY_MODE_ALL_DAYS) return false
        val kind = `in`.sreerajp.chronotune_smart_clock.data.SpecialDayRegistry
            .kindOf(alarmType.todayEpochDay())
        return kind == `in`.sreerajp.chronotune_smart_clock.data.SpecialDay.KIND_HOLIDAY
    }

    private fun rescheduleNextOccurrence(
        context: Context,
        intent: Intent,
        type: String,
        id: Int,
        label: String,
        tone: String,
        uri: String,
        volume: Float,
        durationMin: Int,
        days: String,
        pauseStart: Long,
        pauseEnd: Long,
        skipEpochDay: Long,
        holidayMode: String,
        startEpochDay: Long
    ) {
        val hour = intent.getIntExtra("HOUR", -1)
        val minute = intent.getIntExtra("MINUTE", -1)
        if (hour < 0 || minute < 0) return

        val scheduler = AlarmScheduler(context)
        try {
            if (type == "MUSIC") {
                scheduler.scheduleMusic(
                    `in`.sreerajp.chronotune_smart_clock.data.MusicSchedule(
                        id = id,
                        hour = hour,
                        minute = minute,
                        durationMinutes = durationMin,
                        label = label,
                        isEnabled = true,
                        daysOfWeek = days,
                        musicTrackName = tone,
                        customFileUri = uri,
                        volume = volume
                    )
                )
            } else {
                scheduler.scheduleAlarm(
                    `in`.sreerajp.chronotune_smart_clock.data.Alarm(
                        id = id,
                        hour = hour,
                        minute = minute,
                        label = label,
                        isEnabled = true,
                        daysOfWeek = days,
                        customToneName = tone,
                        customToneUri = uri,
                        volume = volume,
                        snoozeMinutes = intent.getIntExtra("SNOOZE_MIN", 5),
                        isVibrate = intent.getBooleanExtra("VIBRATE", true),
                        dismissChallenge = intent.getStringExtra("CHALLENGE") ?: "NONE",
                        challengeDifficulty = intent.getStringExtra("CHALLENGE_DIFFICULTY") ?: "EASY",
                        challengeCount = intent.getIntExtra("CHALLENGE_COUNT", 1),
                        pauseStartMillis = pauseStart,
                        pauseEndMillis = pauseEnd,
                        autoSilenceMinutes = intent.getIntExtra("AUTO_SILENCE_MIN", 0),
                        skipNextEpochDay = skipEpochDay,
                        holidayMode = holidayMode,
                        maxSnoozeCount = intent.getIntExtra("MAX_SNOOZE_COUNT", 0),
                        snoozeMode = intent.getStringExtra("SNOOZE_MODE") ?: "FIXED",
                        startEpochDay = startEpochDay
                    )
                    // A fresh scheduled firing always starts with zero snoozes used.
                )
            }
            Log.d("AlarmReceiver", "Re-armed next occurrence for $type id=$id (days=$days)")
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Failed to re-arm next occurrence: ${e.message}")
        }
    }
}

/**
 * Re-arms persisted alarms and music schedules after the device reboots (alarms set with
 * AlarmManager don't survive reboot) or when the system clock changes.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Received: $action")
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> rescheduleAll(context)
        }
    }

    private fun rescheduleAll(context: Context) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(appContext)
                val repo = ClockRepository(
                    db.alarmDao(), db.worldClockDao(), db.musicScheduleDao(),
                    db.timerDao(), db.timerPresetDao()
                )
                val scheduler = AlarmScheduler(appContext)

                // Load the holiday / work-day list before scheduling, so a holiday-aware alarm
                // lands on the right day instead of the next raw weekday.
                `in`.sreerajp.chronotune_smart_clock.data.SpecialDayRegistry.refresh(appContext)

                repo.allAlarms.first().forEach { alarm ->
                    if (alarm.isEnabled) scheduler.scheduleAlarm(alarm)
                }
                repo.allMusicSchedules.first().forEach { schedule ->
                    if (schedule.isEnabled) scheduler.scheduleMusic(schedule)
                }
                // Re-arm running timers: elapsedRealtime reset on reboot, so recompute the
                // display base from the persisted RTC target and re-schedule the ring.
                TimerEngine.rescheduleAllAfterBoot(repo, appContext)
                Log.d("BootReceiver", "Rescheduled alarms, music schedules and timers")
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to reschedule: ${e.message}")
            } finally {
                // Resume the analog widget tick + force an immediate redraw of any live widgets
                // (handles BOOT, package replace, time-set, and timezone-change cases uniformly).
                try {
                    `in`.sreerajp.chronotune_smart_clock.widget.AnalogClockWidgetProvider.scheduleNextTick(appContext)
                    `in`.sreerajp.chronotune_smart_clock.widget.AnalogClockWidgetProvider.renderAll(appContext)
                    `in`.sreerajp.chronotune_smart_clock.widget.DigitalClockWidgetProvider.renderAll(appContext)
                } catch (_: Exception) { }
                pending.finish()
            }
        }
    }
}

class AlarmDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Stop the foreground service — it owns the audio engine and the ongoing
        // notification, so this tears down everything atomically. Going through the
        // service avoids the race where the receiver runs in a cold process and
        // audioEngine?.stop() is a no-op against a stale singleton.
        try {
            context.startService(AlarmService.stopIntent(context))
        } catch (e: Exception) {
            Log.e("AlarmDismissReceiver", "Failed to stop alarm service: ${e.message}")
            // Best-effort fallback if the service can't be reached.
            ActiveAlarmState.dismiss(context)
            val notifId = intent.getIntExtra("NOTIFICATION_ID", -1)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notifId != -1) nm.cancel(notifId) else nm.cancelAll()
        }
    }
}

/**
 * "+1 min" action on a finished timer's ring notification. Stops the current ring, then puts the
 * timer back to RUNNING for another minute (see [TimerEngine.restartForOneMinute]).
 */
class TimerAddMinuteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val timerId = intent.getIntExtra("TIMER_ID", -1)

        // Stop the currently-ringing timer via the service (audio + notification + foreground).
        try {
            context.startService(AlarmService.stopIntent(context))
        } catch (_: Exception) {
            ActiveAlarmState.dismiss(context)
        }

        if (timerId < 0) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(appContext)
                val repo = ClockRepository(
                    db.alarmDao(), db.worldClockDao(), db.musicScheduleDao(),
                    db.timerDao(), db.timerPresetDao()
                )
                TimerEngine.restartForOneMinute(repo, appContext, timerId)
            } catch (e: Exception) {
                Log.e("TimerAddMinuteReceiver", "Failed to add a minute: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}

class AlarmSnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra("NOTIFICATION_ID", -1)
        val id = intent.getIntExtra("ID", notifId)
        val label = intent.getStringExtra("LABEL") ?: "Alarm"
        val tone = intent.getStringExtra("TONE") ?: "Morning Breeze"
        val uri = intent.getStringExtra("URI") ?: ""
        val volume = intent.getFloatExtra("VOLUME", 0.8f)
        val snoozeMin = intent.getIntExtra("SNOOZE_MIN", 5)
        val challenge = intent.getStringExtra("CHALLENGE") ?: "NONE"
        val challengeDifficulty = intent.getStringExtra("CHALLENGE_DIFFICULTY") ?: "EASY"
        val challengeCount = intent.getIntExtra("CHALLENGE_COUNT", 1)
        val autoSilenceMin = intent.getIntExtra("AUTO_SILENCE_MIN", 0)
        val maxSnoozeCount = intent.getIntExtra("MAX_SNOOZE_COUNT", 0)
        val snoozeMode = intent.getStringExtra("SNOOZE_MODE") ?: "FIXED"
        val snoozeCount = intent.getIntExtra("SNOOZE_COUNT", 0)

        // Tear down the currently-ringing alarm via the service (audio + notification +
        // foreground state all go together).
        try {
            context.startService(AlarmService.stopIntent(context))
        } catch (_: Exception) {
            ActiveAlarmState.dismiss(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notifId != -1) nm.cancel(notifId)
        }

        // Re-arm the alarm to fire again after the snooze period. This refuses by itself once
        // the snooze limit is reached, so a stale notification cannot buy an extra snooze.
        ActiveAlarmState.scheduleSnooze(
            context = context,
            id = id,
            label = label,
            tone = tone,
            uri = uri,
            volume = volume,
            snoozeMinutes = snoozeMin,
            dismissChallenge = challenge,
            challengeDifficulty = challengeDifficulty,
            challengeCount = challengeCount,
            autoSilenceMinutes = autoSilenceMin,
            maxSnoozeCount = maxSnoozeCount,
            snoozeMode = snoozeMode,
            snoozeCount = snoozeCount
        )
    }
}
