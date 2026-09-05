package `in`.sreerajp.chronotune_smart_clock.data

import android.app.AlarmManager
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The one way anything in the app records what an alarm did.
 *
 * Two rules hold everywhere this is used:
 *
 * 1. **Logging must never be able to stop an alarm.** Every call is fire-and-forget on a
 *    background scope and swallows all its own errors. A caller does not wait for it, and a
 *    failure here can never propagate into the ring path.
 * 2. **Ring first, log second.** Call sites hand the ring to the service and then record it,
 *    never the other way round.
 *
 * The device-state fields ([AlarmEvent.screenOn] and friends) are filled in here rather than by
 * each caller, so a ring that happened in Doze on a locked phone is recorded the same way no
 * matter which code path noticed it.
 */
object AlarmEventLog {

    // Its own scope on purpose: a receiver's goAsync() window or an activity's lifecycle must
    // never decide whether an event gets written.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Records [event]. [AlarmEvent.actualAt] and the device-state fields are filled in unless
     * the caller already set them.
     */
    fun record(context: Context, event: AlarmEvent) {
        val appContext = context.applicationContext
        val stamped = stamp(appContext, event)
        scope.launch {
            try {
                AppDatabase.getDatabase(appContext).alarmEventDao().insert(stamped)
            } catch (e: Exception) {
                Log.e(TAG, "Could not record ${event.event} for ${event.alarmId}: ${e.message}")
            }
        }
    }

    /**
     * Records [event] and waits for the write.
     *
     * Only for callers that are already inside a coroutine doing database work and want the row
     * to exist before they move on — the missed-alarm check, for example. Still swallows its
     * own errors.
     */
    suspend fun recordNow(context: Context, event: AlarmEvent) {
        val appContext = context.applicationContext
        try {
            AppDatabase.getDatabase(appContext)
                .alarmEventDao()
                .insert(stamp(appContext, event))
        } catch (e: Exception) {
            Log.e(TAG, "Could not record ${event.event} for ${event.alarmId}: ${e.message}")
        }
    }

    /** Fills in the timestamp and the device state, leaving anything the caller set alone. */
    private fun stamp(context: Context, event: AlarmEvent): AlarmEvent {
        val now = System.currentTimeMillis()
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            event.copy(
                actualAt = if (event.actualAt > 0L) event.actualAt else now,
                screenOn = pm?.isInteractive ?: false,
                deviceLocked = km?.isKeyguardLocked ?: false,
                dozeIdle = pm?.isDeviceIdleMode ?: false,
                exactAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    am?.canScheduleExactAlarms() ?: true
                } else {
                    true
                }
            )
        } catch (e: Exception) {
            // Device state is a nice-to-have; the event itself matters more.
            event.copy(actualAt = if (event.actualAt > 0L) event.actualAt else now)
        }
    }

    private const val TAG = "AlarmEventLog"
}
