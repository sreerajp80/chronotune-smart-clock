package `in`.sreerajp.chronotune_smart_clock.ui

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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Live React State that your MainActivity and screens can observe
object ActiveAlarmState {
    data class ActiveAlarm(
        val id: Int,
        val type: String, // "ALARM" or "MUSIC"
        val label: String,
        val tone: String,
        val volume: Float,
        val durationMin: Int = 0,
        val uri: String? = null
    )

    private val _activeAlarm = MutableStateFlow<ActiveAlarm?>(null)
    val activeAlarm = _activeAlarm.asStateFlow()

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
                audioEngine?.playPlaylist(playlist, alarm.volume, durationMs)
            }
        } else {
            audioEngine?.playAudio(alarm.tone, alarm.uri, alarm.volume, null)
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
        audioEngine?.stop()
        _activeAlarm.value = null
        cancelNotification(context, current.id)
        scheduleSnooze(
            context = context,
            id = current.id,
            label = current.label,
            tone = current.tone,
            volume = current.volume,
            snoozeMinutes = snoozeMinutes
        )
    }

    // Re-arms a one-shot alarm `snoozeMinutes` from now. Safe to call from a BroadcastReceiver
    // even after the process was killed, because it doesn't depend on _activeAlarm being set.
    fun scheduleSnooze(
        context: Context,
        id: Int,
        label: String,
        tone: String,
        volume: Float,
        snoozeMinutes: Int = 5
    ) {
        val scheduler = AlarmScheduler(context)
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.MINUTE, snoozeMinutes)

        val tempAlarm = `in`.sreerajp.chronotune_smart_clock.data.Alarm(
            id = id + 50000, // Safe offset for temporary snooze alarm
            hour = cal.get(java.util.Calendar.HOUR_OF_DAY),
            minute = cal.get(java.util.Calendar.MINUTE),
            label = "$label (Snoozed)",
            isEnabled = true,
            customToneName = tone,
            volume = volume
        )
        scheduler.scheduleAlarm(tempAlarm)
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
        val id = intent.getIntExtra("ID", -1)
        val type = intent.getStringExtra("TYPE") ?: "ALARM"
        val label = intent.getStringExtra("LABEL") ?: "Alarm Ringing"
        val tone = intent.getStringExtra("TONE") ?: "Morning Breeze"
        val uri = intent.getStringExtra("URI") ?: ""
        val volume = intent.getFloatExtra("VOLUME", 0.8f)
        val durationMin = intent.getIntExtra("DURATION_MIN", 0)
        val days = intent.getStringExtra("DAYS") ?: ""
        val pauseStart = intent.getLongExtra("PAUSE_START", 0L)
        val pauseEnd = intent.getLongExtra("PAUSE_END", 0L)

        Log.d("AlarmReceiver", "Alarm occurred! Type: $type, Label: $label, ID: $id")

        // AlarmManager one-shots don't repeat. For a repeating alarm/schedule we must re-arm
        // the next occurrence ourselves now that this one has fired — otherwise it rings once
        // and never again. (One-shot alarms with no selected days are intentionally left to
        // simply not repeat.)
        if (days.isNotBlank()) {
            rescheduleNextOccurrence(context, intent, type, id, label, tone, uri, volume, durationMin, days, pauseStart, pauseEnd)
        }

        // Safety guard: pause-aware scheduling should already keep this from firing during the
        // pause window, but if a stale alarm slips through, suppress the ring (the re-arm above
        // still lands the next occurrence after the window).
        if (type == "ALARM" && isPausedNow(pauseStart, pauseEnd)) {
            Log.d("AlarmReceiver", "Alarm $id is within its pause window — suppressing ring")
            return
        }

        // Hand off to a foreground service. The service owns the audio + notification +
        // activity launch so the process can't be reaped mid-alarm, dismiss reliably stops
        // playback, and the OS grants BAL exemption to launch the full-screen UI.
        val alarm = ActiveAlarmState.ActiveAlarm(id, type, label, tone, volume, durationMin, uri)
        ContextCompat.startForegroundService(context, AlarmService.startIntent(context, alarm))
    }

    private fun isPausedNow(pauseStartMillis: Long, pauseEndMillis: Long): Boolean {
        if (pauseStartMillis <= 0L || pauseEndMillis <= 0L) return false
        val today = `in`.sreerajp.chronotune_smart_clock.data.Alarm.todayEpochDay()
        val perDay = `in`.sreerajp.chronotune_smart_clock.data.Alarm.MILLIS_PER_DAY
        return today in (pauseStartMillis / perDay)..(pauseEndMillis / perDay)
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
        pauseEnd: Long
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
                        pauseStartMillis = pauseStart,
                        pauseEndMillis = pauseEnd
                    )
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
                val repo = ClockRepository(db.alarmDao(), db.worldClockDao(), db.musicScheduleDao())
                val scheduler = AlarmScheduler(appContext)

                repo.allAlarms.first().forEach { alarm ->
                    if (alarm.isEnabled) scheduler.scheduleAlarm(alarm)
                }
                repo.allMusicSchedules.first().forEach { schedule ->
                    if (schedule.isEnabled) scheduler.scheduleMusic(schedule)
                }
                Log.d("BootReceiver", "Rescheduled alarms and music schedules")
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

class AlarmSnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra("NOTIFICATION_ID", -1)
        val id = intent.getIntExtra("ID", notifId)
        val label = intent.getStringExtra("LABEL") ?: "Alarm"
        val tone = intent.getStringExtra("TONE") ?: "Morning Breeze"
        val volume = intent.getFloatExtra("VOLUME", 0.8f)

        // Tear down the currently-ringing alarm via the service (audio + notification +
        // foreground state all go together).
        try {
            context.startService(AlarmService.stopIntent(context))
        } catch (_: Exception) {
            ActiveAlarmState.dismiss(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notifId != -1) nm.cancel(notifId)
        }

        // Re-arm the alarm to fire again after the snooze period.
        ActiveAlarmState.scheduleSnooze(
            context = context,
            id = id,
            label = label,
            tone = tone,
            volume = volume
        )
    }
}
