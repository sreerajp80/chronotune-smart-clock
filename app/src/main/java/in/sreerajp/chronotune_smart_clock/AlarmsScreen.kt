@file:Suppress("ASSIGNED_VALUE_IS_NEVER_READ") // Compose state setters: value is read on recomposition

package `in`.sreerajp.chronotune_smart_clock

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import `in`.sreerajp.chronotune_smart_clock.audio.AudioEngine
import `in`.sreerajp.chronotune_smart_clock.data.*
import `in`.sreerajp.chronotune_smart_clock.ui.ClockViewModel
import `in`.sreerajp.chronotune_smart_clock.ui.theme.Button3D
import android.net.Uri
import android.content.Intent
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight

// Single-letter label for a day number (1=Mon .. 7=Sun).
internal fun dayLetter(n: Int): String = when (n) {
    1 -> "M"; 2 -> "T"; 3 -> "W"; 4 -> "T"; 5 -> "F"; 6 -> "S"; 7 -> "S"; else -> "?"
}

// Short label for a day number (1=Mon .. 7=Sun).
internal fun dayShort(n: Int): String = when (n) {
    1 -> "Mon"; 2 -> "Tue"; 3 -> "Wed"; 4 -> "Thu"; 5 -> "Fri"; 6 -> "Sat"; 7 -> "Sun"; else -> "?"
}

