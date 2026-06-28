package `in`.sreerajp.chronotune_smart_clock

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import `in`.sreerajp.chronotune_smart_clock.ui.ClockViewModel
import `in`.sreerajp.chronotune_smart_clock.ui.theme.Button3D
import java.util.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun StopwatchScreen(
    viewModel: ClockViewModel,
    onOpenSettings: () -> Unit
) {
    val stopwatchTime by viewModel.stopwatchTime.collectAsStateWithLifecycle()
    val stopwatchState by viewModel.stopwatchState.collectAsStateWithLifecycle()
    val laps by viewModel.laps.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Stopwatch",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Open Settings"
                )
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))

        // Large high-precision radial milliseconds timer
        Box(
            modifier = Modifier
                .size(240.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f), CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // High-precision clock values conversion
            val min = (stopwatchTime / 60000) % 60
            val sec = (stopwatchTime / 1000) % 60
            val milli = (stopwatchTime / 10) % 100
            val timeString = String.format(Locale.ROOT, "%02d:%02d.%02d", min, sec, milli)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = timeString,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "MIN:SEC.MS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Dynamic Interactive Controllers Box
        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Action / Reset button
            OutlinedButton(
                onClick = {
                    if (stopwatchState == ClockViewModel.StopwatchState.RUNNING) {
                        viewModel.recordLap()
                    } else {
                        viewModel.resetStopwatch()
                    }
                },
                enabled = stopwatchState != ClockViewModel.StopwatchState.IDLE,
                shape = CircleShape,
                modifier = Modifier.size(76.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = if (stopwatchState == ClockViewModel.StopwatchState.RUNNING) "Lap" else "Reset",
                    fontWeight = FontWeight.Bold
                )
            }

            // Center Primary Toggle button
            val isRunning = stopwatchState == ClockViewModel.StopwatchState.RUNNING
            val swColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            Button3D(
                onClick = {
                    if (isRunning) viewModel.pauseStopwatch() else viewModel.startStopwatch()
                },
                shape = CircleShape,
                color = swColor,
                contentColor = Color.White,
                elevation = 14.dp,
                modifier = Modifier
                    .size(92.dp)
                    .testTag("stopwatch_toggle_fab"),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Stopwatch Play Trigger",
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Split Laps Scroll List Section
        if (laps.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
            ) {
                LazyColumn(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(laps) { lap ->
                        val lMin = (lap.splitTimeMs / 60000) % 60
                        val lSec = (lap.splitTimeMs / 1000) % 60
                        val lMil = (lap.splitTimeMs / 10) % 100
                        val elapsedLapStr = String.format(Locale.ROOT, "%02d:%02d.%02d", lMin, lSec, lMil)

                        val dMin = (lap.lapTimeMs / 60000) % 60
                        val dSec = (lap.lapTimeMs / 1000) % 60
                        val dMil = (lap.lapTimeMs / 10) % 100
                        val deltaLapStr = String.format(Locale.ROOT, "+%02d:%02d.%02d", dMin, dSec, dMil)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Lap " + lap.number,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = elapsedLapStr,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = deltaLapStr,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    }
                }
            }
        }
    }
}


// ==========================================
// 4. TIMER SCREEN
// ==========================================


