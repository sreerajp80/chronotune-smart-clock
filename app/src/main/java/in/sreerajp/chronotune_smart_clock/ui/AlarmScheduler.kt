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
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If time is past, schedule for tomorrow
            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1)
            }
        }

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
        }

        // Offset the request code by 10000 to keep it unique from alarm IDs
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            schedule.id + 10000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, schedule.hour)
            set(Calendar.MINUTE, schedule.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1)
            }
        }

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
