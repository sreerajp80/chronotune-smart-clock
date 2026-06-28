package `in`.sreerajp.chronotune_smart_clock.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import `in`.sreerajp.chronotune_smart_clock.data.Alarm
import `in`.sreerajp.chronotune_smart_clock.data.MusicSchedule
import `in`.sreerajp.chronotune_smart_clock.data.TimerItem
import `in`.sreerajp.chronotune_smart_clock.data.nextTriggerTime

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
     * Schedules a one-shot countdown timer to ring at [TimerItem.fireAtWallClock] using the
     * same privileged setAlarmClock path as alarms, so it rings even when the app is
     * backgrounded / Dozed / killed. The ring is handled by the shared [AlarmReceiver] ->
     * [AlarmService] stack (TYPE = "TIMER"), using the timer's chosen tone/volume.
     */
    fun scheduleTimer(timer: TimerItem) {
        if (timer.fireAtWallClock <= 0L) return

        val ringId = timer.id + TimerItem.RING_ID_OFFSET
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ID", ringId)
            putExtra("TIMER_ID", timer.id)
            putExtra("TYPE", "TIMER")
            putExtra("LABEL", timer.label.ifBlank { "Timer Finished" })
            putExtra("TONE", timer.toneName)
            putExtra("URI", timer.toneUri)
            putExtra("VOLUME", timer.volume)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ringId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            if (canExact) {
                val showIntent = Intent(context, `in`.sreerajp.chronotune_smart_clock.MainActivity::class.java)
                val showPending = PendingIntent.getActivity(
                    context,
                    ringId,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val info = AlarmManager.AlarmClockInfo(timer.fireAtWallClock, showPending)
                alarmManager.setAlarmClock(info, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    timer.fireAtWallClock,
                    pendingIntent
                )
            }
            Log.d("AlarmScheduler", "Scheduled timer ${timer.id} for ${timer.fireAtWallClock}")
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "Timer exact scheduling denied, falling back: ${e.message}")
            alarmManager.set(AlarmManager.RTC_WAKEUP, timer.fireAtWallClock, pendingIntent)
        }
    }

    fun cancelTimer(timer: TimerItem) {
        val ringId = timer.id + TimerItem.RING_ID_OFFSET
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ringId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

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
