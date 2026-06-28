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

@Composable
fun MusicSchedulerScreen(
    viewModel: ClockViewModel,
    onOpenSettings: () -> Unit
) {
    val schedules by viewModel.musicSchedules.collectAsStateWithLifecycle()
    val is24Hour by AppPrefs.is24Hour.collectAsStateWithLifecycle()
    @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
    var showAddDialog by remember { mutableStateOf(false) }

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
                            onDelete = { viewModel.deleteMusicSchedule(schedule) }
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
            contentColor = Color.White,
            elevation = 12.dp,
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Schedule Action")
        }
    }

    if (showAddDialog) {
        MusicScheduleEditDialog(
            is24Hour = is24Hour,
            onDismiss = {
                @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
                showAddDialog = false
            },
            onSave = { hr, min, dur, lbl, days, track, uri, volume ->
                viewModel.addMusicSchedule(hr, min, dur, lbl, days, track, uri, volume)
                @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
                showAddDialog = false
            }
        )
    }
}


@Composable
fun MusicScheduleCard(
    schedule: MusicSchedule,
    is24Hour: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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

                // Days checklist indicator
                val daysList = listOf("M", "T", "W", "T", "F", "S", "S")
                val activeDays = schedule.getRepeatDaysList()
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    daysList.forEachIndexed { index, day ->
                        val dayNum = index + 1
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


// ──────────────────────────────────────────────────────────────
// V1 · Refined original — Configure Music Schedule design tokens
// Maps directly to dD palette from chronos-dark-parts.jsx for dark
// mode; light mode maps onto the app's warm cream/vermillion light
// theme so the layout stays theme-aware.
// ──────────────────────────────────────────────────────────────
private data class MusicV1Palette(
    val dialogBg: Color,
    val bgCardAlt: Color,
    val hairline: Color,
    val ink: Color,
    val inkSoft: Color,
    val muted: Color,
    val mutedSoft: Color,
    val primary: Color,
    val primaryGradTop: Color,
    val primarySoft: Color,
    val onPrimarySoft: Color,
    val primaryFaint: Color,
    val yellowSoft: Color,
)


@Composable
private fun rememberMusicV1Palette(): MusicV1Palette {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(isDark) {
        if (isDark) {
            MusicV1Palette(
                dialogBg = Color(0xFF0A0B11),
                bgCardAlt = Color(0xFF141822),
                hairline = Color(0xFF262A38),
                ink = Color(0xFFFFFFFF),
                inkSoft = Color(0xFFE3DDD0),
                muted = Color(0xFF9095A2),
                mutedSoft = Color(0xFF6A6F7B),
                primary = Color(0xFFE85A2C),
                primaryGradTop = Color(0xFFF37B4F),
                primarySoft = Color(0xFF7A3318),
                onPrimarySoft = Color.White,
                primaryFaint = Color(0x29E85A2C),
                yellowSoft = Color(0xFF8C6B26),
            )
        } else {
            MusicV1Palette(
                dialogBg = Color(0xFFFAF3E7),
                bgCardAlt = Color(0xFFEDDFC8),
                hairline = Color(0xFFB59B7C),
                ink = Color(0xFF3D2817),
                inkSoft = Color(0xFF6B5946),
                muted = Color(0xFF6B5946),
                mutedSoft = Color(0xFFB59B7C),
                primary = Color(0xFFD2552B),
                primaryGradTop = Color(0xFFE07A4F),
                primarySoft = Color(0xFFF4D5C7),
                onPrimarySoft = Color(0xFF8B3818),
                primaryFaint = Color(0x29D2552B),
                yellowSoft = Color(0xFFC9A765),
            )
        }
    }
}


@Composable
private fun MusicV1TimeDigitBox(
    value: String,
    onValueChange: (String) -> Unit
) {
    val palette = rememberMusicV1Palette()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .size(width = 76.dp, height = 68.dp)
            .background(palette.bgCardAlt, RoundedCornerShape(12.dp))
            .border(1.5.dp, palette.hairline, RoundedCornerShape(12.dp)),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(
            textAlign = TextAlign.Center,
            fontSize = 36.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.ink,
            fontFamily = FontFamily.Monospace,
            letterSpacing = (-0.5).sp
        ),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.primary),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
        ),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.Center) { innerTextField() }
        }
    )
}


@Composable
private fun MusicV1AmPmPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = rememberMusicV1Palette()
    val border = if (selected) palette.primarySoft else palette.hairline
    val bg = if (selected) palette.primarySoft else Color.Transparent
    val fg = if (selected) palette.onPrimarySoft else palette.muted
    Box(
        modifier = Modifier
            .size(width = 50.dp, height = 30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.3.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            letterSpacing = 0.5.sp
        )
    }
}


