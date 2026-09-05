package `in`.sreerajp.chronotune_smart_clock

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent
import `in`.sreerajp.chronotune_smart_clock.data.formatShortDuration
import `in`.sreerajp.chronotune_smart_clock.data.delayMs
import `in`.sreerajp.chronotune_smart_clock.data.looksHalfAsleep
import `in`.sreerajp.chronotune_smart_clock.data.summaryLine
import `in`.sreerajp.chronotune_smart_clock.ui.ClockViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What the alarms actually did, day by day.
 *
 * The app used to keep no record at all, so a missed alarm and a quiet morning looked the same,
 * and an alarm dismissed half asleep left no trace. Every row here is one thing that happened:
 * armed, rang, suppressed, snoozed, dismissed, or never rang.
 *
 * "All" is the default filter and shows everything, including the quiet system rows. They are
 * styled more softly so the important ones stand out, but nothing is hidden.
 */
@Composable
fun HistoryScreen(
    viewModel: ClockViewModel,
    onBack: () -> Unit,
    showHeader: Boolean = false
) {
    val events by viewModel.alarmEvents.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dayFormat = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()) }

    // Save-as dialog for the plain-text export.
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { viewModel.exportAlarmHistory(it) } }

    val shown = remember(events, filter) { events.filter { filter.matches(it) } }
    // Grouped by calendar day, newest first. The list is already in that order, so grouping
    // keeps it without another sort.
    val grouped = remember(shown) { shown.groupBy { dayKeyOf(it.actualAt) } }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (showHeader) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Text(
                    text = "Alarm history",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        SummaryStrip(events)

        Spacer(modifier = Modifier.height(12.dp))

        // Filters. Wide on purpose — the row scrolls rather than wrapping or squashing.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HistoryFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(option.label, fontSize = 13.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    val name = "chronotune-alarm-history-" +
                        SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date()) +
                        ".txt"
                    exportLauncher.launch(name)
                },
                enabled = events.isNotEmpty()
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export", fontSize = 13.sp)
            }
            TextButton(onClick = { confirmClear = true }, enabled = events.isNotEmpty()) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clear", fontSize = 13.sp)
            }
        }

        if (shown.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (events.isEmpty()) {
                        "Nothing recorded yet.\nThe next time an alarm is set or rings, it will show up here."
                    } else {
                        "No events match this filter."
                    },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                grouped.forEach { (dayStart, rows) ->
                    item(key = "day-$dayStart") {
                        Text(
                            text = dayLabel(dayStart, dayFormat),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                        )
                    }
                    items(rows, key = { it.id }) { event ->
                        EventRow(
                            event = event,
                            time = timeFormat.format(Date(event.actualAt)),
                            expanded = expandedId == event.id,
                            onToggle = {
                                expandedId = if (expandedId == event.id) null else event.id
                            }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the history?") },
            text = { Text("Every recorded event will be deleted. Alarms themselves are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAlarmHistory()
                    confirmClear = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Keep") }
            }
        )
    }
}

/** Last seven days at a glance, so a bad week is obvious before reading any rows. */
@Composable
private fun SummaryStrip(events: List<AlarmEvent>) {
    val weekAgo = remember { System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000 }
    val recent = remember(events) { events.filter { it.actualAt >= weekAgo } }
    val rang = recent.count { it.event == AlarmEvent.FIRED }
    val missed = recent.count { it.event == AlarmEvent.MISSED }
    val snoozed = recent.count { it.event == AlarmEvent.SNOOZED }
    val delays = recent.mapNotNull { if (it.event == AlarmEvent.FIRED) it.delayMs() else null }
    val averageDelay = if (delays.isEmpty()) 0L else delays.sum() / delays.size

    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "LAST 7 DAYS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryCell("Rang", rang.toString())
                SummaryCell(
                    "Did not ring",
                    missed.toString(),
                    highlight = missed > 0
                )
                SummaryCell("Snoozed", snoozed.toString())
                SummaryCell(
                    "Typical delay",
                    if (delays.isEmpty()) "—" else formatShortDuration(averageDelay)
                )
            }
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = if (highlight) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EventRow(
    event: AlarmEvent,
    time: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val isSystem = event.event in AlarmEvent.SYSTEM_EVENTS
    val accent = accentFor(event)
    // System rows stay legible but recede, so a missed alarm is never buried under a row of
    // routine "set to ring" entries.
    val bodyColor =
        if (isSystem) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = time,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = bodyColor,
                modifier = Modifier.width(50.dp)
            )
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (event.label.isBlank()) defaultLabelFor(event) else event.label,
                    fontSize = 14.sp,
                    fontWeight = if (isSystem) FontWeight.Normal else FontWeight.Bold,
                    color = bodyColor
                )
                Text(
                    text = event.summaryLine(),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (event.looksHalfAsleep()) {
                    Text(
                        text = "Turned off within seconds, phone still locked — did you sleep through it?",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 68.dp, top = 6.dp)) {
                val stamp = remember {
                    SimpleDateFormat("d MMM yyyy, HH:mm:ss", Locale.getDefault())
                }
                if (event.scheduledAt > 0L) {
                    DetailLine("Due at", stamp.format(Date(event.scheduledAt)))
                    event.delayMs()?.let { DetailLine("Late by", formatShortDuration(it)) }
                }
                DetailLine("Happened at", stamp.format(Date(event.actualAt)))
                if (event.ringDurationMs > 0L) {
                    DetailLine("Had been ringing for", formatShortDuration(event.ringDurationMs))
                }
                if (event.dismissSource != AlarmEvent.SOURCE_NONE) {
                    DetailLine(
                        "Turned off from",
                        when (event.dismissSource) {
                            AlarmEvent.SOURCE_FULL_SCREEN -> "The alarm screen"
                            AlarmEvent.SOURCE_NOTIFICATION -> "The notification"
                            AlarmEvent.SOURCE_AUTO_SILENCE -> "Nobody — it stopped by itself"
                            else -> event.dismissSource
                        }
                    )
                }
                if (event.challengeType != "NONE" && event.challengeType.isNotBlank()) {
                    DetailLine(
                        "Challenge",
                        "${event.challengeType} (${event.challengeDifficulty}), " +
                            "${event.challengeRounds} round(s)"
                    )
                    if (event.challengeAttempts > 0) {
                        DetailLine("Answers given", event.challengeAttempts.toString())
                    }
                    if (event.challengeSolvedMs > 0L) {
                        DetailLine("Solved in", formatShortDuration(event.challengeSolvedMs))
                    }
                }
                if (event.snoozeIndex > 0) {
                    DetailLine(
                        "Snooze",
                        buildString {
                            append("number ${event.snoozeIndex}")
                            if (event.snoozeLimit > 0) append(" of ${event.snoozeLimit}")
                            else append(" (no limit)")
                            if (event.snoozeMode.isNotBlank()) append(", ${event.snoozeMode}")
                        }
                    )
                }
                if (event.snoozeGapMinutes > 0) {
                    DetailLine("Snooze gap", "${event.snoozeGapMinutes} min")
                }
                if (event.nextRingAt > 0L) {
                    DetailLine("Rings again at", stamp.format(Date(event.nextRingAt)))
                }
                DetailLine(
                    "Phone at the time",
                    listOf(
                        if (event.screenOn) "screen on" else "screen off",
                        if (event.deviceLocked) "locked" else "unlocked",
                        if (event.dozeIdle) "deep sleep" else "awake",
                        if (event.exactAllowed) "exact alarms allowed" else "exact alarms blocked"
                    ).joinToString(", ")
                )
                if (event.detail.isNotBlank()) DetailLine("Note", event.detail)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(130.dp)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Colour of the dot beside a row — red for trouble, green for a clean ring. */
@Composable
private fun accentFor(event: AlarmEvent): Color = when (event.event) {
    AlarmEvent.MISSED, AlarmEvent.RING_FAILED -> MaterialTheme.colorScheme.error
    AlarmEvent.FIRED -> Color(0xFF43A047)
    AlarmEvent.DISMISSED -> MaterialTheme.colorScheme.primary
    AlarmEvent.SNOOZED, AlarmEvent.AUTO_SILENCED, AlarmEvent.QUEUED -> Color(0xFFF9A825)
    AlarmEvent.SUPPRESSED_PAUSE, AlarmEvent.SUPPRESSED_SKIP, AlarmEvent.SUPPRESSED_HOLIDAY ->
        MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.outline
}

/** Rows written by the system itself carry no alarm label. */
private fun defaultLabelFor(event: AlarmEvent): String = when (event.event) {
    AlarmEvent.RESCHEDULED_BOOT, AlarmEvent.RESCHEDULED_WATCHDOG -> "All alarms"
    else -> "Alarm ${event.alarmId}"
}

/** Local midnight for a timestamp, used to group rows by day. */
private fun dayKeyOf(timeMs: Long): Long {
    val cal = java.util.Calendar.getInstance().apply {
        timeInMillis = timeMs
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun dayLabel(dayStart: Long, format: SimpleDateFormat): String {
    val today = dayKeyOf(System.currentTimeMillis())
    val oneDay = 24L * 60 * 60 * 1000
    return when (dayStart) {
        today -> "TODAY"
        today - oneDay -> "YESTERDAY"
        else -> format.format(Date(dayStart)).uppercase(Locale.getDefault())
    }
}

/**
 * The filter chips. ALL is first and default, and really does mean all — the quieter system
 * events are included rather than hidden.
 */
enum class HistoryFilter(val label: String) {
    ALL("All"),
    MISSED("Did not ring"),
    RANG("Rang"),
    SNOOZED("Snoozed"),
    DISMISSED("Dismissed"),
    SUPPRESSED("Skipped"),
    SYSTEM("System");

    fun matches(event: AlarmEvent): Boolean = when (this) {
        ALL -> true
        MISSED -> event.event == AlarmEvent.MISSED || event.event == AlarmEvent.RING_FAILED
        RANG -> event.event == AlarmEvent.FIRED || event.event == AlarmEvent.QUEUED
        SNOOZED -> event.event == AlarmEvent.SNOOZED
        DISMISSED -> event.event == AlarmEvent.DISMISSED || event.event == AlarmEvent.AUTO_SILENCED
        SUPPRESSED -> event.event in setOf(
            AlarmEvent.SUPPRESSED_PAUSE,
            AlarmEvent.SUPPRESSED_SKIP,
            AlarmEvent.SUPPRESSED_HOLIDAY
        )
        SYSTEM -> event.event in AlarmEvent.SYSTEM_EVENTS
    }
}
