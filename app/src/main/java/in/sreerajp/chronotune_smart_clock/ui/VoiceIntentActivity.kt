package `in`.sreerajp.chronotune_smart_clock.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Toast
import `in`.sreerajp.chronotune_smart_clock.AppPrefs
import `in`.sreerajp.chronotune_smart_clock.MainActivity
import `in`.sreerajp.chronotune_smart_clock.R
import `in`.sreerajp.chronotune_smart_clock.data.Alarm
import `in`.sreerajp.chronotune_smart_clock.data.AppDatabase
import `in`.sreerajp.chronotune_smart_clock.data.repository.ClockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
 * Handles the standard Android clock actions, so a voice assistant saying
 * "set an alarm for 7" can drive this app instead of the stock clock.
 *
 * It has no UI of its own: it does the work, shows a short toast, and finishes. When the
 * request is incomplete (no time given) or the assistant asks for the UI, it opens
 * [MainActivity] on the right tab instead.
 */
class VoiceIntentActivity : Activity() {

    // Outlives this activity on purpose — the activity finishes immediately while the
    // database write and alarm scheduling complete in the background.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val repository by lazy {
        val db = AppDatabase.getDatabase(applicationContext)
        ClockRepository(
            db.alarmDao(), db.worldClockDao(), db.musicScheduleDao(),
            db.timerDao(), db.timerPresetDao()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPrefs.init(applicationContext)
        handle(intent)
        finish()
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            AlarmClock.ACTION_SET_ALARM -> handleSetAlarm(intent)
            AlarmClock.ACTION_SET_TIMER -> handleSetTimer(intent)
            AlarmClock.ACTION_SHOW_ALARMS -> openApp(MainActivity.TAB_ALARMS)
            AlarmClock.ACTION_SHOW_TIMERS -> openApp(MainActivity.TAB_TIMER)
            AlarmClock.ACTION_DISMISS_ALARM -> ActiveAlarmState.dismiss(this)
            AlarmClock.ACTION_SNOOZE_ALARM -> {
                val minutes = intent.getIntExtra(
                    AlarmClock.EXTRA_ALARM_SNOOZE_DURATION,
                    AppPrefs.getDefaultSnoozeMinutes(this)
                )
                ActiveAlarmState.snooze(this, minutes)
            }
            else -> openApp(MainActivity.TAB_ALARMS)
        }
    }

    // ---------------------------------------------------------------- alarms

    private fun handleSetAlarm(intent: Intent) {
        val message = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE).orEmpty()
        var hour = intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1)
        var minute = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0)
        var days = intent.readDays()
        var label = message

        if (hour !in 0..23) {
            // No structured time. Some assistants pass the raw sentence instead, so try to
            // read it ourselves before giving up and opening the UI.
            val parsed = VoiceCommandParser.parse(message)
            if (parsed is VoiceCommand.SetAlarm) {
                hour = parsed.hour
                minute = parsed.minute
                days = parsed.days
                label = parsed.label
            } else {
                openApp(MainActivity.TAB_ALARMS)
                return
            }
        }

        val alarm = Alarm(
            hour = hour,
            minute = minute.coerceIn(0, 59),
            label = label,
            daysOfWeek = days.sorted().joinToString(","),
            customToneName = AppPrefs.defaultAlarmTone.value,
            volume = 0.8f,
            isVibrate = intent.getBooleanExtra(AlarmClock.EXTRA_VIBRATE, true),
            isEnabled = true,
            snoozeMinutes = AppPrefs.getDefaultSnoozeMinutes(this),
            autoSilenceMinutes = AppPrefs.getDefaultAutoSilenceMinutes(this)
        )

        val context = applicationContext
        scope.launch {
            val id = repository.insertAlarm(alarm).toInt()
            AlarmScheduler(context).scheduleAlarm(alarm.copy(id = id))
        }

        toast(getString(R.string.voice_alarm_set, formatTime(alarm.hour, alarm.minute)))

        // EXTRA_SKIP_UI is the assistant saying "do it silently". Without it, showing the
        // list is the documented, expected behaviour.
        if (!intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)) {
            openApp(MainActivity.TAB_ALARMS)
        }
    }

    /** Converts EXTRA_DAYS (java.util.Calendar day numbers) to the app's 1=Mon..7=Sun. */
    private fun Intent.readDays(): List<Int> {
        val raw = getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS) ?: return emptyList()
        return raw.mapNotNull { calendarDay ->
            when (calendarDay) {
                Calendar.SUNDAY -> 7
                in Calendar.MONDAY..Calendar.SATURDAY -> calendarDay - 1
                else -> null
            }
        }.distinct().sorted()
    }

    // ---------------------------------------------------------------- timers

    private fun handleSetTimer(intent: Intent) {
        val seconds = intent.getIntExtra(AlarmClock.EXTRA_LENGTH, 0)
        val message = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE).orEmpty()

        val durationMs = if (seconds > 0) seconds * 1000L
        else (VoiceCommandParser.parse(message) as? VoiceCommand.SetTimer)?.durationMs ?: 0L

        if (durationMs <= 0L) {
            openApp(MainActivity.TAB_TIMER)
            return
        }

        val context = applicationContext
        scope.launch {
            TimerEngine.addAndStart(
                repository, context, durationMs, message, "Cosmic Shimmer", "", 0.8f
            )
        }

        toast(getString(R.string.voice_timer_set, formatDuration(durationMs)))
        if (!intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)) {
            openApp(MainActivity.TAB_TIMER)
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun openApp(tab: Int) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TAB, tab)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    private fun toast(text: String) =
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun formatTime(hour: Int, minute: Int): String {
        if (AppPrefs.is24Hour.value) {
            return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        }
        val amPm = if (hour >= 12) "PM" else "AM"
        val display = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format(Locale.getDefault(), "%02d:%02d %s", display, minute, amPm)
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return buildString {
            if (hours > 0) append("$hours h ")
            if (minutes > 0) append("$minutes min ")
            if (seconds > 0) append("$seconds sec")
        }.trim().ifBlank { "0 min" }
    }

    companion object {
        /** Convenience for other parts of the app that want to hand over a spoken sentence. */
        fun intentFor(context: Context, spoken: String): Intent =
            Intent(context, VoiceIntentActivity::class.java)
                .setAction(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_MESSAGE, spoken)
    }
}
