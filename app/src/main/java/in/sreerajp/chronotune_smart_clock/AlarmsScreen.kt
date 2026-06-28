@file:Suppress("ASSIGNED_VALUE_IS_NEVER_READ") // Compose state setters: value is read on recomposition

package `in`.sreerajp.chronotune_smart_clock

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.font.FontFamily
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp

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


@Composable
fun AlarmsScreen(
    viewModel: ClockViewModel,
    onOpenSettings: () -> Unit
) {
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val is24Hour by AppPrefs.is24Hour.collectAsStateWithLifecycle()
    @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<Alarm?>(null) }

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
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Open Settings"
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

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
                            onEdit = { editingAlarm = alarm }
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
            contentColor = Color.White,
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
            onSave = { hr, min, lbl, days, tone, uri, vol, vib, pStart, pEnd ->
                viewModel.addAlarm(hr, min, lbl, days, tone, uri, vol, vib, pStart, pEnd)
                showAddDialog = false
            }
        )
    }

    editingAlarm?.let { current ->
        AlarmEditDialog(
            existing = current,
            is24Hour = is24Hour,
            onDismiss = { editingAlarm = null },
            onSave = { hr, min, lbl, days, tone, uri, vol, vib, pStart, pEnd ->
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
                        pauseStartMillis = pStart,
                        pauseEndMillis = pEnd
                    )
                )
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
    onEdit: () -> Unit
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

                // Repetition days representation
                val daysList = listOf("M", "T", "W", "T", "F", "S", "S")
                val activeDays = alarm.getRepeatDaysList()
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    daysList.forEachIndexed { index, day ->
                        val dayNum = index + 1
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
        }
    }
}


// Filled rounded card used for hour/minute entry in the alarm dialog
@Composable
private fun TimeDigitBox(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp)
            )
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(14.dp)
            ),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(
            textAlign = TextAlign.Center,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = (-0.5).sp
        ),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
        ),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.Center) { innerTextField() }
        }
    )
}


