package `in`.sreerajp.chronotune_smart_clock.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Owns the lifecycle of an active alarm. Runs as a foreground service so the OS keeps
 * the process alive (audio + dismiss stay wired up), grants BAL exemption (the alarm
 * activity reliably takes over the screen even when the device is unlocked), and ties
 * the notification to a stoppable owner (cancelling the service tears everything down).
 */
class AlarmService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAlarmAndSelf()
                return START_NOT_STICKY
            }
            ACTION_DEMOTE -> {
                demoteNotification()
                return START_NOT_STICKY
            }
        }

        val id = intent?.getIntExtra(EXTRA_ID, -1) ?: -1
        val type = intent?.getStringExtra(EXTRA_TYPE) ?: "ALARM"
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: "Alarm Ringing"
        val tone = intent?.getStringExtra(EXTRA_TONE) ?: "Morning Breeze"
        val uri = intent?.getStringExtra(EXTRA_URI) ?: ""
        val volume = intent?.getFloatExtra(EXTRA_VOLUME, 0.8f) ?: 0.8f
        val durationMin = intent?.getIntExtra(EXTRA_DURATION_MIN, 0) ?: 0

        Log.d(TAG, "Starting alarm service: id=$id type=$type")

        val alarm = ActiveAlarmState.ActiveAlarm(id, type, label, tone, volume, durationMin, uri)
        currentAlarmId = id
        currentAlarm = alarm

        val fsi = buildFullScreenPendingIntent(this, alarm)
        val notification = buildNotification(this, alarm, fsi)

        // Must call startForeground within 5 seconds. Use a media-playback type so
        // the OS treats the audio playback as a legitimate foreground use case.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                id,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(id, notification)
        }

        // Wake the device briefly so audio + activity startup complete even when dozing.
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "chronotune:alarm-$id")
        try {
            wakeLock.acquire(60_000L)
            ActiveAlarmState.triggerAlarm(this, alarm)

            // Direct startActivity from the foreground service. With BAL exemption
            // (inherited from the setAlarmClock broadcast), this should bring up the
            // full-screen alarm UI immediately, screen on or off.
            val openIntent = Intent(this, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("ALARM_ID", alarm.id)
            try {
                startActivity(openIntent)
            } catch (e: Exception) {
                Log.e(TAG, "startActivity failed, falling back to FSI: ${e.message}")
                try {
                    fsi.send()
                } catch (e2: Exception) {
                    Log.e(TAG, "fsi.send also failed: ${e2.message}")
                }
            }
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }

        return START_NOT_STICKY
    }

    private fun demoteNotification() {
        // Once the alarm activity is on screen, the heads-up + FSI become redundant
        // (the user can already see Dismiss/Snooze in the activity). Re-post the
        // foreground notification on a low-importance channel with no FSI so the
        // heads-up disappears while the foreground service stays alive.
        val id = currentAlarmId
        val alarm = currentAlarm ?: return
        if (id == -1) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
        val silent = buildSilentNotification(this, alarm)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(id, silent, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(id, silent)
        }
    }

    private fun stopAlarmAndSelf() {
        ActiveAlarmState.dismiss(this)
        val id = currentAlarmId
        if (id != -1) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            try { nm.cancel(id) } catch (_: Exception) { /* ignore */ }
        }
        currentAlarmId = -1
        currentAlarm = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        // Defensive: if the service is destroyed for any other reason (e.g. system kill),
        // make sure the audio engine is shut down so we don't leave dangling playback.
        ActiveAlarmState.dismiss(this)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmService"
        const val CHANNEL_ID = "clock_alarms_channel"
        const val CHANNEL_ID_ACTIVE = "clock_alarms_active_channel"

        const val ACTION_STOP = "in.sreerajp.chronotune_smart_clock.ACTION_STOP_ALARM"
        const val ACTION_DEMOTE = "in.sreerajp.chronotune_smart_clock.ACTION_DEMOTE_ALARM"

        const val EXTRA_ID = "ID"
        const val EXTRA_TYPE = "TYPE"
        const val EXTRA_LABEL = "LABEL"
        const val EXTRA_TONE = "TONE"
        const val EXTRA_URI = "URI"
        const val EXTRA_VOLUME = "VOLUME"
        const val EXTRA_DURATION_MIN = "DURATION_MIN"

        private var currentAlarmId: Int = -1
        private var currentAlarm: ActiveAlarmState.ActiveAlarm? = null

        fun startIntent(context: Context, alarm: ActiveAlarmState.ActiveAlarm): Intent =
            Intent(context, AlarmService::class.java).apply {
                putExtra(EXTRA_ID, alarm.id)
                putExtra(EXTRA_TYPE, alarm.type)
                putExtra(EXTRA_LABEL, alarm.label)
                putExtra(EXTRA_TONE, alarm.tone)
                putExtra(EXTRA_URI, alarm.uri ?: "")
                putExtra(EXTRA_VOLUME, alarm.volume)
                putExtra(EXTRA_DURATION_MIN, alarm.durationMin)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, AlarmService::class.java).apply { action = ACTION_STOP }

        fun demoteIntent(context: Context): Intent =
            Intent(context, AlarmService::class.java).apply { action = ACTION_DEMOTE }

        private fun buildFullScreenPendingIntent(
            context: Context,
            alarm: ActiveAlarmState.ActiveAlarm
        ): PendingIntent {
            val openIntent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("ALARM_ID", alarm.id)
            }
            return PendingIntent.getActivity(
                context,
                alarm.id,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun buildNotification(
            context: Context,
            alarm: ActiveAlarmState.ActiveAlarm,
            fullScreenPendingIntent: PendingIntent
        ): android.app.Notification {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Alarms & Schedules Trigger",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Triggers alarms and schedulers elegantly"
                    enableVibration(true)
                    setBypassDnd(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setSound(null, AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                }
                nm.createNotificationChannel(channel)
            }

            // Dismiss action routes through the service so audio + foreground state are
            // torn down atomically — no chance for the audio to keep playing after the
            // notification is gone.
            val dismissIntent = Intent(context, AlarmDismissReceiver::class.java).apply {
                putExtra("NOTIFICATION_ID", alarm.id)
            }
            val dismissPending = PendingIntent.getBroadcast(
                context,
                alarm.id,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val snoozeIntent = Intent(context, AlarmSnoozeReceiver::class.java).apply {
                putExtra("NOTIFICATION_ID", alarm.id)
                putExtra("ID", alarm.id)
                putExtra("LABEL", alarm.label)
                putExtra("TONE", alarm.tone)
                putExtra("VOLUME", alarm.volume)
            }
            val snoozePending = PendingIntent.getBroadcast(
                context,
                alarm.id + 100000,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(if (alarm.type == "ALARM") "Alarm Triggered" else "Auto Music Playing")
                .setContentText("${alarm.label} - Playing ${alarm.tone}")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPending)

            if (alarm.type == "ALARM") {
                builder.addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze", snoozePending)
            }

            return builder.build()
        }

        /**
         * Low-importance variant of the alarm notification. No FSI, no heads-up, no
         * sound — just a status-bar entry that keeps the foreground service alive
         * while the alarm activity is on screen.
         */
        private fun buildSilentNotification(
            context: Context,
            alarm: ActiveAlarmState.ActiveAlarm
        ): android.app.Notification {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID_ACTIVE,
                    "Alarm Active (Silent)",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Persistent indicator while the alarm screen is showing"
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(channel)
            }

            val openIntent = Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("ALARM_ID", alarm.id)
            val openPending = PendingIntent.getActivity(
                context,
                alarm.id,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val dismissIntent = Intent(context, AlarmDismissReceiver::class.java).apply {
                putExtra("NOTIFICATION_ID", alarm.id)
            }
            val dismissPending = PendingIntent.getBroadcast(
                context,
                alarm.id,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return NotificationCompat.Builder(context, CHANNEL_ID_ACTIVE)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(if (alarm.type == "ALARM") "Alarm Ringing" else "Auto Music Playing")
                .setContentText(alarm.label)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setContentIntent(openPending)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPending)
                .build()
        }
    }
}
