package `in`.sreerajp.chronotune_smart_clock

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import `in`.sreerajp.chronotune_smart_clock.data.*
import `in`.sreerajp.chronotune_smart_clock.data.repository.ClockRepository
import `in`.sreerajp.chronotune_smart_clock.ui.ClockViewModel
import `in`.sreerajp.chronotune_smart_clock.ui.theme.MyApplicationTheme
import `in`.sreerajp.chronotune_smart_clock.widget.AnalogClockWidgetProvider
import `in`.sreerajp.chronotune_smart_clock.widget.DigitalClockWidgetProvider
import androidx.activity.viewModels
import android.content.Intent
import java.util.*
import androidx.core.net.toUri


class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
    private val repository by lazy {
        ClockRepository(
            database.alarmDao(), database.worldClockDao(), database.musicScheduleDao(),
            database.timerDao(), database.timerPresetDao()
        )
    }
    private val viewModel: ClockViewModel by viewModels {
        ClockViewModel.Factory(applicationContext, repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        AppPrefs.init(applicationContext)

        // After a fresh install or force-stop, Android suppresses APPWIDGET_UPDATE
        // broadcasts, so the launcher shows "Tap to open this app so your widget
        // can refresh." Opening MainActivity clears the stopped flag — push a
        // render now so the widget catches up the moment the user lands here.
        DigitalClockWidgetProvider.renderAll(applicationContext)
        AnalogClockWidgetProvider.renderAll(applicationContext)
        AnalogClockWidgetProvider.scheduleNextTick(applicationContext)

        setContent {
            val systemTheme = isSystemInDarkTheme()
            // Setup dynamic theme state with user toggle override
            @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
            var darkThemeOverride by remember { mutableStateOf<Boolean?>(null) }
            val isDark = darkThemeOverride ?: systemTheme

            MyApplicationTheme(darkTheme = isDark) {
                // Request critical notification permissions on Android 13+
                val context = LocalContext.current
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (!isGranted) {
                        Toast.makeText(context, "Notifications are required to sound background alarms", Toast.LENGTH_SHORT).show()
                    }
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    // Android 14+ no longer auto-grants USE_FULL_SCREEN_INTENT to arbitrary
                    // apps. Without it the alarm screen is downgraded to a heads-up.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        val nm = context.getSystemService(NotificationManager::class.java)
                        if (nm?.canUseFullScreenIntent() == false) {
                            Toast.makeText(
                                context,
                                "Enable 'Allow full-screen notifications' so alarms can take over the screen",
                                Toast.LENGTH_LONG
                            ).show()
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                                    .setData("package:${context.packageName}".toUri())
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (_: Exception) { /* settings screen unavailable */ }
                        }
                    }
                    // "Display over other apps" — required on Android 14+ for the alarm
                    // activity to actually appear when launched from the foreground
                    // service while the screen is unlocked. Without it the OS silently
                    // drops the startActivity() call and the user only sees the heads-up.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        !Settings.canDrawOverlays(context)
                    ) {
                        Toast.makeText(
                            context,
                            "Enable 'Display over other apps' so alarms can take over the screen",
                            Toast.LENGTH_LONG
                        ).show()
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                .setData("package:${context.packageName}".toUri())
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (_: Exception) { /* settings screen unavailable */ }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ClockAppScreen(
                        viewModel = viewModel,
                        isDark = isDark,
                        onToggleTheme = {
                            @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
                            darkThemeOverride = !isDark
                        }
                    )
                }
            }
        }
    }
}