// Vertical pill button used for AM/PM selection
@Composable
private fun AmPmPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(width = 50.dp, height = 30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                color = if (selected) primary.copy(alpha = 0.18f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.3.dp,
                color = if (selected) primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            color = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


// Solid-filled circular day selector chip (Repeat on Days).
@Composable
private fun DayCircleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(
                color = if (selected) primary else Color.Transparent,
                shape = CircleShape
            )
            .border(
                width = 1.dp,
                color = if (selected) primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


// Bottom sheet to Configure or edit alarms. Hosts two horizontally-sliding panes:
// the main editor and a dedicated tone picker (Built-in / Ringtones / From File).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditDialog(
    existing: Alarm? = null,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onSave: (hour: Int, minute: Int, label: String, repeatDays: List<Int>, tone: String, uri: String, volume: Float, isVibrate: Boolean, pauseStartMillis: Long, pauseEndMillis: Long) -> Unit
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
    var currentTone by remember { mutableStateOf(existing?.customToneName ?: "Morning Breeze") }
    var customToneUri by remember { mutableStateOf(existing?.customToneUri ?: "") }
    var volumeScale by remember { mutableFloatStateOf(existing?.volume ?: 0.8f) }
    var vibrate by remember { mutableStateOf(existing?.isVibrate ?: true) }

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

    // Tone picker navigation + content state
    var screen by remember { mutableStateOf("main") } // "main" | "tones"
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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            previewEngine.stop()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        // Back gesture returns from the tone picker to the main editor.
        BackHandler(enabled = screen == "tones") {
            stopPreview()
            screen = "main"
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

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val progress by animateFloatAsState(
                targetValue = if (screen == "tones") 1f else 0f,
                animationSpec = tween(320),
                label = "paneSlide"
            )

            // ---------------- MAIN EDITOR PANE ----------------
            // This pane determines the sheet height; the tone pane matches it (matchParentSize)
            // so switching panes never resizes the sheet and there is no trailing blank space.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = -0.16f * widthPx * progress
                        alpha = 1f - 0.6f * progress
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                // Header: title + close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (existing == null) "Configure Alarm" else "Edit Alarm",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape)
                            .clickable {
                                previewEngine.stop()
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Time picker row — filled cards with AM/PM stack on the right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimeDigitBox(
                        value = hourInput,
                        onValueChange = { hourInput = sanitizeHour(it) },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = ":",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                    TimeDigitBox(
                        value = minuteInput,
                        onValueChange = { minuteInput = sanitizeMinute(it) },
                        modifier = Modifier.weight(1f)
                    )

                    // AM/PM Switcher (12-hour mode only)
                    if (!is24Hour) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AmPmPill(label = "AM", selected = !isPm, onClick = { isPm = false })
                            AmPmPill(label = "PM", selected = isPm, onClick = { isPm = true })
                        }
                    }
                }

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

                // Repeat days
                Text(
                    "Repeat on Days",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))
                val dayChipsLabel = listOf("Mon" to 1, "Tue" to 2, "Wed" to 3, "Thu" to 4, "Fri" to 5, "Sat" to 6, "Sun" to 7)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    dayChipsLabel.forEach { dayPair ->
                        val daySelected = selectedDays.contains(dayPair.second)
                        DayCircleChip(
                            label = dayPair.first,
                            selected = daySelected,
                            onClick = {
                                if (daySelected) selectedDays.remove(dayPair.second)
                                else selectedDays.add(dayPair.second)
                            }
                        )
                    }
                }

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
                            screen = "tones"
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

                // Sound Volume with percentage
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volume Icon",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Sound Volume",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "${(volumeScale * 100).roundToInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = volumeScale,
                    onValueChange = { volumeScale = it },
                    valueRange = 0.1f..1.0f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp),
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    },
                    track = { sliderState ->
                        val range = sliderState.valueRange
                        val fraction = ((sliderState.value - range.start) /
                            (range.endInclusive - range.start)).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                )

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

                Spacer(modifier = Modifier.height(6.dp))

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

                // Footer: Cancel + Save/Update
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            previewEngine.stop()
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button3D(
                        onClick = {
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
                            onSave(rawHour, rawMin, labelText, selectedDays.toList(), currentTone, customToneUri, volumeScale, vibrate, pauseStartMillis, pauseEndMillis)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        elevation = 8.dp,
                        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 13.dp)
                    ) {
                        Text(
                            if (existing == null) "Save Alarm" else "Update Alarm",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ---------------- TONE PICKER PANE ----------------
            Column(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { translationX = widthPx * (1f - progress) }
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Header: back + title
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape)
                            .clickable {
                                stopPreview()
                                screen = "main"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        "Alarm Tone",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Search field
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search tones", fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(11.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ToneTabChip("Built-in", toneTab == "builtin") { stopPreview(); toneTab = "builtin" }
                    ToneTabChip("Ringtones", toneTab == "ringtones") { stopPreview(); toneTab = "ringtones" }
                    ToneTabChip("From File", toneTab == "files") { stopPreview(); toneTab = "files" }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // List
                val q = query.trim().lowercase()
                val items = when (toneTab) {
                    "builtin" -> tonesList.map { ToneItem(it, builtinSubs[it] ?: "Built-in tone", "") }
                    "ringtones" -> ringtones.map { ToneItem(it.first, "System sound", it.second) }
                    else -> pickedFiles.map { ToneItem(it.first, "From device", it.second) }
                }
                val filtered = if (q.isBlank()) items else items.filter { it.name.lowercase().contains(q) }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (toneTab == "files" && pickedFiles.isEmpty() && q.isBlank()) {
                        // Empty state for the From File tab
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No files added yet",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Pick any audio file from your device to use it as your alarm tone.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button3D(
                                onClick = { filePickerLauncher.launch(arrayOf("audio/*")) },
                                shape = RoundedCornerShape(11.dp),
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White,
                                elevation = 6.dp,
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 11.dp)
                            ) {
                                Text("Browse device files", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filtered) { item ->
                                val selected = if (item.uri.isBlank()) {
                                    customToneUri.isBlank() && currentTone == item.name
                                } else {
                                    customToneUri == item.uri
                                }
                                val key = item.uri.ifBlank { item.name }
                                TonePickerRow(
                                    item = item,
                                    selected = selected,
                                    playing = playingKey == key,
                                    onSelect = {
                                        currentTone = item.name
                                        customToneUri = item.uri
                                    },
                                    onTogglePlay = {
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
                                    }
                                )
                            }
                            if (toneTab == "files" && pickedFiles.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(13.dp))
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(13.dp)
                                            )
                                            .clickable { filePickerLauncher.launch(arrayOf("audio/*")) },
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Add another file",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom bar: selected summary + Done
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(0.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Selected · $currentTone",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Button3D(
                        onClick = {
                            stopPreview()
                            screen = "main"
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        elevation = 6.dp,
                        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 11.dp)
                    ) {
                        Text("Done", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
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
    }
}


// ==========================================
// 3. STOPWATCH SCREEN
// ==========================================


