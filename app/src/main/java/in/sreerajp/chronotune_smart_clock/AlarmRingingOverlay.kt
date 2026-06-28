package `in`.sreerajp.chronotune_smart_clock

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import `in`.sreerajp.chronotune_smart_clock.ui.ActiveAlarmState
import `in`.sreerajp.chronotune_smart_clock.ui.theme.Button3D
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AlarmRingingOverlay(
    alarm: ActiveAlarmState.ActiveAlarm,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse animation")
    val pulseRatio by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse indicator"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Center ringing logo animation
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(180.dp),
                contentAlignment = Alignment.Center
            ) {
                // Throbbing background arpeggiator circles
                Box(
                    modifier = Modifier
                        .size(110.dp * pulseRatio)
                        .background(
                            color = if (alarm.type == "ALARM") Color.Red.copy(alpha = 0.15f) 
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = CircleShape
                        )
                )

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            color = if (alarm.type == "ALARM") Color.Red else MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (alarm.type == "ALARM") Icons.Default.NotificationsActive
                                      else Icons.Default.MusicNote,
                        contentDescription = "Ringing alarm icon",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Text headings descriptions
            Text(
                text = if (alarm.type == "ALARM") "ALARM RINGING" else "MUSIC PLAYING",
                fontSize = 14.sp,
                color = if (alarm.type == "ALARM") Color.Red else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 3.sp
            )

            Text(
                text = alarm.label,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Track: " + alarm.tone,
                fontSize = 14.sp,
                color = Color.LightGray,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Action dismiss/snooze configurations
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button3D(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(60.dp)
                    .testTag("dismiss_ring_overlay_button"),
                color = if (alarm.type == "ALARM") Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(30.dp),
                elevation = 14.dp
            ) {
                Text(
                    text = "DISMISS",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }

            if (alarm.type == "ALARM") {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(52.dp)
                        .testTag("snooze_ring_overlay_button"),
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text(
                        text = "SNOOZE (5 MIN)",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}


