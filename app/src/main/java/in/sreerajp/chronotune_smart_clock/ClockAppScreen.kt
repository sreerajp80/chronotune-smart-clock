package `in`.sreerajp.chronotune_smart_clock

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import `in`.sreerajp.chronotune_smart_clock.ui.ActiveAlarmState
import `in`.sreerajp.chronotune_smart_clock.ui.AlarmService
import `in`.sreerajp.chronotune_smart_clock.ui.ClockViewModel
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ClockAppScreen(
    viewModel: ClockViewModel,
    isDark: Boolean,
    onToggleTheme: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    var showSettings by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                // Hide navigation bar when viewing the Settings overlay
                AnimatedVisibility(
                    visible = !showSettings,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Default.Language, contentDescription = "Clock Tab") },
                            label = { Text("Clock", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Default.Alarm, contentDescription = "Alarms Tab") },
                            label = { Text("Alarms", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = { Icon(Icons.Default.Timer, contentDescription = "Stopwatch Tab") },
                            label = { Text("Stopwatch", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            icon = { Icon(Icons.Default.HourglassEmpty, contentDescription = "Timer Tab") },
                            label = { Text("Timer", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == 4,
                            onClick = { selectedTab = 4 },
                            icon = { Icon(Icons.Default.MusicNote, contentDescription = "Scheduler Tab") },
                            label = { Text("Schedules", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Animated modern transitions between screens
                AnimatedContent(
                    targetState = showSettings,
                    transitionSpec = {
                        if (targetState) {
                            (slideInHorizontally { width -> width / 2 } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width / 2 } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { width -> -width / 2 } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width / 2 } + fadeOut()
                            )
                        }
                    },
                    label = "Settings transition"
                ) { isSettingsVisible ->
                    if (isSettingsVisible) {
                        SettingsScreen(
                            isDark = isDark,
                            onToggleTheme = onToggleTheme,
                            onBack = { showSettings = false }
                        )
                    } else {
                        // Main screens with their respective Settings button actions
                        when (selectedTab) {
                            0 -> WorldClockScreen(viewModel, isDark, onOpenSettings = { showSettings = true })
                            1 -> AlarmsScreen(viewModel, onOpenSettings = { showSettings = true })
                            2 -> StopwatchScreen(viewModel, onOpenSettings = { showSettings = true })
                            3 -> TimerScreen(viewModel, onOpenSettings = { showSettings = true })
                            4 -> MusicSchedulerScreen(viewModel, onOpenSettings = { showSettings = true })
                        }
                    }
                }
            }
        }
        // The dedicated full-screen AlarmActivity (launched by the alarm notification's
        // full-screen intent) is the primary ringing UI. But when it can't come to the
        // front — secured lock screen, FSI/overlay permission missing, or OEM
        // background-launch restrictions — the user ends up here in the main app with the
        // alarm still sounding and no way to stop it. Render the same ringing overlay here
        // as a reliable fallback so the audio always has a visible Dismiss/Snooze screen.
        val context = LocalContext.current
        val activeAlarm by ActiveAlarmState.activeAlarm.collectAsStateWithLifecycle()
        activeAlarm?.let { ring ->
            AlarmRingingOverlay(
                alarm = ring,
                onDismiss = {
                    // Stop via the service so audio + notification + foreground state are
                    // torn down together.
                    try {
                        context.startService(AlarmService.stopIntent(context))
                    } catch (_: Exception) {
                        ActiveAlarmState.dismiss(context)
                    }
                },
                onSnooze = {
                    val snoozeId = ring.id
                    val snoozeLabel = ring.label
                    val snoozeTone = ring.tone
                    val snoozeVolume = ring.volume
                    try {
                        context.startService(AlarmService.stopIntent(context))
                    } catch (_: Exception) {
                        ActiveAlarmState.dismiss(context)
                    }
                    ActiveAlarmState.scheduleSnooze(
                        context = context,
                        id = snoozeId,
                        label = snoozeLabel,
                        tone = snoozeTone,
                        volume = snoozeVolume
                    )
                }
            )
        }
    }
}

// ==========================================
// 1. CLOCK & WORLD TIME SCREEN
// ==========================================


