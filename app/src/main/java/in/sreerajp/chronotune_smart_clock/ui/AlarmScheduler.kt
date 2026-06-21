package `in`.sreerajp.chronotune_smart_clock.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import `in`.sreerajp.chronotune_smart_clock.data.Alarm
import `in`.sreerajp.chronotune_smart_clock.data.MusicSchedule
import java.util.Calendar

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAlarm(alarm: Alarm) {
        if (!alarm.isEnabled) return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ID", alarm.id)
            putExtra("TYPE", "ALARM")
            putExtra("LABEL", alarm.label.ifBlank { "Alarm Ringing" })
            putExtra("TONE", alarm.customToneName)
            putExtra("URI", alarm.customToneUri)
            putExtra("VOLUME", alarm.volume)
            putExtra("VIBRATE", alarm.isVibrate)
            putExtra("SNOOZE_MIN", alarm.snoozeMinutes)
            // Carry repeat-day info + time so the receiver can re-arm the next occurrence after firing.
            putExtra("DAYS", alarm.daysOfWeek)
            putExtra("HOUR", alarm.hour)
            putExtra("MINUTE", alarm.minute)
            // Carry the pause window so the receiver can re-arm pause-aware after firing.
            putExtra("PAUSE_START", alarm.pauseStartMillis)
            putExtra("PAUSE_END", alarm.pauseEndMillis)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = nextTriggerTime(
            alarm.hour, alarm.minute, alarm.getRepeatDaysList(),
            alarm.pauseStartMillis, alarm.pauseEndMillis
        )

        // setAlarmClock is the only AlarmManager API that grants the "user-facing alarm
        // clock" privilege. On Android 12+ this is what lets the resulting broadcast
        // post a full-screen intent that actually takes over the screen and bypass
        // Doze/battery restrictions — setExactAndAllowWhileIdle does NOT.
        try {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            if (canExact) {
                // Show-intent is what the system uses to open the alarm's edit UI from the
                // status bar "next alarm" chip. We point it at the main app so the user can
                // jump to the alarms screen.
                val showIntent = Intent(context, `in`.sreerajp.chronotune_smart_clock.MainActivity::class.java)
                val showPending = PendingIntent.getActivity(
                    context,
                    alarm.id,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val info = AlarmManager.AlarmClockInfo(calendar.timeInMillis, showPending)
                alarmManager.setAlarmClock(info, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
            Log.d("AlarmScheduler", "Successfully scheduled alarm ${alarm.id} for ${calendar.time}")
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "Permission lack for exact scheduling, falling back: ${e.message}")
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    fun cancelAlarm(alarm: Alarm) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun scheduleMusic(schedule: MusicSchedule) {
        if (!schedule.isEnabled) return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ID", schedule.id)
            putExtra("TYPE", "MUSIC")
            putExtra("LABEL", schedule.label.ifBlank { "Scheduled Music" })
            putExtra("TONE", schedule.musicTrackName)
            putExtra("URI", schedule.customFileUri)
            putExtra("VOLUME", schedule.volume)
            putExtra("DURATION_MIN", schedule.durationMinutes)
            putExtra("DAYS", schedule.daysOfWeek)
            putExtra("HOUR", schedule.hour)
            putExtra("MINUTE", schedule.minute)
        }

        // Offset the request code by 10000 to keep it unique from alarm IDs
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            schedule.id + 10000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = nextTriggerTime(schedule.hour, schedule.minute, schedule.getRepeatDaysList())

        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            Log.d("AlarmScheduler", "Successfully scheduled music ${schedule.id} for ${calendar.time}")
        } catch (e: Exception) {
            Log.e("AlarmScheduler", "Failed scheduling music: ${e.message}")
        }
    }

    /**
     * Computes the next time the alarm/schedule should fire.
     *
     * - When [repeatDays] is empty the alarm is a one-shot: today at hour:minute, or
     *   tomorrow if that time has already passed.
     * - When [repeatDays] is set (1=Monday … 7=Sunday, matching the model encoding) the
     *   result is the soonest selected weekday at hour:minute that is still in the future.
     * - When a pause window [pauseStartMillis]..[pauseEndMillis] is configured, any candidate
     *   date that falls inside it is skipped so the alarm resumes only after the window ends.
     */
    private fun nextTriggerTime(
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

    fun cancelMusic(schedule: MusicSchedule) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            schedule.id + 10000,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
