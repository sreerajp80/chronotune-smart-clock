package `in`.sreerajp.chronotune_smart_clock

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import `in`.sreerajp.chronotune_smart_clock.ui.ActiveAlarmState
import `in`.sreerajp.chronotune_smart_clock.ui.theme.Button3D
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The ringing screen.
 *
 * [onDismiss] reports how the dismiss was earned: how many answers the challenge took and how
 * long it took to solve, both 0 when the alarm has no challenge. Those numbers go into the
 * alarm history, where they are what distinguishes a real wake-up from a half-asleep tap.
 */
@Composable
fun AlarmRingingOverlay(
    alarm: ActiveAlarmState.ActiveAlarm,
    onDismiss: (challengeAttempts: Int, challengeMs: Long) -> Unit,
    onSnooze: () -> Unit
) {
    // When a dismiss challenge is set, tapping DISMISS opens the challenge panel instead of
    // stopping the alarm; only completing the challenge calls onDismiss.
    var showChallenge by remember { mutableStateOf(false) }
    val hasChallenge = alarm.type == "ALARM" &&
        alarm.dismissChallenge != DismissChallengeType.NONE

    // Intercept back navigation: if a challenge is showing, back returns to the ringing screen.
    // Otherwise, deliberately do nothing so the alarm screen does not close or hide on back
    // presses or gestures. Only tapping Dismiss or completing Snooze should dismiss.
    BackHandler(enabled = true) {
        if (showChallenge && hasChallenge) {
            showChallenge = false
        }
    }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Consume all taps landing outside buttons so they do not fall through
                // or cause unexpected window dismissal
                detectTapGestures { }
            }
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .systemBarsPadding()
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
                        tint = if (alarm.type == "ALARM") Color.White else MaterialTheme.colorScheme.onPrimary,
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
            // An alarm that has used up its snooze allowance drops to a single Dismiss, same
            // as music — there is no point offering a button the snooze path would refuse.
            if (alarm.type == "ALARM" && alarm.canSnooze()) {
                // Two actions side by side: Dismiss (tap) on the left,
                // Snooze (swipe up) on the right. The swipe guard on Snooze
                // stops accidental snoozing when the user means to dismiss.
                Row(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Button3D(
                        onClick = { if (hasChallenge) showChallenge = true else onDismiss(0, 0L) },
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .testTag("dismiss_ring_overlay_button"),
                        color = Color(0xFFD32F2F),
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

                    SwipeUpSnoozeButton(
                        snoozeMinutes = alarm.nextSnoozeGapMinutes(),
                        snoozesRemaining = alarm.snoozesRemaining(),
                        onSnooze = onSnooze,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // Music playback has no snooze — and neither does an alarm that has run out.
                if (alarm.type == "ALARM") {
                    Text(
                        text = "No snoozes left — time to get up",
                        fontSize = 14.sp,
                        color = Color.LightGray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                Button3D(
                    onClick = { if (hasChallenge) showChallenge = true else onDismiss(0, 0L) },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(60.dp)
                        .testTag("dismiss_ring_overlay_button"),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
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
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

        // Wake-up challenge overlay — shown when the user taps DISMISS on a challenge alarm.
        if (showChallenge && hasChallenge) {
            DismissChallengePanel(
                challengeType = alarm.dismissChallenge,
                difficulty = alarm.challengeDifficulty,
                count = alarm.challengeCount,
                onSolved = { attempts, elapsedMs -> onDismiss(attempts, elapsedMs) },
                onCancel = { showChallenge = false }
            )
        }
    }
}

/**
 * Snooze control that only fires on a deliberate upward swipe. A plain tap does
 * nothing, so the user cannot snooze by accident when reaching for Dismiss.
 * The button follows the finger while dragging and snaps back if released before
 * the swipe passes the threshold.
 */
@Composable
private fun SwipeUpSnoozeButton(
    snoozeMinutes: Int,
    // Snoozes left after this one is used, or null when the alarm has no limit.
    snoozesRemaining: Int?,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 90.dp.toPx() }

    // Vertical offset (negative = up) that follows the finger, animated so the
    // snap-back on release is smooth.
    val dragOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // Live accumulator read inside the gesture handler (Animatable value lags a frame).
    var accumulated by remember { mutableFloatStateOf(0f) }
    var triggered by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .height(60.dp)
            .testTag("snooze_ring_overlay_button")
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        accumulated = 0f
                        triggered = false
                    },
                    onVerticalDrag = { _, delta ->
                        if (triggered) return@detectVerticalDragGestures
                        accumulated += delta
                        // Clamp so the button rises but never drops below its rest position.
                        val clamped = accumulated.coerceIn(-thresholdPx * 1.3f, 0f)
                        scope.launch { dragOffset.snapTo(clamped) }
                        if (accumulated <= -thresholdPx) {
                            triggered = true
                            onSnooze()
                        }
                    },
                    onDragEnd = {
                        if (!triggered) {
                            scope.launch { dragOffset.animateTo(0f) }
                        }
                    },
                    onDragCancel = {
                        scope.launch { dragOffset.animateTo(0f) }
                    }
                )
            }
            .offset { IntOffset(0, dragOffset.value.roundToInt()) }
            .clip(RoundedCornerShape(30.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                BorderStroke(1.5.dp, Color.White.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(30.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = if (snoozesRemaining == null) {
                    "SNOOZE (${snoozeMinutes} MIN)"
                } else {
                    // Showing the count before the last one is used means the user is never
                    // surprised by the Snooze button vanishing.
                    "SNOOZE (${snoozeMinutes} MIN) · $snoozesRemaining LEFT"
                },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "Swipe up",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 10.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}