@Composable
private fun MusicV1DayPill(label: String, active: Boolean, onClick: () -> Unit) {
    val palette = rememberMusicV1Palette()
    val border = if (active) palette.primary else palette.hairline
    val bg = if (active) palette.primaryFaint else Color.Transparent
    val fg = if (active) palette.ink else palette.muted
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.2.dp, border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fg)
    }
}


@Composable
private fun MusicV1MelodyChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = rememberMusicV1Palette()
    val border = if (selected) palette.primarySoft else palette.hairline
    val bg = if (selected) palette.primarySoft else Color.Transparent
    val fg = if (selected) palette.onPrimarySoft else palette.inkSoft
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.3.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fg)
    }
}


// Dotted track slider: filled portion left, empty dots right, with center notch.
@Composable
private fun MusicV1DurationSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange = 5..120,
    step: Int = 5
) {
    val palette = rememberMusicV1Palette()
    val totalSteps = (valueRange.last - valueRange.first) / step + 1
    val currentIndex = ((value - valueRange.first) / step).coerceIn(0, totalSteps - 1)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val pointerMod = Modifier
            .matchParentSize()
            .pointerInput(totalSteps) {
                detectTapGestures { offset ->
                    val frac = (offset.x / widthPx).coerceIn(0f, 1f)
                    val idx = (frac * (totalSteps - 1)).toInt().coerceIn(0, totalSteps - 1)
                    onValueChange(valueRange.first + idx * step)
                }
            }
            .pointerInput(totalSteps) {
                detectHorizontalDragGestures { change, _ ->
                    val frac = (change.position.x / widthPx).coerceIn(0f, 1f)
                    val idx = (frac * (totalSteps - 1)).toInt().coerceIn(0, totalSteps - 1)
                    onValueChange(valueRange.first + idx * step)
                }
            }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(totalSteps) { i ->
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (i < currentIndex) palette.primary else palette.yellowSoft.copy(alpha = 0.85f))
                )
            }
        }

        // Notch positioned at current index
        val notchFraction = if (totalSteps > 1) currentIndex.toFloat() / (totalSteps - 1) else 0f
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (maxWidth * notchFraction) - 1.dp)
                .size(width = 2.dp, height = 26.dp)
                .background(palette.primary, RoundedCornerShape(1.dp))
        )

        Box(modifier = pointerMod)
    }
}


// Thick filled volume slider with center notch and end dot.
@Composable
private fun MusicV1VolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit
) {
    val palette = rememberMusicV1Palette()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val v = value.coerceIn(0f, 1f)

        // Track (background) and filled portion
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(palette.yellowSoft.copy(alpha = 0.85f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(v)
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(palette.primary)
        )
        // Notch
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (maxWidth * v) - 1.dp)
                .size(width = 2.dp, height = 20.dp)
                .background(palette.primary, RoundedCornerShape(1.dp))
        )
        // End dot
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-4).dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(palette.primary)
        )
        // Pointer input layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onValueChange((offset.x / widthPx).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        onValueChange((change.position.x / widthPx).coerceIn(0f, 1f))
                    }
                }
        )
    }
}


