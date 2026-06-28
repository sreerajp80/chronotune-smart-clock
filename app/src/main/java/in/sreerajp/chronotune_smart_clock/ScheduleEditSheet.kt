@file:Suppress("ASSIGNED_VALUE_IS_NEVER_READ") // Compose state setters: value is read on recomposition
@file:OptIn(ExperimentalMaterial3Api::class)

package `in`.sreerajp.chronotune_smart_clock

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.sreerajp.chronotune_smart_clock.ui.theme.Button3D
import kotlin.math.roundToInt

// ============================================================================
// Shared edit-sheet kit for time-of-day schedules (alarms + music schedules).
// Provides one themed ModalBottomSheet scaffold with a sliding two-pane layout
// (main editor ↔ tone picker), plus the common section primitives both editors
// compose. Type-specific fields (alarm pause/vibrate, music duration/multi-track)
// are supplied by each caller.
// ============================================================================

// Filled rounded card used for hour/minute entry.
@Composable
internal fun TimeDigitBox(
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

// Vertical pill button used for AM/PM selection.
@Composable
internal fun AmPmPill(
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
internal fun DayCircleChip(
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
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Sheet header: title + circular close button.
@Composable
internal fun SheetHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
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
                .clickable(onClick = onClose),
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
}

// Time picker row — filled hour/minute cards with optional AM/PM stack.
@Composable
internal fun SheetTimeRow(
    hourInput: String,
    onHourChange: (String) -> Unit,
    minuteInput: String,
    onMinuteChange: (String) -> Unit,
    is24Hour: Boolean,
    isPm: Boolean,
    onAm: () -> Unit,
    onPm: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimeDigitBox(value = hourInput, onValueChange = onHourChange, modifier = Modifier.weight(1f))
        Text(
            text = ":",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        TimeDigitBox(value = minuteInput, onValueChange = onMinuteChange, modifier = Modifier.weight(1f))

        if (!is24Hour) {
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AmPmPill(label = "AM", selected = !isPm, onClick = onAm)
                AmPmPill(label = "PM", selected = isPm, onClick = onPm)
            }
        }
    }
}

// Small bold primary section label.
@Composable
internal fun SheetSectionLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

// "Repeat on Days" horizontal chip row, ordered by the chosen week-start day.
@Composable
internal fun RepeatDaysRow(
    selectedDays: List<Int>,
    onToggleDay: (Int) -> Unit
) {
    val weekStart by AppPrefs.weekStartDay.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppPrefs.orderedWeekDays(weekStart).forEach { dayNum ->
            DayCircleChip(
                label = dayShort(dayNum),
                selected = selectedDays.contains(dayNum),
                onClick = { onToggleDay(dayNum) }
            )
        }
    }
}

// Volume row with icon, label, live percentage, and a custom-thumb slider.
@Composable
internal fun SheetVolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String = "Sound Volume"
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = "Volume Icon",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            label,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            "${(value * 100).roundToInt()}%",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
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
}

// Cancel + Save/Update footer.
@Composable
internal fun SheetFooter(
    saveLabel: String,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onCancel,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button3D(
            onClick = onSave,
            enabled = saveEnabled,
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = 8.dp,
            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 13.dp)
        ) {
            Text(saveLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
        }
    }
}

/**
 * Shared bottom-sheet scaffold with the sliding two-pane (main editor ↔ tone picker) layout.
 *
 * [mainPane] receives `openTonePicker` to slide to the tone pane; [tonePane] receives
 * `closeTonePicker` to slide back. [onBackFromTones] runs whenever the tone pane is dismissed
 * (back gesture or via `closeTonePicker`) — callers use it to stop any tone preview. [overlay]
 * hosts dialogs that should render above the sheet (e.g. the pause date-range picker).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleEditSheet(
    onDismiss: () -> Unit,
    onBackFromTones: () -> Unit = {},
    overlay: @Composable () -> Unit = {},
    mainPane: @Composable ColumnScope.(openTonePicker: () -> Unit) -> Unit,
    tonePane: @Composable ColumnScope.(closeTonePicker: () -> Unit) -> Unit
) {
    var screen by remember { mutableStateOf("main") } // "main" | "tones"
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val closeTones: () -> Unit = {
        onBackFromTones()
        screen = "main"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        BackHandler(enabled = screen == "tones") { closeTones() }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val widthPx = constraints.maxWidth.toFloat()
            val progress by animateFloatAsState(
                targetValue = if (screen == "tones") 1f else 0f,
                animationSpec = tween(320),
                label = "paneSlide"
            )

            // Main editor pane — determines the sheet height; the tone pane matches it.
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
                mainPane { screen = "tones" }
            }

            // Tone picker pane.
            Column(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { translationX = widthPx * (1f - progress) }
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                tonePane(closeTones)
            }
        }

        overlay()
    }
}

/**
 * Shared tone-picker pane content (rendered inside [ScheduleEditSheet]'s tone slot). Works for both
 * single-select (alarms) and multi-select (music) via [isSelected]/[onToggleSelect]; the optional
 * Ringtones tab is shown only when [showRingtones] is true.
 */
@Composable
internal fun ColumnScope.TonePickerPane(
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    toneTab: String,
    onTabChange: (String) -> Unit,
    showRingtones: Boolean,
    builtinItems: List<ToneItem>,
    ringtoneItems: List<ToneItem>,
    fileItems: List<ToneItem>,
    isSelected: (ToneItem) -> Boolean,
    onToggleSelect: (ToneItem) -> Unit,
    playingKey: String?,
    onTogglePlay: (ToneItem) -> Unit,
    onPickFile: () -> Unit,
    selectionSummary: String,
    onBack: () -> Unit,
    onDone: () -> Unit
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
                .clickable(onClick = onBack),
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
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }

    // Search field
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
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
        ToneTabChip("Built-in", toneTab == "builtin") { onTabChange("builtin") }
        if (showRingtones) {
            ToneTabChip("Ringtones", toneTab == "ringtones") { onTabChange("ringtones") }
        }
        ToneTabChip("From File", toneTab == "files") { onTabChange("files") }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // List
    val q = query.trim().lowercase()
    val items = when (toneTab) {
        "builtin" -> builtinItems
        "ringtones" -> ringtoneItems
        else -> fileItems
    }
    val filtered = if (q.isBlank()) items else items.filter { it.name.lowercase().contains(q) }

    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        if (toneTab == "files" && fileItems.isEmpty() && q.isBlank()) {
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
                    "Pick any audio file from your device to use it as your tone.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button3D(
                    onClick = onPickFile,
                    shape = RoundedCornerShape(11.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
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
                    val key = item.uri.ifBlank { item.name }
                    TonePickerRow(
                        item = item,
                        selected = isSelected(item),
                        playing = playingKey == key,
                        onSelect = { onToggleSelect(item) },
                        onTogglePlay = { onTogglePlay(item) }
                    )
                }
                if (toneTab == "files" && fileItems.isNotEmpty()) {
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
                                .clickable(onClick = onPickFile),
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

    // Bottom bar: selection summary + Done
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
            text = selectionSummary,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Button3D(
            onClick = onDone,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = 6.dp,
            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 11.dp)
        ) {
            Text("Done", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
