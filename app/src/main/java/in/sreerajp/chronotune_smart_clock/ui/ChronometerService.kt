package `in`.sreerajp.chronotune_smart_clock.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import `in`.sreerajp.chronotune_smart_clock.MainActivity
import `in`.sreerajp.chronotune_smart_clock.StopwatchPrefs
import `in`.sreerajp.chronotune_smart_clock.data.AppDatabase
import `in`.sreerajp.chronotune_smart_clock.data.TimerItem
import `in`.sreerajp.chronotune_smart_clock.data.repository.ClockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.Locale
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the stopwatch and running timers alive across process death /
 * screen-off, and shows a **live notification with controls** for each. It is alive whenever the
 * stopwatch is RUNNING/PAUSED or any timer is RUNNING/PAUSED, and stops itself otherwise.
 *
 * The actual countdown numbers are driven by the system notification chronometer (anchored to
 * the persisted [SystemClock.elapsedRealtime]/RTC bases), so no high-frequency ticking is needed
 * to keep the notifications fresh — state changes simply re-post via [refresh].
 */
class ChronometerService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val repository by lazy {
        val db = AppDatabase.getDatabase(applicationContext)
        ClockRepository(
            db.alarmDao(), db.worldClockDao(), db.musicScheduleDao(),
            db.timerDao(), db.timerPresetDao()
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Satisfy the "startForeground within 5s of startForegroundService" rule immediately,
        // before any async DB work. This bootstrap notification is reconciled away in rebuild().
        startForegroundCompat(FG_BOOTSTRAP_ID, buildBootstrapNotification())

        val action = intent?.action
        val timerId = intent?.getIntExtra(EXTRA_TIMER_ID, -1) ?: -1

        scope.launch {
            try {
                when (action) {
                    ACTION_SW_PAUSE -> StopwatchPrefs.pause(applicationContext)
                    ACTION_SW_RESUME -> StopwatchPrefs.start(applicationContext)
                    ACTION_SW_LAP -> StopwatchPrefs.lap(applicationContext)
                    ACTION_SW_RESET -> StopwatchPrefs.reset(applicationContext)
                    ACTION_TIMER_PAUSE -> if (timerId >= 0) TimerEngine.pause(repository, applicationContext, timerId)
                    ACTION_TIMER_RESUME -> if (timerId >= 0) TimerEngine.resume(repository, applicationContext, timerId)
                    ACTION_TIMER_ADD_MIN -> if (timerId >= 0) TimerEngine.addMinute(repository, applicationContext, timerId)
                    ACTION_TIMER_CANCEL -> if (timerId >= 0) TimerEngine.cancel(repository, applicationContext, timerId)
                    else -> { /* ACTION_REFRESH / null: just rebuild below */ }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Action $action failed: ${e.message}")
            }
            rebuild()
        }

        return START_STICKY
    }

    /** Reconciles the live notifications with the current persisted state. */
    private suspend fun rebuild() {
        StopwatchPrefs.init(applicationContext)
        val sw = StopwatchPrefs.snapshot.value
        val timers = repository.getAllTimersOnce()
            .filter { it.state == TimerItem.STATE_RUNNING || it.state == TimerItem.STATE_PAUSED }

        // Build the desired notification set (insertion order matters: anchor = first).
        val desired = LinkedHashMap<Int, Notification>()
        if (sw.state != StopwatchPrefs.STATE_IDLE) {
            desired[STOPWATCH_NOTIF_ID] = buildStopwatchNotification(sw)
        }
        for (t in timers) {
            desired[TIMER_NOTIF_BASE + t.id] = buildTimerNotification(t)
        }

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (desired.isEmpty()) {
            cancelStale(nm, emptySet())
            stopForegroundCompat()
            stopSelf()
            return
        }

        val anchorId = desired.keys.first()
        startForegroundCompat(anchorId, desired.getValue(anchorId))
        for ((id, notif) in desired) {
            if (id != anchorId) nm.notify(id, notif)
        }
        cancelStale(nm, desired.keys)
    }

    /** Cancels any notification *we own* that is no longer wanted. */
    private fun cancelStale(nm: NotificationManager, keep: Set<Int>) {
        try {
            nm.activeNotifications.forEach { sbn ->
                val id = sbn.id
                if (ownsId(id) && id !in keep) nm.cancel(id)
            }
        } catch (_: Exception) { /* best-effort */ }
    }

    private fun ownsId(id: Int): Boolean =
        id == FG_BOOTSTRAP_ID || id == STOPWATCH_NOTIF_ID || id in TIMER_NOTIF_BASE until FG_BOOTSTRAP_ID

    // --- Notification builders -------------------------------------------------------------

    private fun buildBootstrapNotification(): Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("ChronoTune")
            .setContentText("Updating timers…")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .build()
    }

