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
        val snoozeCount: Int = 0,
        // The alarm row this ring belongs to. Same as [id] for a scheduled firing; for a
        // snoozed re-ring [id] lives in the snooze id space while this stays the original
        // alarm, so a whole morning can be traced back to one alarm.
        val baseId: Int = id
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
            snoozeCount = current.snoozeCount,
            baseAlarmId = current.baseId,
            source = `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.SOURCE_FULL_SCREEN
        )
    }

    // Re-arms a one-shot alarm a snooze-gap from now. Safe to call from a BroadcastReceiver
    // even after the process was killed, because it doesn't depend on _activeAlarm being set.
    //
    // [snoozeCount] is how many snoozes already happened; this call is number snoozeCount + 1.
    // Returns false and does nothing when the alarm has run out of snoozes.
    //
    // [id] is the id that is ringing right now (which is itself a snooze id from the second
    // snooze onwards); [baseAlarmId] is the alarm row it all started from. The re-ring is
    // always armed at AlarmIds.snoozeRing(baseAlarmId), so a chain of any length reuses one
    // slot instead of climbing 50000 at a time into the timer id space.
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
        snoozeCount: Int = 0,
        baseAlarmId: Int = `in`.sreerajp.chronotune_smart_clock.data.AlarmIds.baseAlarmId(id),
        // Where the snooze was taken: the ringing screen or the notification shade. Recorded
        // in the history, because reaching for the shade is a different act from tapping the
        // big button on the alarm screen.
        source: String = `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.SOURCE_NONE
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
            id = `in`.sreerajp.chronotune_smart_clock.data.AlarmIds.snoozeRing(baseAlarmId),
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
        scheduler.scheduleAlarm(
            tempAlarm,
            snoozeCount = snoozeCount + 1,
            baseAlarmId = baseAlarmId
        )

        // The whole snooze, written down: which one in the chain it was, the gap it actually
        // used, the allowance it is spending, when it will ring again, and how long the alarm
        // had been sounding before the user reached for it.
        `in`.sreerajp.chronotune_smart_clock.data.AlarmEventLog.record(
            context,
            `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent(
                alarmId = baseAlarmId,
                type = `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.TYPE_ALARM,
                label = label,
                event = `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.SNOOZED,
                ringDurationMs = AlarmService.ringDurationMs(),
                dismissSource = source,
                challengeType = dismissChallenge,
                challengeDifficulty = challengeDifficulty,
                challengeRounds = challengeCount,
                snoozeIndex = snoozeCount + 1,
                snoozeGapMinutes = gapMinutes,
                snoozeMode = snoozeMode,
                snoozeLimit = maxSnoozeCount,
                nextRingAt = cal.timeInMillis
            )
        )
        return true
    }

    /**
     * Records that a ring was turned off from a screen the user was looking at.
     *
     * [challengeAttempts] and [challengeMs] describe the wake-up challenge, and are 0 when the
     * alarm has none. Together with the ring duration they are what answers "did I dismiss this
     * in my sleep?" — a challenge solved first-try in four seconds at 05:58 reads very
     * differently from one that took six attempts.
     */
    fun recordDismiss(
        context: Context,
        ring: ActiveAlarm,
        source: String,
        challengeAttempts: Int = 0,
        challengeMs: Long = 0L
    ) {
        `in`.sreerajp.chronotune_smart_clock.data.AlarmEventLog.record(
            context,
            `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent(
                alarmId = ring.baseId,
                type = ring.type,
                label = ring.label,
                event = `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.DISMISSED,
                ringDurationMs = AlarmService.ringDurationMs(),
                dismissSource = source,
                challengeType = ring.dismissChallenge,
                challengeDifficulty = ring.challengeDifficulty,
                challengeRounds = ring.challengeCount,
                challengeAttempts = challengeAttempts,
                challengeSolvedMs = challengeMs,
                // How many snoozes this morning had already used before it finally stopped.
                snoozeIndex = ring.snoozeCount
            )
        )
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
        val baseId = intent.getIntExtra(
            "BASE_ID",
            `in`.sreerajp.chronotune_smart_clock.data.AlarmIds.baseAlarmId(id)
        )
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
        // The time this firing was armed for. Comparing it with now is what shows how late the
        // OS actually delivered the alarm.
        val scheduledAt = intent.getLongExtra("SCHEDULED_AT", 0L)

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
            logEvent(
                context, baseId, type, label, scheduledAt,
                `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.SUPPRESSED_PAUSE,
                "Inside the alarm's pause window"
            )
            return
        }

        // Safety guard: skip-aware scheduling should already keep this from firing on the skipped
        // day, but if a stale alarm slips through, suppress the ring for that one occurrence.
        if (type == "ALARM" && skipEpochDay > 0L &&
            skipEpochDay == `in`.sreerajp.chronotune_smart_clock.data.Alarm.todayEpochDay()) {
            Log.d("AlarmReceiver", "Alarm $id is on its skipped day — suppressing ring")
            logEvent(
                context, baseId, type, label, scheduledAt,
                `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.SUPPRESSED_SKIP,
                "Skip next was set for today"
            )
            return
        }

        // Safety guard: holiday-aware scheduling should already avoid landing on a marked
        // holiday, but a pending alarm armed before the day was marked would still fire.
        // Suppress it here (the re-arm above has already found the next allowed day).
        if (type == "ALARM" && isHolidayToday(holidayMode)) {
            Log.d("AlarmReceiver", "Alarm $id falls on a holiday — suppressing ring")
            logEvent(
                context, baseId, type, label, scheduledAt,
                `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.SUPPRESSED_HOLIDAY,
                "Today is marked as a holiday"
            )
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
            maxSnoozeCount, snoozeMode, snoozeCount, baseId
        )
        // Recorded before the hand-off, so the row exists even if starting the ring throws.
        logEvent(
            context, baseId, type, label, scheduledAt,
            `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.FIRED
        )
        try {
            ContextCompat.startForegroundService(context, AlarmService.startIntent(context, alarm))
        } catch (e: Exception) {
            // Android refuses a foreground-service start when the broadcast that woke us
            // carried no temporary allow-list (ForegroundServiceStartNotAllowedException).
            // Rather than lose the ring completely, send the full-screen intent directly so
            // the user still gets a ringing screen with Dismiss and Snooze on it.
            Log.e("AlarmReceiver", "Foreground service refused for $id, ringing in-process: ${e.message}")
            try {
                // Start the audio in this process and put the ringing screen up ourselves.
                // Without the service the process is no longer protected from being reaped
                // mid-ring, but a ring that might be cut short is far better than silence.
                ActiveAlarmState.triggerAlarm(context.applicationContext, alarm)
                AlarmService.fullScreenIntentFor(context, alarm).send()
                logEvent(
                    context, baseId, type, label, scheduledAt,
                    `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.RING_FAILED,
                    "Foreground service refused; rang in-process instead: ${e.message}"
                )
            } catch (e2: Exception) {
                Log.e("AlarmReceiver", "In-process ring fallback also failed for $id: ${e2.message}")
                logEvent(
                    context, baseId, type, label, scheduledAt,
                    `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.RING_FAILED,
                    "Could not ring at all: ${e2.message}"
                )
            }
        }

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

    /** Records one event about this firing. Best-effort: see AlarmEventLog. */
    private fun logEvent(
        context: Context,
        alarmId: Int,
        type: String,
        label: String,
        scheduledAt: Long,
        event: String,
        detail: String = ""
    ) {
        `in`.sreerajp.chronotune_smart_clock.data.AlarmEventLog.record(
            context,
            `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent(
                alarmId = alarmId,
                type = type,
                label = label,
                event = event,
                scheduledAt = scheduledAt,
                detail = detail
            )
        )
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
            Intent.ACTION_TIMEZONE_CHANGED ->
                rescheduleAll(
                    context,
                    dropLegacySnoozes = action == Intent.ACTION_MY_PACKAGE_REPLACED,
                    reason = action ?: "boot"
                )

            // Registered at runtime by the app (USER_PRESENT cannot be declared in the
            // manifest since Android 8). This is the safety net for the case where the phone
            // was rebooted overnight: the database is encrypted until the first unlock, so
            // BOOT_COMPLETED could not restore anything, and without this the morning alarm
            // would simply never have been armed.
            Intent.ACTION_USER_PRESENT -> repairOnly(context)
        }
    }

    companion object {
        @Volatile
        private var unlockWatchRegistered = false

        /**
         * Starts listening for the screen being unlocked, so alarms can be checked then.
         *
         * `ACTION_USER_PRESENT` cannot be declared in the manifest since Android 8, so it has
         * to be registered from running code. That also means it only helps while the process
         * is alive — the real protection against a force-stop is [WatchdogReceiver] plus the
         * check the app runs at start-up. Called once from MainActivity.
         */
        fun registerUnlockWatch(context: Context) {
            if (unlockWatchRegistered) return
            unlockWatchRegistered = true
            try {
                val filter = android.content.IntentFilter(Intent.ACTION_USER_PRESENT)
                ContextCompat.registerReceiver(
                    context.applicationContext,
                    BootReceiver(),
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            } catch (e: Exception) {
                Log.e("BootReceiver", "Could not watch for unlock: ${e.message}")
            }
        }
    }

    /** Re-arms only the alarms whose pending broadcast has gone missing. */
    private fun repairOnly(context: Context) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduler = AlarmScheduler(appContext)
                val repaired = WatchdogReceiver.repairMissingAlarms(appContext, scheduler)
                if (repaired > 0) {
                    Log.d("BootReceiver", "Unlock check re-armed $repaired alarm(s)")
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Unlock check failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private fun rescheduleAll(
        context: Context,
        dropLegacySnoozes: Boolean = false,
        reason: String = "boot"
    ) {
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
                    // An upgrade from a pre-fix build can carry a snooze left pending in the
                    // old, growing id space. Nothing arms those any more, so clear them once
                    // rather than let a stray re-ring go off days later.
                    if (dropLegacySnoozes) scheduler.cancelLegacySnoozes(alarm.id)
                    if (alarm.isEnabled) scheduler.scheduleAlarm(alarm)
                }
                repo.allMusicSchedules.first().forEach { schedule ->
                    if (schedule.isEnabled) scheduler.scheduleMusic(schedule)
                }
                // Re-arm running timers: elapsedRealtime reset on reboot, so recompute the
                // display base from the persisted RTC target and re-schedule the ring.
                TimerEngine.rescheduleAllAfterBoot(repo, appContext)
                // AlarmManager alarms do not survive a reboot, and that includes the watchdog
                // itself — arm it again so the repair chain keeps running.
                scheduler.scheduleWatchdog()
                Log.d("BootReceiver", "Rescheduled alarms, music schedules and timers")
                `in`.sreerajp.chronotune_smart_clock.data.AlarmEventLog.record(
                    appContext,
                    `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent(
                        alarmId = 0,
                        event = `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.RESCHEDULED_BOOT,
                        detail = reason
                    )
                )
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
        // Recorded before the teardown, while the ring details are still readable. A dismiss
        // taken here never opened the alarm screen — the user swiped down and tapped Dismiss.
        AlarmService.currentRing()?.let { ring ->
            `in`.sreerajp.chronotune_smart_clock.data.AlarmEventLog.record(
                context,
                `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent(
                    alarmId = ring.baseId,
                    type = ring.type,
                    label = ring.label,
                    event = `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.DISMISSED,
                    ringDurationMs = AlarmService.ringDurationMs(),
                    dismissSource = `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.SOURCE_NOTIFICATION,
                    snoozeIndex = ring.snoozeCount
                )
            )
        }

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
        val baseId = intent.getIntExtra(
            "BASE_ID",
            `in`.sreerajp.chronotune_smart_clock.data.AlarmIds.baseAlarmId(id)
        )
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
            snoozeCount = snoozeCount,
            baseAlarmId = baseId,
            source = `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.SOURCE_NOTIFICATION
        )
    }
}

