@file:OptIn(ExperimentalMaterial3Api::class)

package `in`.sreerajp.chronotune_smart_clock

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import `in`.sreerajp.chronotune_smart_clock.data.*
import `in`.sreerajp.chronotune_smart_clock.ui.ClockViewModel
import `in`.sreerajp.chronotune_smart_clock.ui.theme.Button3D
import android.net.Uri
import android.content.Intent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import `in`.sreerajp.chronotune_smart_clock.audio.AudioEngine
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MusicSchedulerScreen(
    viewModel: ClockViewModel,
    onOpenSettings: () -> Unit
) {
    val schedules by viewModel.musicSchedules.collectAsStateWithLifecycle()
    val is24Hour by AppPrefs.is24Hour.collectAsStateWithLifecycle()
    @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
    var showAddDialog by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<MusicSchedule?>(null) }

    val context = LocalContext.current
    // Confirmation toast announcing when the saved schedule will next play.
    fun toastNextPlay(hour: Int, minute: Int, days: List<Int>) {
        val next = nextTriggerTime(hour, minute, days)
        android.widget.Toast.makeText(
            context,
            "Music will play in ${formatTimeUntil(next.timeInMillis)}",
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
                    text = "Music Scheduler",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Open Settings"
                    )
                }
            }
            Text(
                text = "Schedule custom music files or arpeggios to auto-play at specific times for customized durations.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            if (schedules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No schedules active.\nClick the '+' button below to configure a new music player event.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(schedules) { schedule ->
                        MusicScheduleCard(
                            schedule = schedule,
                            is24Hour = is24Hour,
                            onToggle = { viewModel.toggleMusicSchedule(schedule) },
                            onDelete = { viewModel.deleteMusicSchedule(schedule) },
                            onEdit = { editingSchedule = schedule }
                        )
                    }
                }
            }
        }

        Button3D(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(56.dp)
                .testTag("add_schedule_fab"),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = 12.dp,
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Schedule Action")
        }
    }

    if (showAddDialog) {
        MusicScheduleEditDialog(
            existing = null,
            is24Hour = is24Hour,
            onDismiss = {
                @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
                showAddDialog = false
            },
            onSave = { hr, min, dur, lbl, days, track, uri, volume ->
                viewModel.addMusicSchedule(hr, min, dur, lbl, days, track, uri, volume)
                toastNextPlay(hr, min, days)
                @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
                showAddDialog = false
            }
        )
    }

    editingSchedule?.let { current ->
        MusicScheduleEditDialog(
            existing = current,
            is24Hour = is24Hour,
            onDismiss = { editingSchedule = null },
            onSave = { hr, min, dur, lbl, days, track, uri, volume ->
                val daysString = days.sorted().joinToString(",")
                viewModel.updateMusicSchedule(
                    current.copy(
                        hour = hr,
                        minute = min,
                        durationMinutes = dur,
                        label = lbl,
                        daysOfWeek = daysString,
                        musicTrackName = track,
                        customFileUri = uri,
                        volume = volume
                    )
                )
                if (current.isEnabled) toastNextPlay(hr, min, days)
                editingSchedule = null
            }
        )
    }
}