    private fun buildStopwatchNotification(sw: StopwatchPrefs.Snapshot): Notification {
        ensureChannel()
        val running = sw.state == StopwatchPrefs.STATE_RUNNING
        val elapsed = sw.elapsedNow()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Stopwatch")
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())

        if (running) {
            // Count up from (now - elapsed).
            builder.setUsesChronometer(true)
                .setWhen(System.currentTimeMillis() - elapsed)
                .setContentText("Running")
            builder.addAction(0, "Pause", swAction(ACTION_SW_PAUSE))
            builder.addAction(0, "Lap", swAction(ACTION_SW_LAP))
        } else {
            builder.setUsesChronometer(false)
                .setContentText("${formatStopwatch(elapsed)}  •  Paused")
            builder.addAction(0, "Resume", swAction(ACTION_SW_RESUME))
        }
        builder.addAction(0, "Reset", swAction(ACTION_SW_RESET))
        return builder.build()
    }

    private fun buildTimerNotification(timer: TimerItem): Notification {
        ensureChannel()
        val running = timer.state == TimerItem.STATE_RUNNING
        val title = timer.label.ifBlank { "Timer" }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())

        if (running) {
            // Count down to the RTC fire time.
            builder.setUsesChronometer(true)
                .setWhen(timer.fireAtWallClock)
                .setContentText("Counting down")
            builder.setChronometerCountDown(true)
            builder.addAction(0, "Pause", timerAction(ACTION_TIMER_PAUSE, timer.id))
        } else {
            builder.setUsesChronometer(false)
                .setContentText("${formatTimer(timer.remainingMs)}  •  Paused")
            builder.addAction(0, "Resume", timerAction(ACTION_TIMER_RESUME, timer.id))
        }
        builder.addAction(0, "+1 min", timerAction(ACTION_TIMER_ADD_MIN, timer.id))
        builder.addAction(0, "Cancel", timerAction(ACTION_TIMER_CANCEL, timer.id))
        return builder.build()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun swAction(action: String): PendingIntent {
        val intent = Intent(this, ChronometerService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun timerAction(action: String, timerId: Int): PendingIntent {
        val intent = Intent(this, ChronometerService::class.java).apply {
            this.action = action
            putExtra(EXTRA_TIMER_ID, timerId)
        }
        return PendingIntent.getService(
            this, (action + timerId).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Stopwatch & Timers",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Live stopwatch and running-timer controls"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun startForegroundCompat(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(id, notification)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ChronometerService"
        const val CHANNEL_ID = "clock_chronometer_channel"

        // Notification id space owned by this service.
        private const val TIMER_NOTIF_BASE = 920000
        private const val STOPWATCH_NOTIF_ID = 989998
        private const val FG_BOOTSTRAP_ID = 989999

        const val ACTION_REFRESH = "in.sreerajp.chronotune_smart_clock.CHRONO_REFRESH"
        const val ACTION_SW_PAUSE = "in.sreerajp.chronotune_smart_clock.SW_PAUSE"
        const val ACTION_SW_RESUME = "in.sreerajp.chronotune_smart_clock.SW_RESUME"
        const val ACTION_SW_LAP = "in.sreerajp.chronotune_smart_clock.SW_LAP"
        const val ACTION_SW_RESET = "in.sreerajp.chronotune_smart_clock.SW_RESET"
        const val ACTION_TIMER_PAUSE = "in.sreerajp.chronotune_smart_clock.TIMER_PAUSE"
        const val ACTION_TIMER_RESUME = "in.sreerajp.chronotune_smart_clock.TIMER_RESUME"
        const val ACTION_TIMER_ADD_MIN = "in.sreerajp.chronotune_smart_clock.TIMER_ADD_MIN"
        const val ACTION_TIMER_CANCEL = "in.sreerajp.chronotune_smart_clock.TIMER_CANCEL"

        const val EXTRA_TIMER_ID = "TIMER_ID"

        /**
         * Re-evaluates and re-posts the live notifications. Safe to call from anywhere; if the OS
         * forbids starting a background foreground service (and nothing is active anyway), the
         * exception is swallowed.
         */
        fun refresh(context: Context) {
            val intent = Intent(context, ChronometerService::class.java).apply { action = ACTION_REFRESH }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not start ChronometerService: ${e.message}")
            }
        }

        private fun formatStopwatch(ms: Long): String {
            val min = (ms / 60000) % 60
            val sec = (ms / 1000) % 60
            val centi = (ms / 10) % 100
            return String.format(Locale.ROOT, "%02d:%02d.%02d", min, sec, centi)
        }

        private fun formatTimer(ms: Long): String {
            val h = ms / 3600000
            val m = (ms / 60000) % 60
            val s = (ms / 1000) % 60
            return if (h > 0) String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s)
            else String.format(Locale.ROOT, "%02d:%02d", m, s)
        }
    }
}
