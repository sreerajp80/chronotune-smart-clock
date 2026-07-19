package `in`.sreerajp.chronotune_smart_clock

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.shape.RoundedCornerShape
import `in`.sreerajp.chronotune_smart_clock.data.Alarm
import `in`.sreerajp.chronotune_smart_clock.data.CalendarHolidayImporter
import `in`.sreerajp.chronotune_smart_clock.data.SpecialDay
import `in`.sreerajp.chronotune_smart_clock.ui.ClockViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Manages the shared list of holidays and working days.
 *
 * The list is not owned by any single alarm: each alarm decides through its "Holidays" setting
 * whether to skip these days. Marking a day here immediately re-arms every holiday-aware alarm,
 * so a change takes effect for the very next ring rather than one firing later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidaysScreen(
    viewModel: ClockViewModel,
    onBack: () -> Unit,
    // False when the screen is shown inside Settings, which already provides a title bar and
    // a back arrow — a second one would just be noise.
    showHeader: Boolean = false
) {
    val days by viewModel.specialDays.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Calendars offered in the picker; loaded only after the permission is in hand.
    var calendars by remember {
        mutableStateOf<List<CalendarHolidayImporter.CalendarInfo>>(emptyList())
    }
    var showCalendarPicker by remember { mutableStateOf(false) }
    var permissionRefused by remember { mutableStateOf(false) }

    // Loads the calendar list and opens the picker. Called once the permission is granted.
    fun openCalendarPicker() {
        scope.launch {
            calendars = viewModel.availableCalendars()
            showCalendarPicker = true
        }
    }

    // READ_CALENDAR is requested here and nowhere else, so a user who never taps Import is
    // never prompted for it.
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionRefused = !granted
        if (granted) openCalendarPicker()
    }

    var showPicker by remember { mutableStateOf(false) }
    // The date chosen in the picker, waiting for a name and a kind.
    var pendingEpochDay by remember { mutableStateOf<Long?>(null) }
    var pendingName by remember { mutableStateOf("") }
    var pendingKind by remember { mutableStateOf(SpecialDay.KIND_HOLIDAY) }

    val today = remember { Alarm.todayEpochDay() }
    val dateFormat = remember { SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (showHeader) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Holidays & work days",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Used by alarms set to skip holidays.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Button(
            onClick = {
                pendingName = ""
                pendingKind = SpecialDay.KIND_HOLIDAY
                showPicker = true
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Mark a day")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                permissionRefused = false
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_CALENDAR
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    openCalendarPicker()
                } else {
                    calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                }
            },
            enabled = importState !is ClockViewModel.ImportState.Running,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (importState is ClockViewModel.ImportState.Running) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reading calendar...")
            } else {
                Icon(Icons.Default.CalendarMonth, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import from calendar")
            }
        }

        // Import outcome / permission feedback. Deliberately quiet: one line, no dialog, and
        // no repeat prompting if the permission was refused.
        when {
            permissionRefused -> ImportNote(
                "Calendar access was not given. You can still mark days by hand.",
                isError = true
            )
            importState is ClockViewModel.ImportState.Done -> {
                val done = importState as ClockViewModel.ImportState.Done
                ImportNote(
                    if (done.added == 0) {
                        "No all-day events found in ${done.calendarName}."
                    } else {
                        "Added ${done.added} days from ${done.calendarName}."
                    },
                    isError = false
                )
            }
            importState is ClockViewModel.ImportState.Failed -> ImportNote(
                "Could not read the calendar: " +
                    (importState as ClockViewModel.ImportState.Failed).message,
                isError = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (days.isEmpty()) {
            Text(
                text = "No days marked yet.\n\nMark a public holiday or a company off-day and any " +
                    "alarm set to \"Skip holidays\" will stay quiet that morning. Mark a " +
                    "compensatory working Saturday and a \"Work days only\" alarm will still ring.",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp, start = 4.dp, end = 4.dp)
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(days, key = { it.epochDay }) { day ->
                    SpecialDayRow(
                        day = day,
                        isPast = day.epochDay < today,
                        dateText = dateFormat.format(day.toCalendar().time),
                        onDelete = { viewModel.deleteSpecialDay(day.epochDay) }
                    )
                }
            }
        }
    }

    if (showCalendarPicker) {
        AlertDialog(
            onDismissRequest = { showCalendarPicker = false },
            title = { Text("Pick a calendar") },
            text = {
                if (calendars.isEmpty()) {
                    Text(
                        "No calendars found on this device.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column {
                        Text(
                            text = "Only all-day events are imported, so meetings in a work " +
                                "calendar will not silence your alarms.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyColumn {
                            items(calendars, key = { it.id }) { cal ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showCalendarPicker = false
                                            viewModel.importHolidaysFromCalendar(
                                                cal.id, cal.displayName
                                            )
                                        }
                                        .padding(vertical = 10.dp)
                                ) {
                                    Text(
                                        text = cal.displayName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (cal.accountName.isNotBlank()) {
                                        Text(
                                            text = cal.accountName,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCalendarPicker = false }) { Text("Cancel") }
            }
        )
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedDateMillis != null,
                    onClick = {
                        // The picker hands back UTC midnight, which is exactly the basis the
                        // rest of the app stores days in — so no conversion is needed here.
                        pendingEpochDay =
                            pickerState.selectedDateMillis?.div(Alarm.MILLIS_PER_DAY)
                        showPicker = false
                    }
                ) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(
                state = pickerState,
                title = {
                    Text(
                        "Pick a date to mark",
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }
    }

    val epochDay = pendingEpochDay
    if (epochDay != null) {
        AlertDialog(
            onDismissRequest = { pendingEpochDay = null },
            title = { Text("Mark this day") },
            text = {
                Column {
                    Text(
                        text = dateFormat.format(
                            SpecialDay(epochDay = epochDay).toCalendar().time
                        ),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pendingName,
                        onValueChange = { pendingName = it },
                        label = { Text("Name (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = pendingKind == SpecialDay.KIND_HOLIDAY,
                            onClick = { pendingKind = SpecialDay.KIND_HOLIDAY },
                            label = { Text("Holiday") }
                        )
                        FilterChip(
                            selected = pendingKind == SpecialDay.KIND_WORKING_DAY,
                            onClick = { pendingKind = SpecialDay.KIND_WORKING_DAY },
                            label = { Text("Working day") }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (pendingKind == SpecialDay.KIND_HOLIDAY) {
                            "Alarms set to skip holidays will stay quiet on this day."
                        } else {
                            "Alarms set to \"Work days only\" will ring on this day even if " +
                                "that weekday is not selected."
                        },
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addSpecialDay(epochDay, pendingName, pendingKind)
                    pendingEpochDay = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { pendingEpochDay = null }) { Text("Cancel") }
            }
        )
    }
}

/** One-line feedback under the import button. */
@Composable
private fun ImportNote(text: String, isError: Boolean) {
    Text(
        text = text,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
    )
}

@Composable
private fun SpecialDayRow(
    day: SpecialDay,
    isPast: Boolean,
    dateText: String,
    onDelete: () -> Unit
) {
    val isHoliday = day.kind == SpecialDay.KIND_HOLIDAY
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isHoliday) Icons.Default.BeachAccess else Icons.Default.Work,
                contentDescription = null,
                tint = if (isHoliday) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    // A day that has already gone by can no longer affect any alarm, so it is
                    // dimmed rather than hidden — the user can still see and remove it.
                    color = if (isPast) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = buildString {
                        append(if (isHoliday) "Holiday" else "Working day")
                        if (day.name.isNotBlank()) append(" · ${day.name}")
                        // Marked so the user can tell which rows a re-import will replace and
                        // which ones they entered themselves (those are never touched).
                        if (day.source == SpecialDay.SOURCE_CALENDAR) append(" · from calendar")
                        if (isPast) append(" · past")
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remove this day")
            }
        }
    }
}
