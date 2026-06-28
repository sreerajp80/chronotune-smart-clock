package `in`.sreerajp.chronotune_smart_clock

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp

// Compose state mutated inside event lambdas is read on a later recomposition (earlier in
// source order), which the "assigned value is never read" dataflow inspection can't see.
@Suppress("AssignedValueIsNeverRead")
@Composable
fun TimerScreen(
    viewModel: ClockViewModel,
    onOpenSettings: () -> Unit
) {
    val timers by viewModel.timers.collectAsStateWithLifecycle()
    val presets by viewModel.timerPresets.collectAsStateWithLifecycle()
    val nowElapsed by viewModel.nowElapsed.collectAsStateWithLifecycle()

    var showAddSheet by remember { mutableStateOf(false) }

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
                text = "Timer",
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

        Spacer(modifier = Modifier.height(12.dp))

        // Quick-start presets
        if (presets.isNotEmpty()) {
            Text(
                "Presets",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(presets, key = { it.id }) { preset ->
                    TimerPresetChip(
                        label = preset.label,
                        duration = formatDurationLabel(preset.durationMs),
                        onStart = { viewModel.startTimerFromPreset(preset) },
                        onDelete = { viewModel.deletePreset(preset) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Active / finished timers list, or an empty state.
        if (timers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No timers running",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Add a timer or tap a preset to get started.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(timers, key = { it.id }) { timer ->
                    TimerRow(
                        timer = timer,
                        nowElapsed = nowElapsed,
                        onPause = { viewModel.pauseTimer(timer.id) },
                        onResume = { viewModel.resumeTimer(timer.id) },
                        onAddMinute = { viewModel.addMinuteToTimer(timer.id) },
                        onCancel = { viewModel.cancelTimer(timer.id) },
                        onDismiss = { viewModel.dismissTimer(timer.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button3D(
            onClick = { showAddSheet = true },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("timer_add_action"),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add timer", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }

    if (showAddSheet) {
        AddTimerSheet(
            onDismiss = { showAddSheet = false },
            onStart = { durationMs, label, tone, uri, volume, saveAsPreset ->
                viewModel.addTimer(durationMs, label, tone, uri, volume)
                if (saveAsPreset) {
                    viewModel.addPreset(label.ifBlank { formatDurationLabel(durationMs) }, durationMs, tone, uri, volume)
                }
                showAddSheet = false
            }
        )
    }
}


// One running/paused/finished timer with its live countdown + controls.
@Composable
private fun TimerRow(
    timer: TimerItem,
    nowElapsed: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onAddMinute: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val finished = timer.state == TimerItem.STATE_FINISHED
    val running = timer.state == TimerItem.STATE_RUNNING
    val remaining = timer.currentRemaining(nowElapsed)
    val progress = if (timer.totalDurationMs > 0L && !finished)
        (remaining.toFloat() / timer.totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val ringColor = if (finished) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Mini progress dial + countdown
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 5.dp.toPx()
                    drawCircle(color = trackColor, style = Stroke(width = strokePx))
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = progress * 360f,
                        useCenter = false,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
                Icon(
                    imageVector = Icons.Default.HourglassEmpty,
                    contentDescription = null,
                    tint = ringColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = timer.label.ifBlank { "Timer" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = if (finished) "Time's up" else formatTimerClock(remaining),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (finished) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (finished) {
                Button3D(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = 4.dp,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("Dismiss", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            } else {
                OutlinedButton(
                    onClick = if (running) onPause else onResume,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(42.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text(if (running) "Pause" else "Resume", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold) }

                OutlinedButton(
                    onClick = onAddMinute,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(42.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("+1 min", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold) }

                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(42.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("Cancel", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}


// A tappable preset chip with a small inline delete affordance.
@Composable
private fun TimerPresetChip(
    label: String,
    duration: String,
    onStart: () -> Unit,
    onDelete: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(primary.copy(alpha = 0.12f), RoundedCornerShape(13.dp))
            .border(1.dp, primary.copy(alpha = 0.4f), RoundedCornerShape(13.dp))
            .clickable(onClick = onStart)
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primary, maxLines = 1)
            Text(duration, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Delete preset $label",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}


// Bottom sheet to configure & start a new timer: duration picker + label + tone/volume picker
// + optional "save as preset". Mirrors the alarm editor's two-pane (main / tones) layout.
// Compose state mutated inside event lambdas is read on a later recomposition (earlier in
// source order), which the "assigned value is never read" dataflow inspection can't see.
@Suppress("AssignedValueIsNeverRead")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTimerSheet(
    onDismiss: () -> Unit,
    onStart: (durationMs: Long, label: String, tone: String, uri: String, volume: Float, saveAsPreset: Boolean) -> Unit
) {
    var pickerHours by remember { mutableIntStateOf(0) }
    var pickerMinutes by remember { mutableIntStateOf(5) }
    var pickerSeconds by remember { mutableIntStateOf(0) }
    var labelText by remember { mutableStateOf("") }
    var saveAsPreset by remember { mutableStateOf(false) }

    var currentTone by remember { mutableStateOf("Cosmic Shimmer") }
    var customToneUri by remember { mutableStateOf("") }
    var volumeScale by remember { mutableFloatStateOf(0.8f) }

    val tonesList = listOf("Morning Breeze", "Cosmic Shimmer", "Ocean Zen", "Digital Alarm", "Retro Chiptune", "Deep Lofi Lounge")
    val builtinSubs = mapOf(
        "Morning Breeze" to "Soft ambient",
        "Cosmic Shimmer" to "Dreamy synth",
        "Ocean Zen" to "Ocean calm",
        "Digital Alarm" to "Alert buzzer",
        "Retro Chiptune" to "Upbeat scale",
        "Deep Lofi Lounge" to "Soothing arpeggio"
    )

    var screen by remember { mutableStateOf("main") } // "main" | "tones"
    var toneTab by remember { mutableStateOf("builtin") }
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

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { loadSystemRingtones(context) }
        ringtones.clear()
        ringtones.addAll(loaded)
    }

    DisposableEffect(Unit) { onDispose { previewEngine.stop() } }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { /* some providers don't grant persistable access */ }
            val name = queryDisplayName(context, uri) ?: "Custom file"
            if (pickedFiles.none { it.second == uri.toString() }) {
                pickedFiles.add(name to uri.toString())
            }
            currentTone = name
            customToneUri = uri.toString()
            toneTab = "files"
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val totalMs = ((pickerHours * 3600L) + (pickerMinutes * 60L) + pickerSeconds) * 1000L

    ModalBottomSheet(
        onDismissRequest = { previewEngine.stop(); onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        BackHandler(enabled = screen == "tones") { stopPreview(); screen = "main" }

        if (screen == "main") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "New Timer",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Duration picker
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    TimerPickerColumn("H", pickerHours, 0..23) { pickerHours = it }
                    Text(":", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    TimerPickerColumn("M", pickerMinutes, 0..59) { pickerMinutes = it }
                    Text(":", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    TimerPickerColumn("S", pickerSeconds, 0..59) { pickerSeconds = it }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = labelText,
                    onValueChange = { labelText = it },
                    placeholder = { Text("Label (optional)", fontSize = 14.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Tone selector row -> opens the tones pane
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { stopPreview(); screen = "tones" }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tone", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(currentTone, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Volume
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sound Volume", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
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
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { saveAsPreset = !saveAsPreset },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = saveAsPreset, onCheckedChange = { saveAsPreset = it })
                    Text("Save as preset", fontSize = 13.5.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button3D(
                    onClick = {
                        stopPreview()
                        onStart(totalMs, labelText.trim(), currentTone, customToneUri, volumeScale, saveAsPreset)
                    },
                    enabled = totalMs > 0L,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start timer", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // ---------------- TONES PANE ----------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { stopPreview(); screen = "main" }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text("Choose tone", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search tones", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(11.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ToneTabChip("Built-in", toneTab == "builtin") { stopPreview(); toneTab = "builtin" }
                    ToneTabChip("Ringtones", toneTab == "ringtones") { stopPreview(); toneTab = "ringtones" }
                    ToneTabChip("From File", toneTab == "files") { stopPreview(); toneTab = "files" }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val q = query.trim().lowercase()
                val items = when (toneTab) {
                    "builtin" -> tonesList.map { ToneItem(it, builtinSubs[it] ?: "Built-in tone", "") }
                    "ringtones" -> ringtones.map { ToneItem(it.first, "System sound", it.second) }
                    else -> pickedFiles.map { ToneItem(it.first, "From device", it.second) }
                }
                val filtered = if (q.isBlank()) items else items.filter { it.name.lowercase().contains(q) }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered) { item ->
                        val selected = if (item.uri.isBlank()) customToneUri.isBlank() && currentTone == item.name
                        else customToneUri == item.uri
                        val key = item.uri.ifBlank { item.name }
                        TonePickerRow(
                            item = item,
                            selected = selected,
                            playing = playingKey == key,
                            onSelect = { currentTone = item.name; customToneUri = item.uri },
                            onTogglePlay = {
                                if (playingKey == key) stopPreview()
                                else {
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
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(13.dp))
                                .clickable { filePickerLauncher.launch(arrayOf("audio/*")) },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add a file", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button3D(
                        onClick = { stopPreview(); screen = "main" },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        elevation = 6.dp,
                        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 11.dp)
                    ) { Text("Done", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}


// "3 min", "1 h 5 min", "45 s" — compact human label for a duration.
private fun formatDurationLabel(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec / 60) % 60
    val s = totalSec % 60
    return buildString {
        if (h > 0) append("$h h ")
        if (m > 0) append("$m min")
        if (h == 0L && m == 0L) append("$s s")
        else if (s > 0 && h == 0L) append(" $s s")
    }.trim().ifBlank { "0 s" }
}


// HH:MM:SS / MM:SS countdown string.
private fun formatTimerClock(ms: Long): String {
    val h = ms / 3600000
    val m = (ms / 60000) % 60
    val s = (ms / 1000) % 60
    return if (h > 0) String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%02d:%02d", m, s)
}


// Resolves a content-Uri's display name for showing a friendly file label.
private fun queryDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    } catch (_: Exception) { null }
}


@Composable
fun TimerPickerColumn(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 6.dp)
    ) {
        IconButton(onClick = { if (value + 1 in range) onValueChange(value + 1) }) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increment $label")
        }
        Text(
            text = String.format(Locale.ROOT, "%02d", value),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        IconButton(onClick = { if (value - 1 in range) onValueChange(value - 1) }) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrement $label")
        }
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}


// ==========================================
// 5. AUTOMATED MUSIC PLAY SCHEDULER
// ==========================================