@Composable
fun MusicScheduleCard(
    schedule: MusicSchedule,
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
            containerColor = if (schedule.isEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
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
                        text = schedule.getFormattedTime(is24Hour),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = schedule.label.ifBlank { "Daily Music Playback" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = schedule.isEnabled,
                        onCheckedChange = { onToggle() },
                        modifier = Modifier.testTag("schedule_switch_${schedule.id}")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Schedule",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-rows depicting durations and Track configurations
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MusicNote, "Music Notes", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = schedule.describeTracks() + " (" + schedule.durationMinutes + " min)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Days checklist indicator (ordered by the chosen week-start day)
                val weekStart by AppPrefs.weekStartDay.collectAsStateWithLifecycle()
                val activeDays = schedule.getRepeatDaysList()
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppPrefs.orderedWeekDays(weekStart).forEach { dayNum ->
                        val day = dayLetter(dayNum)
                        val active = activeDays.contains(dayNum)
                        Box(
                            modifier = Modifier
                                .size(18.dp)
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
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}


// Bottom sheet to configure or edit music schedules. Built on the shared
// ScheduleEditSheet kit (see ScheduleEditSheet.kt) so it mirrors the alarm editor:
// a themed two-pane sheet with the main editor and a multi-select tone/melody picker.
@Composable
fun MusicScheduleEditDialog(
    existing: MusicSchedule? = null,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onSave: (hour: Int, minute: Int, durationMin: Int, label: String, repeatDays: List<Int>, track: String, uri: String, volume: Float) -> Unit
) {
    val initialHour24 = existing?.hour ?: 22
    val initialDisplayHour = if (is24Hour) initialHour24 else when {
        initialHour24 == 0 -> 12
        initialHour24 > 12 -> initialHour24 - 12
        else -> initialHour24
    }
    var hourInput by remember { mutableStateOf(String.format(Locale.ROOT, "%02d", initialDisplayHour)) }
    var minuteInput by remember { mutableStateOf(String.format(Locale.ROOT, "%02d", existing?.minute ?: 0)) }
    var isPm by remember { mutableStateOf((existing?.hour ?: 22) >= 12) }
    var labelText by remember { mutableStateOf(existing?.label ?: "") }
    var durationMinutes by remember { mutableIntStateOf(existing?.durationMinutes ?: 30) }

    val selectedDays = remember {
        mutableStateListOf<Int>().apply { if (existing != null) addAll(existing.getRepeatDaysList()) }
    }
    val selectedAmbients = remember {
        mutableStateListOf<String>().apply { if (existing != null) addAll(existing.getAmbientTracks()) }
    }
    // (uri, displayName) pairs — matches MusicSchedule.getCustomFiles().
    val customFiles = remember {
        mutableStateListOf<Pair<String, String>>().apply { if (existing != null) addAll(existing.getCustomFiles()) }
    }
    var musicVolume by remember { mutableFloatStateOf(existing?.volume ?: 0.6f) }

    val ambientMusicList = listOf("Deep Lofi Lounge", "Morning Breeze", "Cosmic Shimmer", "Ocean Zen")

    // Tone picker content state (pane navigation is owned by ScheduleEditSheet)
    var toneTab by remember { mutableStateOf("builtin") } // "builtin" | "files"
    var query by remember { mutableStateOf("") }
    var playingKey by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val previewEngine = remember { AudioEngine(context.applicationContext) }
    fun stopPreview() {
        previewEngine.stop()
        playingKey = null
    }
    DisposableEffect(Unit) {
        onDispose { previewEngine.stop() }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            val uriStr = uri.toString()
            if (customFiles.none { it.first == uriStr }) {
                customFiles.add(uriStr to getFileNameFromUri(context, uri))
            }
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

    val trackCount = selectedAmbients.size + customFiles.size
    val hasAnyTrack = trackCount > 0

    ScheduleEditSheet(
        onDismiss = { previewEngine.stop(); onDismiss() },
        onBackFromTones = { stopPreview() },
        mainPane = { openTonePicker ->
            SheetHeader(
                title = if (existing == null) "Configure Music Schedule" else "Edit Music Schedule",
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
                placeholder = { Text("Schedule Label (e.g. Bedtime Sound)", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Auto Play Duration
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.HourglassEmpty,
                    contentDescription = "Duration",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Auto Play Duration",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "$durationMinutes min",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = durationMinutes.toFloat(),
                onValueChange = { durationMinutes = ((it / 5f).roundToInt() * 5).coerceIn(5, 120) },
                valueRange = 5f..120f,
                steps = (120 - 5) / 5 - 1,
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

            Spacer(modifier = Modifier.height(14.dp))

            SheetSectionLabel("Play on Days")
            Spacer(modifier = Modifier.height(10.dp))
            RepeatDaysRow(
                selectedDays = selectedDays,
                onToggleDay = { day ->
                    if (selectedDays.contains(day)) selectedDays.remove(day)
                    else selectedDays.add(day)
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Music & Melodies — opens the tone picker pane
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        stopPreview()
                        toneTab = if (customFiles.isNotEmpty() && selectedAmbients.isEmpty()) "files" else "builtin"
                        query = ""
                        openTonePicker()
                    }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Music & Melodies",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (hasAnyTrack) "$trackCount selected" else "None selected",
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

            SheetVolumeSlider(
                value = musicVolume,
                onValueChange = { musicVolume = it },
                label = "Playback Volume"
            )

            Spacer(modifier = Modifier.height(14.dp))

            SheetFooter(
                saveLabel = if (existing == null) "Save Schedule" else "Update Schedule",
                saveEnabled = hasAnyTrack,
                onCancel = { previewEngine.stop(); onDismiss() },
                onSave = {
                    var rawHour = hourInput.toIntOrNull() ?: 22
                    val rawMin = (minuteInput.toIntOrNull() ?: 0).coerceIn(0, 59)
                    if (is24Hour) {
                        rawHour = rawHour.coerceIn(0, 23)
                    } else {
                        if (isPm) {
                            if (rawHour < 12) rawHour += 12
                        } else {
                            if (rawHour == 12) rawHour = 0
                        }
                        rawHour = rawHour.coerceIn(0, 23)
                    }
                    val trackStr = selectedAmbients.joinToString("\n")
                    val uriStr = customFiles.joinToString("\n") { "${it.first}\t${it.second}" }
                    onSave(rawHour, rawMin, durationMinutes, labelText, selectedDays.toList(), trackStr, uriStr, musicVolume)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        },
        tonePane = { closeTonePicker ->
            val builtinItems = ambientMusicList.map { ToneItem(it, "Ambient melody", "") }
            val fileItems = customFiles.map { ToneItem(it.second, "From device", it.first) }
            TonePickerPane(
                title = "Music & Melodies",
                query = query,
                onQueryChange = { query = it },
                toneTab = toneTab,
                onTabChange = { stopPreview(); toneTab = it },
                showRingtones = false,
                builtinItems = builtinItems,
                ringtoneItems = emptyList(),
                fileItems = fileItems,
                isSelected = { item ->
                    if (item.uri.isBlank()) selectedAmbients.contains(item.name)
                    else customFiles.any { it.first == item.uri }
                },
                onToggleSelect = { item ->
                    if (item.uri.isBlank()) {
                        if (selectedAmbients.contains(item.name)) selectedAmbients.remove(item.name)
                        else selectedAmbients.add(item.name)
                    } else {
                        // Files in the list are all selected; tapping removes the file.
                        customFiles.removeAll { it.first == item.uri }
                    }
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
                            volume = musicVolume,
                            durationMs = 10_000L
                        )
                        playingKey = key
                    }
                },
                onPickFile = { filePickerLauncher.launch(arrayOf("audio/*")) },
                selectionSummary = if (hasAnyTrack) "$trackCount selected" else "Nothing selected",
                onBack = closeTonePicker,
                onDone = closeTonePicker
            )
        }
    )
}

// ==========================================
// RINGING EVENT ALARMS OVERLAY
// ==========================================