@Composable
fun MusicScheduleEditDialog(
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onSave: (hour: Int, minute: Int, durationMin: Int, label: String, repeatDays: List<Int>, track: String, uri: String, volume: Float) -> Unit
) {
    var hourInput by remember { mutableStateOf(if (is24Hour) "22" else "10") }
    var minuteInput by remember { mutableStateOf("00") }
    var isPm by remember { mutableStateOf(true) }
    var labelText by remember { mutableStateOf("") }
    var durationMinutes by remember { mutableIntStateOf(30) }

    val selectedDays = remember { mutableStateListOf<Int>() }
    val selectedAmbients = remember { mutableStateListOf<String>() }
    val customFiles = remember { mutableStateListOf<Pair<String, String>>() } // uri to displayName
    var musicVolume by remember { mutableFloatStateOf(0.6f) }

    val ambientMusicList = listOf("Deep Lofi Lounge", "Morning Breeze", "Cosmic Shimmer", "Ocean Zen")
    val palette = rememberMusicV1Palette()

    val context = LocalContext.current
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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = palette.dialogBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 14.dp)
            ) {
                // Heading
                Text(
                    "Configure Music Schedule",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.primary,
                    letterSpacing = (-0.3).sp,
                    lineHeight = 24.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Time row + AM/PM
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MusicV1TimeDigitBox(
                            value = hourInput,
                            onValueChange = { if (it.length <= 2) hourInput = it.filter { d -> d.isDigit() } }
                        )
                        Text(
                            ":",
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.ink,
                            fontFamily = FontFamily.Monospace
                        )
                        MusicV1TimeDigitBox(
                            value = minuteInput,
                            onValueChange = { if (it.length <= 2) minuteInput = it.filter { d -> d.isDigit() } }
                        )
                    }

                    if (!is24Hour) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            MusicV1AmPmPill("AM", selected = !isPm, onClick = { isPm = false })
                            MusicV1AmPmPill("PM", selected = isPm, onClick = { isPm = true })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Schedule Label — transparent bordered input
                BasicTextField(
                    value = labelText,
                    onValueChange = { labelText = it },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 13.sp,
                        color = palette.ink,
                        fontFamily = FontFamily.SansSerif
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.5.dp, palette.hairline, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        if (labelText.isEmpty()) {
                            Text(
                                "Schedule Label (e.g. Bedtime Sound)",
                                fontSize = 13.sp,
                                color = palette.mutedSoft
                            )
                        }
                        inner()
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Auto Play Duration label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.HourglassEmpty,
                        contentDescription = "Duration",
                        modifier = Modifier.size(16.dp),
                        tint = palette.ink
                    )
                    Text(
                        "Auto Play Duration:",
                        fontSize = 13.sp,
                        color = palette.ink,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "$durationMinutes Minutes",
                        fontSize = 13.sp,
                        color = palette.ink,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                MusicV1DurationSlider(
                    value = durationMinutes,
                    onValueChange = { durationMinutes = it }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Play on Days
                Text(
                    "Play on Days",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                val dayChipsLabel = listOf("Mon" to 1, "Tue" to 2, "Wed" to 3, "Thu" to 4, "Fri" to 5, "Sat" to 6, "Sun" to 7)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    dayChipsLabel.forEach { (label, idx) ->
                        val active = selectedDays.contains(idx)
                        MusicV1DayPill(label = label, active = active) {
                            if (active) selectedDays.remove(idx) else selectedDays.add(idx)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Select Ambient Melodies
                Text(
                    "Select Ambient Melodies",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Pick one or more — they will play in order and loop until end time.",
                    fontSize = 11.5.sp,
                    color = palette.muted,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ambientMusicList.forEach { track ->
                        val selected = selectedAmbients.contains(track)
                        MusicV1MelodyChip(label = track, selected = selected) {
                            if (selected) selectedAmbients.remove(track) else selectedAmbients.add(track)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Choose Music Files button — bordered, transparent
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.5.dp, palette.hairline, RoundedCornerShape(10.dp))
                        .clickable { filePickerLauncher.launch(arrayOf("audio/*")) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = "Upload",
                        tint = palette.inkSoft,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (customFiles.isEmpty()) "Choose Music Files..." else "Add More Files",
                        fontSize = 13.sp,
                        color = palette.inkSoft,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (customFiles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    customFiles.forEachIndexed { index, file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}. ${file.second}",
                                fontSize = 11.sp,
                                color = palette.inkSoft,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            IconButton(onClick = { customFiles.removeAt(index) }) {
                                Icon(
                                    Icons.Default.Clear,
                                    "Remove file",
                                    tint = palette.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Playback Music Volume
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volume",
                        modifier = Modifier.size(16.dp),
                        tint = palette.ink
                    )
                    Text(
                        "Playback Music Volume",
                        fontSize = 13.sp,
                        color = palette.ink,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                MusicV1VolumeSlider(
                    value = musicVolume,
                    onValueChange = { musicVolume = it.coerceIn(0.1f, 1f) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cancel — text-only primary
                    Text(
                        "Cancel",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))

                    val hasAnyTrack = selectedAmbients.isNotEmpty() || customFiles.isNotEmpty()
                    // Add Schedule — gradient + orange glow. Encode the disabled
                    // state via the brush itself (not Modifier.alpha) so the
                    // gradient paints correctly without an offscreen layer.
                    val buttonBrush = if (hasAnyTrack) {
                        Brush.verticalGradient(listOf(palette.primaryGradTop, palette.primary))
                    } else {
                        val dim = palette.primarySoft.copy(alpha = 0.55f)
                        Brush.verticalGradient(listOf(dim, dim))
                    }
                    Box(
                        modifier = Modifier
                            .shadow(
                                elevation = if (hasAnyTrack) 22.dp else 0.dp,
                                shape = RoundedCornerShape(12.dp),
                                spotColor = palette.primary,
                                ambientColor = palette.primary
                            )
                            .background(buttonBrush, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = hasAnyTrack) {
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
                            .padding(horizontal = 22.dp, vertical = 12.dp)
                    ) {
                        Text(
                            "Add Schedule",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.2.sp
                        )
                    }
                }
            }
        }
    }
}


// ==========================================
// RINGING EVENT ALARMS OVERLAY
// ==========================================


