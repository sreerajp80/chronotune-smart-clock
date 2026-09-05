package `in`.sreerajp.chronotune_smart_clock.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import `in`.sreerajp.chronotune_smart_clock.AlarmRingingOverlay
import `in`.sreerajp.chronotune_smart_clock.ui.theme.MyApplicationTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
        hideSystemBars()
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

        // Explicitly prevent touches outside the window bounds from finishing the activity.
        setFinishOnTouchOutside(false)

        // Always keep screen awake while ringing regardless of Android version.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Ensure the activity is visible on the lock screen and turns the screen on.
        // We deliberately do NOT request to dismiss the keyguard — showing over the
        // lock screen is enough for the user to tap Dismiss/Snooze. Asking to dismiss
        // a secure keyguard would force an unlock (PIN/pattern/fingerprint) prompt.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        hideSystemBars()

        // Block system back button / back gesture from dismissing or hiding the ringing UI.
        // The ringing screen must only exit when the user taps Dismiss or Snooze.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Deliberately ignored
            }
        })

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
                            onDismiss = { attempts, challengeMs ->
                                // Recorded before the teardown, while the ring is still the
                                // active one and its duration can still be read.
                                ActiveAlarmState.recordDismiss(
                                    context = this@AlarmActivity,
                                    ring = ring,
                                    source = `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.SOURCE_FULL_SCREEN,
                                    challengeAttempts = attempts,
                                    challengeMs = challengeMs
                                )
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
                                val snoozeUri = ring.uri
                                val snoozeVolume = ring.volume
                                val snoozeMinutes = ring.snoozeMinutes
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
                                    uri = snoozeUri,
                                    volume = snoozeVolume,
                                    snoozeMinutes = snoozeMinutes,
                                    dismissChallenge = ring.dismissChallenge,
                                    challengeDifficulty = ring.challengeDifficulty,
                                    challengeCount = ring.challengeCount,
                                    // Carried through so a snooze taken here behaves exactly
                                    // like one taken from the notification: the same
                                    // auto-silence, the same allowance, and the same
                                    // progressive gap. Leaving these at their defaults used to
                                    // hand the user unlimited snoozes on a limited alarm.
                                    autoSilenceMinutes = ring.autoSilenceMinutes,
                                    maxSnoozeCount = ring.maxSnoozeCount,
                                    snoozeMode = ring.snoozeMode,
                                    snoozeCount = ring.snoozeCount,
                                    baseAlarmId = ring.baseId
                                )
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun hideSystemBars() {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
}
