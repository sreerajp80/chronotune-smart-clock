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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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

    // Posted when the ringing alarm has a non-zero auto-silence length; fires stopAlarmAndSelf()
    // after that many minutes so the ring doesn't sound forever. Cancelled on any teardown.
    private val autoSilenceHandler = Handler(Looper.getMainLooper())
    private var autoSilenceRunnable: Runnable? = null

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
        val snoozeMin = intent?.getIntExtra(EXTRA_SNOOZE_MIN, 5) ?: 5
        val challenge = intent?.getStringExtra(EXTRA_CHALLENGE) ?: "NONE"
        val challengeDifficulty = intent?.getStringExtra(EXTRA_CHALLENGE_DIFFICULTY) ?: "EASY"
        val challengeCount = intent?.getIntExtra(EXTRA_CHALLENGE_COUNT, 1) ?: 1
        val autoSilenceMin = intent?.getIntExtra(EXTRA_AUTO_SILENCE, 0) ?: 0
        val maxSnoozeCount = intent?.getIntExtra(EXTRA_MAX_SNOOZE_COUNT, 0) ?: 0
        val snoozeMode = intent?.getStringExtra(EXTRA_SNOOZE_MODE) ?: "FIXED"
        val snoozeCount = intent?.getIntExtra(EXTRA_SNOOZE_COUNT, 0) ?: 0
        val baseId = intent?.getIntExtra(EXTRA_BASE_ID, id) ?: id

        Log.d(TAG, "Starting alarm service: id=$id type=$type")

        val alarm = ActiveAlarmState.ActiveAlarm(
            id, type, label, tone, volume, durationMin, uri, snoozeMin,
            challenge, challengeDifficulty, challengeCount, autoSilenceMin,
            maxSnoozeCount, snoozeMode, snoozeCount, baseId
        )

        // Something is already ringing. Queue this one instead of taking over: the service
        // used to keep a single current alarm, so a second alarm firing in the same minute
        // silently replaced the first — its audio stopped and its notification was orphaned,
        // which to the user looked exactly like an alarm that never went off. The queued ring
        // starts as soon as the active one is dismissed or snoozed.
        if (currentAlarmId != -1 && currentAlarmId != id) {
            if (waitingRings.none { it.id == id }) {
                waitingRings.add(alarm)
                Log.d(TAG, "Alarm $id queued behind $currentAlarmId")
                postWaitingNotification(this, alarm)
                `in`.sreerajp.chronotune_smart_clock.data.AlarmEventLog.record(
                    this,
                    `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent(
                        alarmId = alarm.baseId,
                        type = alarm.type,
                        label = alarm.label,
                        event = `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.QUEUED,
                        detail = "Waiting for alarm $currentAlarmId to finish"
                    )
                )
            }
            return START_NOT_STICKY
        }

        startRinging(alarm)
        return START_NOT_STICKY
    }

    /** Takes over as the active ring: notification, audio, and the full-screen alarm UI. */
    private fun startRinging(alarm: ActiveAlarmState.ActiveAlarm) {
        val id = alarm.id
        val autoSilenceMin = alarm.autoSilenceMinutes
        currentAlarmId = id
        currentAlarm = alarm
        ringStartedAt = System.currentTimeMillis()

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

        // Auto-silence: if configured, stop the whole ring (audio + notification + foreground
        // state) after the chosen number of minutes so it doesn't sound forever. Best-effort —
        // if the OS reaps the process first, playback stops anyway. A snoozed alarm still rings
        // again later as normal.
        cancelAutoSilence()
        if (autoSilenceMin > 0) {
            val runnable = Runnable {
                Log.d(TAG, "Auto-silencing alarm id=$id after $autoSilenceMin min")
                // Recorded before the teardown, so the history can tell an alarm that gave up
                // on its own from one the user actually turned off.
                `in`.sreerajp.chronotune_smart_clock.data.AlarmEventLog.record(
                    this,
                    `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent(
                        alarmId = alarm.baseId,
                        type = alarm.type,
                        label = alarm.label,
                        event = `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.AUTO_SILENCED,
                        ringDurationMs = ringDurationMs(),
                        dismissSource = `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.SOURCE_AUTO_SILENCE,
                        detail = "Stopped by itself after $autoSilenceMin min"
                    )
                )
                stopAlarmAndSelf()
            }
            autoSilenceRunnable = runnable
            autoSilenceHandler.postDelayed(runnable, autoSilenceMin * 60_000L)
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

    private fun cancelAutoSilence() {
        autoSilenceRunnable?.let { autoSilenceHandler.removeCallbacks(it) }
        autoSilenceRunnable = null
    }

    private fun stopAlarmAndSelf() {
        cancelAutoSilence()
        ActiveAlarmState.dismiss(this)
        val id = currentAlarmId
        if (id != -1) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            try { nm.cancel(id) } catch (_: Exception) { /* ignore */ }
        }
        currentAlarmId = -1
        currentAlarm = null

        // Another alarm fired while this one was ringing and has been waiting its turn. Give
        // it the floor now rather than stopping the service, so it is not lost.
        val next = if (waitingRings.isNotEmpty()) waitingRings.removeAt(0) else null
        if (next != null) {
            Log.d(TAG, "Starting queued alarm ${next.id}")
            try {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(waitingNotificationId(next.id))
            } catch (_: Exception) { /* ignore */ }
            startRinging(next)
            return
        }

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
        cancelAutoSilence()
        ActiveAlarmState.dismiss(this)
        // Clear any queued rings and their waiting notes: with the service gone there is
        // nothing left to start them, and a stale queue would confuse the next ring.
        if (waitingRings.isNotEmpty()) {
            try {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                waitingRings.forEach { nm.cancel(waitingNotificationId(it.id)) }
            } catch (_: Exception) { /* ignore */ }
            waitingRings.clear()
        }
        currentAlarmId = -1
        currentAlarm = null
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
        const val EXTRA_SNOOZE_MIN = "SNOOZE_MIN"
        const val EXTRA_CHALLENGE = "CHALLENGE"
        const val EXTRA_CHALLENGE_DIFFICULTY = "CHALLENGE_DIFFICULTY"
        const val EXTRA_CHALLENGE_COUNT = "CHALLENGE_COUNT"
        const val EXTRA_AUTO_SILENCE = "AUTO_SILENCE_MIN"
        const val EXTRA_MAX_SNOOZE_COUNT = "MAX_SNOOZE_COUNT"
        const val EXTRA_SNOOZE_MODE = "SNOOZE_MODE"
        const val EXTRA_SNOOZE_COUNT = "SNOOZE_COUNT"
        const val EXTRA_BASE_ID = "BASE_ID"

        private var currentAlarmId: Int = -1
        private var currentAlarm: ActiveAlarmState.ActiveAlarm? = null

        /**
         * When the ring currently sounding started. Deliberately not cleared when the ring
         * stops: dismiss and snooze both ask for the ring to stop and *then* record what
         * happened, so the value has to survive that gap. It is simply overwritten by the
         * next ring.
         */
        private var ringStartedAt: Long = 0L

        /**
         * How long the last ring had been sounding. This is the number that separates "I woke
         * up and turned it off" from a dismiss two seconds in, half asleep.
         */
        fun ringDurationMs(): Long =
            if (ringStartedAt > 0L) (System.currentTimeMillis() - ringStartedAt).coerceAtLeast(0L)
            else 0L

        /** The ring sounding right now, or null when nothing is. */
        fun currentRing(): ActiveAlarmState.ActiveAlarm? = currentAlarm

        /**
         * Rings that arrived while another alarm was already sounding, oldest first. Only one
         * alarm can own the audio and the full-screen screen at a time, so the rest wait here
         * and are started in turn as each active ring is dismissed or snoozed.
         */
        private val waitingRings = mutableListOf<ActiveAlarmState.ActiveAlarm>()

        /** Notification id for the "waiting its turn" note, kept clear of every ring id. */
        private fun waitingNotificationId(ringId: Int): Int =
            ringId + `in`.sreerajp.chronotune_smart_clock.data.AlarmIds.DISMISS_ACTION_OFFSET

        /**
         * The full-screen PendingIntent for a ring. Exposed so [AlarmReceiver] can still put
         * the ringing screen up when the OS refuses to start this service at all.
         */
        fun fullScreenIntentFor(
            context: Context,
            alarm: ActiveAlarmState.ActiveAlarm
        ): PendingIntent = buildFullScreenPendingIntent(context, alarm)

        /**
         * A quiet notification telling the user a second alarm is waiting behind the one that
         * is currently sounding. Without it a queued alarm would be invisible until its turn.
         */
        private fun postWaitingNotification(
            context: Context,
            alarm: ActiveAlarmState.ActiveAlarm
        ) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID_ACTIVE,
                        "Alarm Active (Silent)",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        setSound(null, null)
                        enableVibration(false)
                        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    }
                    nm.createNotificationChannel(channel)
                }
                val note = NotificationCompat.Builder(context, CHANNEL_ID_ACTIVE)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("Alarm waiting")
                    .setContentText("${alarm.label} will ring after the current alarm")
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(true)
                    .build()
                nm.notify(waitingNotificationId(alarm.id), note)
            } catch (e: Exception) {
                Log.e(TAG, "Could not post waiting notification: ${e.message}")
            }
        }

        fun startIntent(context: Context, alarm: ActiveAlarmState.ActiveAlarm): Intent =
            Intent(context, AlarmService::class.java).apply {
                putExtra(EXTRA_ID, alarm.id)
                putExtra(EXTRA_BASE_ID, alarm.baseId)
                putExtra(EXTRA_TYPE, alarm.type)
                putExtra(EXTRA_LABEL, alarm.label)
                putExtra(EXTRA_TONE, alarm.tone)
                putExtra(EXTRA_URI, alarm.uri ?: "")
                putExtra(EXTRA_VOLUME, alarm.volume)
                putExtra(EXTRA_DURATION_MIN, alarm.durationMin)
                putExtra(EXTRA_SNOOZE_MIN, alarm.snoozeMinutes)
                putExtra(EXTRA_CHALLENGE, alarm.dismissChallenge)
                putExtra(EXTRA_CHALLENGE_DIFFICULTY, alarm.challengeDifficulty)
                putExtra(EXTRA_CHALLENGE_COUNT, alarm.challengeCount)
                putExtra(EXTRA_AUTO_SILENCE, alarm.autoSilenceMinutes)
                putExtra(EXTRA_MAX_SNOOZE_COUNT, alarm.maxSnoozeCount)
                putExtra(EXTRA_SNOOZE_MODE, alarm.snoozeMode)
                putExtra(EXTRA_SNOOZE_COUNT, alarm.snoozeCount)
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

            // When a dismiss challenge is set, the notification's Dismiss action must not stop
            // the alarm outright (that would let the user skip the challenge from the shade).
            // Instead it reopens the full-screen alarm, where the challenge is enforced.
            val hasChallenge = alarm.type == "ALARM" && alarm.dismissChallenge != "NONE"
            val dismissActionPending = if (hasChallenge) fullScreenPendingIntent else dismissPending

            val snoozeIntent = Intent(context, AlarmSnoozeReceiver::class.java).apply {
                putExtra("NOTIFICATION_ID", alarm.id)
                putExtra("ID", alarm.id)
                // The alarm row behind this ring, so the re-ring is armed in the right slot
                // even when the current ring is itself a snooze.
                putExtra("BASE_ID", alarm.baseId)
                putExtra("LABEL", alarm.label)
                putExtra("TONE", alarm.tone)
                putExtra("URI", alarm.uri ?: "")
                putExtra("VOLUME", alarm.volume)
                putExtra("SNOOZE_MIN", alarm.snoozeMinutes)
                // Keep the challenge on the snoozed re-ring.
                putExtra("CHALLENGE", alarm.dismissChallenge)
                putExtra("CHALLENGE_DIFFICULTY", alarm.challengeDifficulty)
                putExtra("CHALLENGE_COUNT", alarm.challengeCount)
                putExtra("AUTO_SILENCE_MIN", alarm.autoSilenceMinutes)
                // Carry the snooze allowance so the receiver can enforce the limit itself.
                putExtra("MAX_SNOOZE_COUNT", alarm.maxSnoozeCount)
                putExtra("SNOOZE_MODE", alarm.snoozeMode)
                putExtra("SNOOZE_COUNT", alarm.snoozeCount)
            }
            val snoozePending = PendingIntent.getBroadcast(
                context,
                `in`.sreerajp.chronotune_smart_clock.data.AlarmIds.snoozeAction(alarm.id),
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(
                    when (alarm.type) {
                        "MUSIC" -> "Auto Music Playing"
                        "TIMER" -> "Timer Finished"
                        else -> "Alarm Triggered"
                    }
                )
                .setContentText("${alarm.label} - Playing ${alarm.tone}")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissActionPending)

            // No Snooze action once the alarm has used up its allowance — showing a button that
            // the receiver would refuse anyway is worse than not showing it at all.
            if (alarm.type == "ALARM" && alarm.canSnooze()) {
                val remaining = alarm.snoozesRemaining()
                val snoozeLabel = if (remaining == null) {
                    "Snooze"
                } else {
                    "Snooze (${alarm.nextSnoozeGapMinutes()}m, $remaining left)"
                }
                builder.addAction(android.R.drawable.ic_lock_idle_alarm, snoozeLabel, snoozePending)
            }

            // A finished timer's ring gets a "+1 min" action that stops the ring and restarts
            // the timer for another minute. The timer id is the ring id minus the offset.
            if (alarm.type == "TIMER") {
                val timerId = alarm.id - `in`.sreerajp.chronotune_smart_clock.data.TimerItem.RING_ID_OFFSET
                val addMinIntent = Intent(context, TimerAddMinuteReceiver::class.java).apply {
                    putExtra("TIMER_ID", timerId)
                }
                val addMinPending = PendingIntent.getBroadcast(
                    context,
                    `in`.sreerajp.chronotune_smart_clock.data.AlarmIds.addMinuteAction(alarm.id),
                    addMinIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(android.R.drawable.ic_input_add, "+1 min", addMinPending)
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

            // Same challenge guard as the heads-up notification: when a challenge is set, the
            // Dismiss action reopens the full-screen alarm instead of stopping it directly.
            val hasChallenge = alarm.type == "ALARM" && alarm.dismissChallenge != "NONE"
            val dismissActionPending = if (hasChallenge) openPending else dismissPending

            return NotificationCompat.Builder(context, CHANNEL_ID_ACTIVE)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(
                    when (alarm.type) {
                        "MUSIC" -> "Auto Music Playing"
                        "TIMER" -> "Timer Finished"
                        else -> "Alarm Ringing"
                    }
                )
                .setContentText(alarm.label)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setContentIntent(openPending)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissActionPending)
                .build()
        }
    }
}
