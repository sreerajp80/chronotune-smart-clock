package `in`.sreerajp.chronotune_smart_clock.ui

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.sreerajp.chronotune_smart_clock.AlarmRingingOverlay
import `in`.sreerajp.chronotune_smart_clock.ui.theme.MyApplicationTheme

/**
 * Standalone full-screen alarm UI. Launched by the alarm notification's full-screen intent
 * (and directly by AlarmReceiver) so the ringing screen appears on the lock screen and
 * over other apps without bringing the main MainActivity to the foreground.
 *
 * Dismiss/Snooze finish this activity, returning the user to whatever they were doing —
 * the main app is never shown.
 */
class AlarmActivity : ComponentActivity() {

    override fun onResume() {
        super.onResume()
        // Tell the service to swap its heads-up notification for a silent low-importance
        // one — the user has the alarm UI in front of them now and doesn't need the
        // floating banner on top of it.
        try {
            startService(AlarmService.demoteIntent(this))
        } catch (_: Exception) { /* service may be gone if dismiss happened first */ }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ensure the activity is visible on the lock screen and turns the screen on.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setContent {
            val isDark = isSystemInDarkTheme()
            MyApplicationTheme(darkTheme = isDark) {
                val activeAlarm by ActiveAlarmState.activeAlarm.collectAsStateWithLifecycle()

                // Auto-finish if the alarm was dismissed elsewhere (e.g., from the notification
                // action button) while this screen is showing.
                LaunchedEffect(activeAlarm) {
                    if (activeAlarm == null) finish()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    activeAlarm?.let { ring ->
                        AlarmRingingOverlay(
                            alarm = ring,
                            onDismiss = {
                                // Stop via the service so audio + notification + foreground
                                // state are torn down together.
                                try {
                                    startService(AlarmService.stopIntent(this@AlarmActivity))
                                } catch (_: Exception) {
                                    ActiveAlarmState.dismiss(this@AlarmActivity)
                                }
                                finish()
                            },
                            onSnooze = {
                                val snoozeId = ring.id
                                val snoozeLabel = ring.label
                                val snoozeTone = ring.tone
                                val snoozeVolume = ring.volume
                                try {
                                    startService(AlarmService.stopIntent(this@AlarmActivity))
                                } catch (_: Exception) {
                                    ActiveAlarmState.dismiss(this@AlarmActivity)
                                }
                                ActiveAlarmState.scheduleSnooze(
                                    context = this@AlarmActivity,
                                    id = snoozeId,
                                    label = snoozeLabel,
                                    tone = snoozeTone,
                                    volume = snoozeVolume
                                )
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }
}