/**
 * Repairs alarms the system has silently thrown away.
 *
 * Android drops every pending alarm an app owns when the app is force-stopped, when an OEM
 * battery cleaner kills it, or when it is hibernated for being unused. Nothing tells the app
 * that this happened, and before this receiver existed the alarm simply never rang again until
 * the user rebooted or edited it.
 *
 * Fires roughly every few hours (see [AppPrefs.WATCHDOG_INTERVAL_MS]), re-arms itself, and
 * rebuilds only the alarms whose PendingIntent has actually gone missing.
 */
class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val scheduler = AlarmScheduler(appContext)
            // Re-arm the next watchdog first, so a failure below can never end the chain.
            try {
                scheduler.scheduleWatchdog()
            } catch (e: Exception) {
                Log.e("WatchdogReceiver", "Could not re-arm watchdog: ${e.message}")
            }
            try {
                val repaired = repairMissingAlarms(appContext, scheduler)
                Log.d("WatchdogReceiver", "Watchdog checked alarms, re-armed $repaired")
                if (repaired > 0) {
                    // Only worth a row when something was actually broken: a watchdog run that
                    // finds everything in order is not news, and would bury the log.
                    `in`.sreerajp.chronotune_smart_clock.data.AlarmEventLog.record(
                        appContext,
                        `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent(
                            alarmId = 0,
                            event = `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.RESCHEDULED_WATCHDOG,
                            detail = "Re-armed $repaired alarm(s) the system had dropped"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("WatchdogReceiver", "Watchdog check failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /**
         * Re-arms every enabled alarm and music schedule whose pending broadcast has vanished,
         * and returns how many were rebuilt.
         *
         * Only the missing ones are touched: re-arming everything on each check would churn
         * every pending intent on the device for no reason.
         */
        suspend fun repairMissingAlarms(context: Context, scheduler: AlarmScheduler): Int {
            val db = AppDatabase.getDatabase(context)
            val repo = ClockRepository(
                db.alarmDao(), db.worldClockDao(), db.musicScheduleDao(),
                db.timerDao(), db.timerPresetDao()
            )
            // Holiday-aware alarms need the day list before their next date can be worked out.
            try {
                `in`.sreerajp.chronotune_smart_clock.data.SpecialDayRegistry.refresh(context)
            } catch (_: Exception) { /* empty list just means "no holidays known" */ }

            var repaired = 0
            repo.getAllAlarmsOnce().forEach { alarm ->
                if (!alarm.isEnabled) return@forEach
                // A snoozed alarm has its own pending re-ring; leave it alone rather than
                // arming the base alarm on top of it.
                val snoozePending = scheduler.isArmed(
                    `in`.sreerajp.chronotune_smart_clock.data.AlarmIds.snoozeRing(alarm.id)
                )
                if (!scheduler.isArmed(alarm.id) && !snoozePending) {
                    scheduler.scheduleAlarm(alarm)
                    repaired++
                }
            }
            repo.getAllMusicSchedulesOnce().forEach { schedule ->
                if (!schedule.isEnabled) return@forEach
                if (!scheduler.isArmed(
                        `in`.sreerajp.chronotune_smart_clock.data.AlarmIds.musicRing(schedule.id)
                    )
                ) {
                    scheduler.scheduleMusic(schedule)
                    repaired++
                }
            }
            return repaired
        }
    }
}

/**
 * Upgrades alarms to the exact "alarm clock" path once the user grants the exact-alarm
 * permission in system settings.
 *
 * Without this, an alarm saved while the permission was off keeps the weaker while-idle
 * scheduling for ever, even after the user fixes the permission from the Settings screen.
 */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ExactAlarmPermission", "Exact alarm permission state changed — re-arming alarms")
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
                `in`.sreerajp.chronotune_smart_clock.data.SpecialDayRegistry.refresh(appContext)
                // Every alarm is re-armed here, not just the missing ones: the point is to move
                // them all onto the stronger scheduling path.
                repo.getAllAlarmsOnce().forEach { if (it.isEnabled) scheduler.scheduleAlarm(it) }
                repo.getAllMusicSchedulesOnce().forEach { if (it.isEnabled) scheduler.scheduleMusic(it) }
            } catch (e: Exception) {
                Log.e("ExactAlarmPermission", "Re-arm after permission change failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