// Formats a pause window (UTC-midnight millis) as e.g. "Jun 20 – Jun 27". Uses the UTC zone
// to match how the date-range picker encodes calendar dates.
private fun formatPauseRange(startMillis: Long, endMillis: Long): String {
    if (startMillis <= 0L || endMillis <= 0L) return "Not set"
    val fmt = java.text.SimpleDateFormat("MMM d", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val start = fmt.format(Date(startMillis))
    val end = fmt.format(Date(endMillis))
    return if (start == end) start else "$start – $end"
}

// Formats a skip-next epoch day (UTC-midnight based) as e.g. "Mon, Jul 20". Uses the UTC zone
// to match how epoch days are stored (see Alarm.localCalendarToEpochDay).
private fun formatSkipDay(epochDay: Long): String {
    val millis = epochDay * Alarm.MILLIS_PER_DAY
    val fmt = java.text.SimpleDateFormat("EEE, MMM d", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return fmt.format(Date(millis))
}

// Formats a start-date epoch day as e.g. "Mon, Jul 20 2026". Same UTC basis as formatSkipDay;
// the year is included because a start date can sit far further out than a skip day.
private fun formatEpochDay(epochDay: Long): String {
    val millis = epochDay * Alarm.MILLIS_PER_DAY
    val fmt = java.text.SimpleDateFormat("EEE, MMM d yyyy", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return fmt.format(Date(millis))
}


@Composable
fun AlarmsScreen(
    viewModel: ClockViewModel,
    onOpenSettings: () -> Unit,
    // Opens the alarm history. Used by the "did not ring" banner, which is only useful if the
    // user can get to the events behind it.
    onOpenHistory: () -> Unit = onOpenSettings,
    // Lets a spoken command that is not about alarms ("timer for 10 minutes") move the
    // user to the tab where the result actually shows up.
    onNavigateTab: (Int) -> Unit = {}
) {
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val is24Hour by AppPrefs.is24Hour.collectAsStateWithLifecycle()
    // An alarm that was armed for a past time and never rang. Null unless there is one the
    // user has not already been told about.
    val missedAlarm by viewModel.missedAlarm.collectAsStateWithLifecycle()
    @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<Alarm?>(null) }

    val context = LocalContext.current
    // Confirmation toast announcing when the saved alarm will next ring.
    fun toastNextRing(
        hour: Int,
        minute: Int,
        days: List<Int>,
        pauseStart: Long,
        pauseEnd: Long,
        holidayMode: String = Alarm.HOLIDAY_MODE_ALL_DAYS,
        startEpochDay: Long = 0L
    ) {
        // Uses the same holiday- and start-date-aware calculation as the scheduler, so the
        // toast never promises a morning the alarm will actually skip.
        val next = nextTriggerTime(
            hour, minute, days, pauseStart, pauseEnd, 0L,
            holidayMode, `in`.sreerajp.chronotune_smart_clock.data.SpecialDayRegistry::kindOf,
            startEpochDay
        )
        android.widget.Toast.makeText(
            context,
            "Alarm will ring in ${formatTimeUntil(next.timeInMillis)}",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Alarms",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VoiceCommandMicButton(viewModel = viewModel, onNavigate = onNavigateTab)
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Open Settings"
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // The one thing the user most needs to know when they open this screen: an alarm
            // that should have gone off did not. Without this, a failure leaves no trace the
            // user would ever see.
            missedAlarm?.let { missed ->
                MissedAlarmBanner(
                    missed = missed,
                    is24Hour = is24Hour,
                    onOpenHistory = {
                        viewModel.dismissMissedAlarmBanner()
                        onOpenHistory()
                    },
                    onDismiss = { viewModel.dismissMissedAlarmBanner() }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (alarms.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No alarms configured.\nClick the '+' button below to create one.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(alarms) { alarm ->
                        AlarmCard(
                            alarm = alarm,
                            is24Hour = is24Hour,
                            onToggle = { viewModel.toggleAlarm(alarm) },
                            onDelete = { viewModel.deleteAlarm(alarm) },
                            onEdit = { editingAlarm = alarm },
                            onSkipNext = { skip -> viewModel.setSkipNext(alarm, skip) }
                        )
                    }
                }
            }
        }

        // Add Alarm floating action button (FAB)
        Button3D(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(56.dp)
                .testTag("add_alarm_fab"),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = 12.dp,
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Alarm Action")
        }
    }

    if (showAddDialog) {
        AlarmEditDialog(
            existing = null,
            is24Hour = is24Hour,
            onDismiss = {
                @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
                showAddDialog = false
            },
            onSave = { hr, min, lbl, days, tone, uri, vol, vib, pStart, pEnd, snooze, challenge, difficulty, count, autoSilence, maxSnooze, snoozeStyle, holidays, startDay ->
                viewModel.addAlarm(hr, min, lbl, days, tone, uri, vol, vib, pStart, pEnd, snooze, challenge, difficulty, count, autoSilence, holidays, maxSnooze, snoozeStyle, startDay)
                toastNextRing(hr, min, days, pStart, pEnd, holidays, startDay)
                showAddDialog = false
            }
        )
    }

    editingAlarm?.let { current ->
        AlarmEditDialog(
            existing = current,
            is24Hour = is24Hour,
            onDismiss = { editingAlarm = null },
            onSave = { hr, min, lbl, days, tone, uri, vol, vib, pStart, pEnd, snooze, challenge, difficulty, count, autoSilence, maxSnooze, snoozeStyle, holidays, startDay ->
                val daysString = days.sorted().joinToString(",")
                viewModel.updateAlarm(
                    current.copy(
                        hour = hr,
                        minute = min,
                        label = lbl,
                        daysOfWeek = daysString,
                        customToneName = tone,
                        customToneUri = uri,
                        volume = vol,
                        isVibrate = vib,
                        snoozeMinutes = snooze,
                        dismissChallenge = challenge,
                        challengeDifficulty = difficulty,
                        challengeCount = count,
                        pauseStartMillis = pStart,
                        pauseEndMillis = pEnd,
                        autoSilenceMinutes = autoSilence,
                        maxSnoozeCount = maxSnooze,
                        snoozeMode = snoozeStyle,
                        holidayMode = holidays,
                        startEpochDay = startDay
                    )
                )
                if (current.isEnabled) toastNextRing(hr, min, days, pStart, pEnd, holidays, startDay)
                editingAlarm = null
            }
        )
    }
}


@Composable
fun AlarmCard(
    alarm: Alarm,
    is24Hour: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onSkipNext: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isEnabled) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = alarm.getFormattedTime(is24Hour),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    if (alarm.label.isNotBlank()) {
                        Text(
                            text = alarm.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    if (alarm.isPauseConfigured()) {
                        val pausedNow = alarm.isPausedNow()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (pausedNow) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = if (pausedNow) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = (if (pausedNow) "Paused · " else "Pause · ") +
                                    formatPauseRange(alarm.pauseStartMillis, alarm.pauseEndMillis),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (pausedNow) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // A start date still ahead explains why an otherwise-armed alarm is silent,
                    // so it is shown. Once the date has passed the badge disappears: the value
                    // is inert from then on and would only be noise.
                    if (alarm.hasFutureStartDate()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventAvailable,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = (if (alarm.isDatedOneShot()) "On " else "From ") +
                                    formatEpochDay(alarm.startEpochDay),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // Holiday awareness is invisible on a normal day, so it gets its own badge —
                    // otherwise a user cannot tell why an alarm stayed quiet one morning.
                    if (alarm.holidayMode != Alarm.HOLIDAY_MODE_ALL_DAYS) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BeachAccess,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (alarm.holidayMode == Alarm.HOLIDAY_MODE_WORKDAYS_ONLY) {
                                    "Work days only"
                                } else {
                                    "Skips holidays"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = alarm.isEnabled,
                        onCheckedChange = { onToggle() },
                        modifier = Modifier.testTag("alarm_switch_${alarm.id}")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete alarm",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Alarm attributes row (Selected Tone & repetition days)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Ringtone icon",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = alarm.customToneName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                // Repetition days representation (ordered by the chosen week-start day)
                val weekStart by AppPrefs.weekStartDay.collectAsStateWithLifecycle()
                val activeDays = alarm.getRepeatDaysList()
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppPrefs.orderedWeekDays(weekStart).forEach { dayNum ->
                        val day = dayLetter(dayNum)
                        val active = activeDays.contains(dayNum)
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(
                                    color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (active) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day,
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                lineHeight = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Skip-next control — only meaningful for an enabled, repeating alarm.
            if (alarm.isEnabled && alarm.getRepeatDaysList().isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                AlarmSkipNextRow(alarm = alarm, onSkipNext = onSkipNext)
            }
        }
    }
}

// Row shown on a repeating alarm card: lets the user skip just the next firing and shows
// the skipped date while a skip is pending.
@Composable
private fun AlarmSkipNextRow(alarm: Alarm, onSkipNext: (Boolean) -> Unit) {
    val skipping = alarm.isSkippingActive()
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onSkipNext(!skipping) }
            .background(
                if (skipping) primary.copy(alpha = 0.12f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = if (skipping) primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = null,
                tint = if (skipping) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (skipping) "Skipping ${formatSkipDay(alarm.skipNextEpochDay)}"
                       else "Skip next alarm",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (skipping) primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = if (skipping) "Undo" else "Skip",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = primary
        )
    }
}


// Bottom sheet to Configure or edit alarms. Hosts two horizontally-sliding panes:
// the main editor and a dedicated tone picker (Built-in / Ringtones / From File).
// Built on the shared ScheduleEditSheet kit (see ScheduleEditSheet.kt).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditDialog(
    existing: Alarm? = null,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onSave: (hour: Int, minute: Int, label: String, repeatDays: List<Int>, tone: String, uri: String, volume: Float, isVibrate: Boolean, pauseStartMillis: Long, pauseEndMillis: Long, snoozeMinutes: Int, dismissChallenge: String, challengeDifficulty: String, challengeCount: Int, autoSilenceMinutes: Int, maxSnoozeCount: Int, snoozeMode: String, holidayMode: String, startEpochDay: Long) -> Unit
) {
    val initialHour24 = existing?.hour ?: 7
    val initialDisplayHour = if (is24Hour) {
        initialHour24
    } else when {
        initialHour24 == 0 -> 12
        initialHour24 > 12 -> initialHour24 - 12
        else -> initialHour24
    }
    var hourInput by remember { mutableStateOf(String.format(Locale.ROOT, "%02d", initialDisplayHour)) }
    var minuteInput by remember { mutableStateOf(String.format(Locale.ROOT, "%02d", existing?.minute ?: 30)) }
    var isPm by remember { mutableStateOf((existing?.hour ?: 7) >= 12) }
    var labelText by remember { mutableStateOf(existing?.label ?: "") }

    val selectedDays = remember {
        mutableStateListOf<Int>().apply {
            if (existing != null) addAll(existing.getRepeatDaysList())
            else addAll(listOf(1, 2, 3, 4, 5)) // Mon–Fri default for new alarms
        }
    }
    var currentTone by remember { mutableStateOf(existing?.customToneName ?: AppPrefs.defaultAlarmTone.value) }
    var customToneUri by remember { mutableStateOf(existing?.customToneUri ?: "") }
    var volumeScale by remember { mutableFloatStateOf(existing?.volume ?: 0.8f) }
    var vibrate by remember { mutableStateOf(existing?.isVibrate ?: true) }

    // Per-alarm snooze length (defaults to the global setting for a new alarm) + dismiss challenge.
    var snoozeMinutes by remember { mutableIntStateOf(existing?.snoozeMinutes ?: AppPrefs.defaultSnoozeMinutes.value) }
    // Per-alarm auto-silence length (0 = ring until dismissed); a new alarm seeds from the global default.
    var autoSilenceMinutes by remember { mutableIntStateOf(existing?.autoSilenceMinutes ?: AppPrefs.defaultAutoSilenceMinutes.value) }
    // New alarms start from the global defaults; an existing alarm always keeps its own values.
    var maxSnoozeCount by remember { mutableIntStateOf(existing?.maxSnoozeCount ?: AppPrefs.defaultMaxSnoozeCount.value) }
    var snoozeMode by remember { mutableStateOf(existing?.snoozeMode ?: AppPrefs.defaultSnoozeMode.value) }
    var holidayMode by remember { mutableStateOf(existing?.holidayMode ?: Alarm.HOLIDAY_MODE_ALL_DAYS) }
    var startEpochDay by remember { mutableLongStateOf(existing?.startEpochDay ?: 0L) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var dismissChallenge by remember { mutableStateOf(existing?.dismissChallenge ?: DismissChallengeType.NONE) }
    var challengeDifficulty by remember { mutableStateOf(existing?.challengeDifficulty ?: "EASY") }
    var challengeCount by remember { mutableIntStateOf(existing?.challengeCount ?: 1) }

    // Pause window (UTC-midnight millis; 0 = no pause). Opens a calendar range picker.
    var pauseStartMillis by remember { mutableLongStateOf(existing?.pauseStartMillis ?: 0L) }
    var pauseEndMillis by remember { mutableLongStateOf(existing?.pauseEndMillis ?: 0L) }
    var showPausePicker by remember { mutableStateOf(false) }

    val tonesList = listOf("Morning Breeze", "Cosmic Shimmer", "Ocean Zen", "Digital Alarm", "Retro Chiptune", "Deep Lofi Lounge")
    val builtinSubs = mapOf(
        "Morning Breeze" to "Soft ambient",
        "Cosmic Shimmer" to "Dreamy synth",
        "Ocean Zen" to "Ocean calm",
        "Digital Alarm" to "Alert buzzer",
        "Retro Chiptune" to "Upbeat scale",
        "Deep Lofi Lounge" to "Soothing arpeggio"
    )

    // Tone picker content state (pane navigation is owned by ScheduleEditSheet)
    var toneTab by remember { mutableStateOf("builtin") } // "builtin" | "ringtones" | "files"
    var query by remember { mutableStateOf("") }
    var playingKey by remember { mutableStateOf<String?>(null) }
    val pickedFiles = remember { mutableStateListOf<Pair<String, String>>() }
    val ringtones = remember { mutableStateListOf<Pair<String, String>>() }

    val context = LocalContext.current
    val previewEngine = remember { AudioEngine(context.applicationContext) }

    fun stopPreview() {
        previewEngine.stop()
        playingKey = null
    }

    // Seed the picked-files tab with any existing custom file, then load system ringtones
    // off the main thread and reconcile (a ringtone selection shouldn't also show as a file).
    LaunchedEffect(Unit) {
        val existingUri = existing?.customToneUri
        if (!existingUri.isNullOrBlank() && pickedFiles.none { it.second == existingUri }) {
            val displayName = existing.customToneName.ifBlank { "Custom file" }
            pickedFiles.add(displayName to existingUri)
        }
        val loaded = withContext(Dispatchers.IO) { loadSystemRingtones(context) }
        ringtones.clear()
        ringtones.addAll(loaded)
        if (customToneUri.isNotBlank() && ringtones.any { it.second == customToneUri }) {
            pickedFiles.removeAll { it.second == customToneUri }
        }
    }

    DisposableEffect(Unit) {
        onDispose { previewEngine.stop() }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            val name = getFileNameFromUri(context, uri)
            val uriStr = uri.toString()
            if (pickedFiles.none { it.second == uriStr }) pickedFiles.add(name to uriStr)
            currentTone = name
            customToneUri = uriStr
            toneTab = "files"
        }
    }

    // Input sanitizers respecting the active hour format
    val hourMax = if (is24Hour) 23 else 12
    val sanitizeHour: (String) -> String = { raw ->
        val digits = raw.filter { it.isDigit() }.take(2)
        when {
            digits.isEmpty() -> ""
            (digits.toIntOrNull() ?: 0) > hourMax -> hourInput
            else -> digits
        }
    }
    val sanitizeMinute: (String) -> String = { raw ->
        val digits = raw.filter { it.isDigit() }.take(2)
        when {
            digits.isEmpty() -> ""
            (digits.toIntOrNull() ?: 0) > 59 -> minuteInput
            else -> digits
        }
    }

    ScheduleEditSheet(
        onDismiss = {
            previewEngine.stop()
            onDismiss()
        },
        onBackFromTones = { stopPreview() },
        overlay = {
            // Single-date picker for the start date / one-time date.
            if (showStartDatePicker) {
                val startState = rememberDatePickerState(
                    initialSelectedDateMillis =
                        startEpochDay.takeIf { it > 0L }?.times(Alarm.MILLIS_PER_DAY)
                )
                DatePickerDialog(
                    onDismissRequest = { showStartDatePicker = false },
                    confirmButton = {
                        TextButton(
                            enabled = startState.selectedDateMillis != null,
                            onClick = {
                                // The picker returns UTC midnight, which is already the basis
                                // epoch days are stored in — no conversion needed.
                                startState.selectedDateMillis?.let {
                                    startEpochDay = it / Alarm.MILLIS_PER_DAY
                                }
                                showStartDatePicker = false
                            }
                        ) { Text("Set") }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = {
                                startEpochDay = 0L
                                showStartDatePicker = false
                            }) { Text("Clear") }
                            TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
                        }
                    }
                ) {
                    DatePicker(
                        state = startState,
                        modifier = Modifier.weight(1f),
                        title = {
                            Text(
                                if (selectedDays.isEmpty()) "Date to ring on" else "Start repeating from",
                                modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    )
                }
            }

            // Calendar range picker for pausing the alarm over a span of days.
            if (showPausePicker) {
                val rangeState = rememberDateRangePickerState(
                    initialSelectedStartDateMillis = pauseStartMillis.takeIf { it > 0L },
                    initialSelectedEndDateMillis = pauseEndMillis.takeIf { it > 0L }
                )
                DatePickerDialog(
                    onDismissRequest = { showPausePicker = false },
                    confirmButton = {
                        TextButton(
                            enabled = rangeState.selectedStartDateMillis != null,
                            onClick = {
                                val start = rangeState.selectedStartDateMillis
                                if (start != null) {
                                    pauseStartMillis = start
                                    // A single-day pause is allowed: fall back to the start date.
                                    pauseEndMillis = rangeState.selectedEndDateMillis ?: start
                                }
                                showPausePicker = false
                            }
                        ) { Text("Set") }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = {
                                pauseStartMillis = 0L
                                pauseEndMillis = 0L
                                showPausePicker = false
                            }) { Text("Clear") }
                            TextButton(onClick = { showPausePicker = false }) { Text("Cancel") }
                        }
                    }
                ) {
                    DateRangePicker(
                        state = rangeState,
                        modifier = Modifier.weight(1f),
                        title = {
                            Text(
                                "Pause alarm for date range",
                                modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    )
                }
            }
        },
        mainPane = { openTonePicker ->
            SheetHeader(
                title = if (existing == null) "Configure Alarm" else "Edit Alarm",
                onClose = { previewEngine.stop(); onDismiss() }
            )
            Spacer(modifier = Modifier.height(16.dp))

            SheetTimeRow(
                hourInput = hourInput,
                onHourChange = { hourInput = sanitizeHour(it) },
                minuteInput = minuteInput,
                onMinuteChange = { minuteInput = sanitizeMinute(it) },
                is24Hour = is24Hour,
                isPm = isPm,
                onAm = { isPm = false },
                onPm = { isPm = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Label
            OutlinedTextField(
                value = labelText,
                onValueChange = { labelText = it },
                placeholder = { Text("Alarm Label (e.g. Work)", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            SheetSectionLabel("Repeat on Days")
            Spacer(modifier = Modifier.height(10.dp))
            RepeatDaysRow(
                selectedDays = selectedDays,
                onToggleDay = { dayNum ->
                    if (selectedDays.contains(dayNum)) selectedDays.remove(dayNum)
                    else selectedDays.add(dayNum)
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Alarm Audio Tone — opens the tone picker pane
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        stopPreview()
                        toneTab = when {
                            customToneUri.isBlank() -> "builtin"
                            ringtones.any { it.second == customToneUri } -> "ringtones"
                            else -> "files"
                        }
                        query = ""
                        openTonePicker()
                    }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Alarm Audio Tone",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = currentTone,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            SheetVolumeSlider(value = volumeScale, onValueChange = { volumeScale = it })

            Spacer(modifier = Modifier.height(4.dp))

            // Vibration toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Vibration alerts",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(checked = vibrate, onCheckedChange = { vibrate = it })
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Snooze length — quick presets plus a custom stepper (1–60 min).
            SheetSectionLabel("Snooze length")
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppPrefs.SNOOZE_CHOICES.forEach { m ->
                    ChoiceChip(label = "$m", selected = snoozeMinutes == m) { snoozeMinutes = m }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Custom minutes",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                StepperControl(
                    value = snoozeMinutes,
                    onDecrement = { snoozeMinutes = (snoozeMinutes - 1).coerceIn(1, 60) },
                    onIncrement = { snoozeMinutes = (snoozeMinutes + 1).coerceIn(1, 60) },
                    valueSuffix = " min"
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Start date. One value, two meanings, decided by whether any weekday is selected:
            // with weekdays it means "don't begin before this day"; with none it pins the
            // single ring to that exact date. The label follows suit so the wording always
            // matches what the alarm will actually do.
            val isRepeating = selectedDays.isNotEmpty()
            SheetSectionLabel(if (isRepeating) "Starts on" else "Date")
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChoiceChip(
                    label = if (startEpochDay > 0L) {
                        formatEpochDay(startEpochDay)
                    } else if (isRepeating) {
                        "Right away"
                    } else {
                        "Next time it comes up"
                    },
                    selected = startEpochDay > 0L
                ) { showStartDatePicker = true }

                if (startEpochDay > 0L) {
                    ChoiceChip(label = "Clear", selected = false) { startEpochDay = 0L }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when {
                    startEpochDay <= 0L && isRepeating ->
                        "Begins on the next selected weekday."
                    startEpochDay <= 0L ->
                        "Rings once, today or tomorrow."
                    isRepeating ->
                        "Stays quiet until ${formatEpochDay(startEpochDay)}, then repeats as usual."
                    else ->
                        "Rings once on ${formatEpochDay(startEpochDay)}, then switches itself off."
                },
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Snooze limit — after this many snoozes the Snooze button goes away.
            SheetSectionLabel("Snooze limit")
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0, 1, 2, 3, 5).forEach { n ->
                    ChoiceChip(
                        label = if (n == 0) "Unlimited" else "$n",
                        selected = maxSnoozeCount == n
                    ) { maxSnoozeCount = n }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (maxSnoozeCount == 0) "You can keep snoozing as long as you like."
                       else "After $maxSnoozeCount snooze(s) only Dismiss is left.",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Snooze style — same gap every time, or a gap that shrinks with each snooze.
            SheetSectionLabel("Snooze style")
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Alarm.SNOOZE_MODE_FIXED to "Same each time",
                    Alarm.SNOOZE_MODE_PROGRESSIVE to "Getting shorter"
                ).forEach { (value, label) ->
                    ChoiceChip(label = label, selected = snoozeMode == value) {
                        snoozeMode = value
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (snoozeMode == Alarm.SNOOZE_MODE_PROGRESSIVE) {
                    // Show the real sequence rather than describing it — it is easier to grasp.
                    val preview = (0 until 4).joinToString(", ") {
                        "${snoozeGapMinutes(snoozeMinutes, snoozeMode, it)}"
                    }
                    "Each snooze is shorter: $preview ... min."
                } else {
                    "Every snooze waits $snoozeMinutes min."
                },
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Holidays — whether this alarm consults the shared holiday / work-day list.
            SheetSectionLabel("Holidays")
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Alarm.HOLIDAY_MODE_ALL_DAYS to "All days",
                    Alarm.HOLIDAY_MODE_SKIP_HOLIDAYS to "Skip holidays",
                    Alarm.HOLIDAY_MODE_WORKDAYS_ONLY to "Work days only"
                ).forEach { (value, label) ->
                    ChoiceChip(label = label, selected = holidayMode == value) {
                        holidayMode = value
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when (holidayMode) {
                    Alarm.HOLIDAY_MODE_SKIP_HOLIDAYS ->
                        "Stays quiet on days marked as holidays."
                    Alarm.HOLIDAY_MODE_WORKDAYS_ONLY ->
                        "Stays quiet on holidays, and still rings on days marked as working days."
                    else -> "Ignores the holiday list and rings on every selected day."
                },
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (holidayMode != Alarm.HOLIDAY_MODE_ALL_DAYS) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Mark days in Settings › Holidays & work days.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Auto-silence — how long the alarm keeps ringing before it stops on its own.
            SheetSectionLabel("Auto-silence after")
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppPrefs.AUTO_SILENCE_CHOICES.forEach { m ->
                    ChoiceChip(
                        label = if (m == 0) "Never" else "$m min",
                        selected = autoSilenceMinutes == m
                    ) { autoSilenceMinutes = m }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (autoSilenceMinutes == 0) "Rings until you dismiss it."
                       else "Stops on its own after $autoSilenceMinutes min if not dismissed.",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Dismiss challenge — a wake-up task required before the alarm can be turned off.
            SheetSectionLabel("Dismiss challenge")
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val types = listOf(
                    DismissChallengeType.NONE to "None",
                    DismissChallengeType.MATH to "Math",
                    DismissChallengeType.PHRASE to "Phrase",
                    DismissChallengeType.MEMORY to "Memory"
                )
                types.forEach { (value, label) ->
                    ChoiceChip(label = label, selected = dismissChallenge == value) {
                        dismissChallenge = value
                    }
                }
            }

            if (dismissChallenge != DismissChallengeType.NONE) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Difficulty",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("EASY" to "Easy", "MEDIUM" to "Medium", "HARD" to "Hard").forEach { (value, label) ->
                        ChoiceChip(label = label, selected = challengeDifficulty == value) {
                            challengeDifficulty = value
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Rounds to solve",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    StepperControl(
                        value = challengeCount,
                        onDecrement = { challengeCount = (challengeCount - 1).coerceIn(1, 10) },
                        onIncrement = { challengeCount = (challengeCount + 1).coerceIn(1, 10) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pause alarm (date range) — opens the calendar range picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showPausePicker = true }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Pause alarm",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val pauseConfigured = pauseStartMillis > 0L && pauseEndMillis > 0L
                    if (pauseConfigured) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = formatPauseRange(pauseStartMillis, pauseEndMillis),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Text(
                            text = "Not set",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            SheetFooter(
                saveLabel = if (existing == null) "Save Alarm" else "Update Alarm",
                onCancel = { previewEngine.stop(); onDismiss() },
                onSave = {
                    var rawHour = hourInput.toIntOrNull() ?: 7
                    val rawMin = (minuteInput.toIntOrNull() ?: 30).coerceIn(0, 59)

                    if (is24Hour) {
                        rawHour = rawHour.coerceIn(0, 23)
                    } else {
                        // Convert standard 12H input back to raw 24H database hours representation
                        if (isPm) {
                            if (rawHour < 12) rawHour += 12
                        } else {
                            if (rawHour == 12) rawHour = 0
                        }
                        rawHour = rawHour.coerceIn(0, 23)
                    }
                    onSave(rawHour, rawMin, labelText, selectedDays.toList(), currentTone, customToneUri, volumeScale, vibrate, pauseStartMillis, pauseEndMillis, snoozeMinutes, dismissChallenge, challengeDifficulty, challengeCount, autoSilenceMinutes, maxSnoozeCount, snoozeMode, holidayMode, startEpochDay)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        },
        tonePane = { closeTonePicker ->
            val builtinItems = tonesList.map { ToneItem(it, builtinSubs[it] ?: "Built-in tone", "") }
            val ringtoneItems = ringtones.map { ToneItem(it.first, "System sound", it.second) }
            val fileItems = pickedFiles.map { ToneItem(it.first, "From device", it.second) }
            TonePickerPane(
                title = "Alarm Tone",
                query = query,
                onQueryChange = { query = it },
                toneTab = toneTab,
                onTabChange = { stopPreview(); toneTab = it },
                showRingtones = true,
                builtinItems = builtinItems,
                ringtoneItems = ringtoneItems,
                fileItems = fileItems,
                isSelected = { item ->
                    if (item.uri.isBlank()) customToneUri.isBlank() && currentTone == item.name
                    else customToneUri == item.uri
                },
                onToggleSelect = { item ->
                    currentTone = item.name
                    customToneUri = item.uri
                },
                playingKey = playingKey,
                onTogglePlay = { item ->
                    val key = item.uri.ifBlank { item.name }
                    if (playingKey == key) {
                        stopPreview()
                    } else {
                        previewEngine.playAudio(
                            toneName = item.name,
                            uriString = item.uri.ifBlank { null },
                            volume = volumeScale,
                            durationMs = 10_000L
                        )
                        playingKey = key
                    }
                },
                onPickFile = { filePickerLauncher.launch(arrayOf("audio/*")) },
                selectionSummary = "Selected · $currentTone",
                onBack = closeTonePicker,
                onDone = closeTonePicker
            )
        }
    )
}


// Small selectable pill chip used for snooze presets, challenge type and difficulty.
@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                color = if (selected) primary.copy(alpha = 0.18f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .border(
                width = 1.3.dp,
                color = if (selected) primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Compact −/value/+ stepper for numeric fields (custom snooze minutes, challenge rounds).
@Composable
private fun StepperControl(
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    valueSuffix: String = ""
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StepperButton(label = "−", onClick = onDecrement)
        Text(
            text = "$value$valueSuffix",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(min = 48.dp),
            textAlign = TextAlign.Center
        )
        StepperButton(label = "+", onClick = onIncrement)
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}


// ==========================================
// 3. STOPWATCH SCREEN
// ==========================================



/**
 * Tells the user, plainly, that an alarm they were relying on did not go off.
 *
 * Shown once per missed alarm: dismissing it records which one was seen, so it does not come
 * back every time the screen is opened.
 */
@Composable
private fun MissedAlarmBanner(
    missed: `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent,
    is24Hour: Boolean,
    onOpenHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    val pattern = if (is24Hour) "EEE d MMM, HH:mm" else "EEE d MMM, hh:mm a"
    val stamp = remember(pattern) {
        java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "An alarm did not ring",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = buildString {
                    append(stamp.format(java.util.Date(missed.scheduledAt)))
                    if (missed.label.isNotBlank()) append(" — ${missed.label}")
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "It was set, but nothing was recorded as ringing at that time.",
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Hide", color = MaterialTheme.colorScheme.onErrorContainer)
                }
                TextButton(onClick = onOpenHistory) {
                    Text("See why", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}
