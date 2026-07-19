package `in`.sreerajp.chronotune_smart_clock

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.sreerajp.chronotune_smart_clock.ui.ClockViewModel
import `in`.sreerajp.chronotune_smart_clock.ui.VoiceCommand
import `in`.sreerajp.chronotune_smart_clock.ui.VoiceCommandParser
import `in`.sreerajp.chronotune_smart_clock.ui.VoiceLanguageSupport
import java.util.Calendar
import java.util.Locale

/**
 * Microphone button that listens for a spoken command and acts on it.
 *
 * Listening is done by the phone's own speech app through [RecognizerIntent], so this app
 * never needs the RECORD_AUDIO permission and no audio ever leaves the normal system path.
 *
 * [onNavigate] receives the tab index to move to (1 = Alarms, 3 = Timer) so a timer spoken
 * from the Alarms screen still lands somewhere the user can see it.
 */
@Composable
fun VoiceCommandMicButton(
    viewModel: ClockViewModel,
    onNavigate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val voiceLanguage by AppPrefs.voiceLanguage.collectAsStateWithLifecycle()

    var heard by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<VoiceCommand?>(null) }
    var showNotUnderstood by remember { mutableStateOf(false) }
    var malayalamSupport by remember {
        mutableStateOf(VoiceLanguageSupport.Support.UNKNOWN)
    }

    val prompt = stringResource(R.string.voice_prompt)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val candidates = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            .orEmpty()
        heard = candidates.firstOrNull().orEmpty()

        // The recogniser ranks its guesses; take the first one that actually makes sense.
        val parsed = candidates
            .map { VoiceCommandParser.parse(it, Calendar.getInstance()) }
            .firstOrNull { it !is VoiceCommand.Unknown }

        when (parsed) {
            null -> {
                // Malayalam may have been mis-heard because the voice model is missing —
                // find out now so the failure dialog can say so.
                if (voiceLanguage == VoiceLanguage.MALAYALAM) {
                    VoiceLanguageSupport.check(context, "ml") { malayalamSupport = it }
                }
                showNotUnderstood = true
            }
            // Navigation commands need no confirmation: nothing is created.
            VoiceCommand.ShowAlarms -> onNavigate(1)
            VoiceCommand.ShowTimers -> onNavigate(3)
            else -> pending = parsed
        }
    }

    IconButton(
        modifier = modifier,
        onClick = {
            if (!VoiceLanguageSupport.isRecognitionAvailable(context)) {
                Toast.makeText(context, R.string.voice_no_recognizer, Toast.LENGTH_LONG).show()
                return@IconButton
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceLanguage.toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            try {
                launcher.launch(intent)
            } catch (_: Exception) {
                Toast.makeText(context, R.string.voice_no_recognizer, Toast.LENGTH_LONG).show()
            }
        }
    ) {
        Icon(
            Icons.Default.Mic,
            contentDescription = stringResource(R.string.voice_mic_description)
        )
    }

    // ---- confirm what we understood before creating anything
    pending?.let { command ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.voice_confirm_title)) },
            text = {
                Column {
                    Text(describeVoiceCommand(command))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.voice_heard, heard),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.applyVoiceCommand(command)
                    when (command) {
                        is VoiceCommand.SetTimer -> onNavigate(3)
                        is VoiceCommand.SetAlarm -> onNavigate(1)
                        else -> Unit
                    }
                    pending = null
                }) { Text(stringResource(R.string.voice_confirm_save)) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) {
                    Text(stringResource(R.string.voice_confirm_cancel))
                }
            }
        )
    }

    // ---- nothing understood
    if (showNotUnderstood) {
        val malayalamChosen = voiceLanguage == VoiceLanguage.MALAYALAM
        AlertDialog(
            onDismissRequest = { showNotUnderstood = false },
            title = { Text(stringResource(R.string.voice_not_understood_title)) },
            text = {
                Column {
                    if (heard.isNotBlank()) {
                        Text(stringResource(R.string.voice_heard, heard))
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        stringResource(
                            if (malayalamChosen) R.string.voice_examples_ml
                            else R.string.voice_examples_en
                        )
                    )
                    // Only bring up the voice model when it is a plausible cause.
                    if (malayalamChosen &&
                        malayalamSupport != VoiceLanguageSupport.Support.SUPPORTED
                    ) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(
                                if (malayalamSupport == VoiceLanguageSupport.Support.NOT_SUPPORTED)
                                    R.string.voice_ml_missing_body
                                else R.string.voice_ml_maybe_missing_body
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotUnderstood = false }) {
                    Text(stringResource(R.string.voice_ok))
                }
            },
            dismissButton = {
                if (malayalamChosen &&
                    malayalamSupport != VoiceLanguageSupport.Support.SUPPORTED
                ) {
                    TextButton(onClick = {
                        if (!VoiceLanguageSupport.openVoiceInputSettings(context)) {
                            Toast.makeText(
                                context,
                                R.string.voice_ml_settings_unavailable,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        showNotUnderstood = false
                    }) { Text(stringResource(R.string.voice_ml_open_settings)) }
                }
            }
        )
    }
}

/** Plain-language description of what will be created, shown in the confirm dialog. */
@Composable
private fun describeVoiceCommand(command: VoiceCommand): String {
    val is24Hour by AppPrefs.is24Hour.collectAsStateWithLifecycle()
    return when (command) {
        is VoiceCommand.SetAlarm -> {
            val time = formatTime(command.hour, command.minute, is24Hour)
            val repeat = describeDays(command.days)
            buildString {
                append(stringResource(R.string.voice_alarm_set, time))
                if (repeat.isNotBlank()) append(" · $repeat")
                if (command.label.isNotBlank()) append(" · ${command.label}")
            }
        }

        is VoiceCommand.SetTimer ->
            stringResource(R.string.voice_timer_set, formatDuration(command.durationMs))

        is VoiceCommand.SnoozeAlarm -> "Snooze"
        VoiceCommand.DismissAlarm -> "Stop the alarm"
        VoiceCommand.ShowAlarms -> "Show alarms"
        VoiceCommand.ShowTimers -> "Show timers"
        is VoiceCommand.Unknown -> command.text
    }
}

private fun formatTime(hour: Int, minute: Int, is24Hour: Boolean): String {
    if (is24Hour) return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
    val amPm = if (hour >= 12) "PM" else "AM"
    val display = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return String.format(Locale.getDefault(), "%02d:%02d %s", display, minute, amPm)
}

private fun describeDays(days: List<Int>): String {
    if (days.isEmpty()) return ""
    if (days.size == 7) return "Every day"
    if (days == listOf(1, 2, 3, 4, 5)) return "Weekdays"
    if (days == listOf(6, 7)) return "Weekends"
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    return days.sorted().joinToString(", ") { names[it - 1] }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return buildString {
        if (hours > 0) append("$hours h ")
        if (minutes > 0) append("$minutes min ")
        if (seconds > 0) append("$seconds sec")
    }.trim().ifBlank { "0 min" }
}
