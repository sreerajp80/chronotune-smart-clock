package `in`.sreerajp.chronotune_smart_clock

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import android.net.Uri
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun getFileNameFromUri(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } catch (_: Exception) {
            // ignore
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result.substring(cut + 1)
        }
    }
    return result ?: "Custom audio"
}


// A single selectable tone entry (built-in synth, system ringtone, or picked file).
// uri is "" for built-in synth tones.
internal data class ToneItem(val name: String, val sub: String, val uri: String)


// Enumerates the device's system alarm + ringtone sounds. Runs off the main thread
// (callers wrap this in withContext(Dispatchers.IO)). Returns (title, uriString) pairs.
internal fun loadSystemRingtones(context: Context): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    val types = listOf(
        android.media.RingtoneManager.TYPE_ALARM,
        android.media.RingtoneManager.TYPE_RINGTONE
    )
    for (type in types) {
        try {
            val rm = android.media.RingtoneManager(context)
            rm.setType(type)
            val cursor = rm.cursor
            while (cursor.moveToNext()) {
                val title = cursor.getString(android.media.RingtoneManager.TITLE_COLUMN_INDEX)
                val uri = rm.getRingtoneUri(cursor.position)
                if (uri != null && !title.isNullOrBlank()) {
                    result.add(title to uri.toString())
                }
            }
        } catch (_: Exception) {
            // RingtoneManager can throw on some OEMs / restricted profiles — skip gracefully.
        }
    }
    return result.distinctBy { it.second }
}


// Segmented tab chip used in the tone picker (Built-in / Ringtones / From File).
@Composable
internal fun RowScope.ToneTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .weight(1f)
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(
                color = if (selected) primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(9.dp)
            )
            .border(
                width = 1.dp,
                color = if (selected) primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(9.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


// Animated equalizer bars shown on a tone row while it is previewing.
@Composable
internal fun EqBars(color: Color) {
    val transition = rememberInfiniteTransition(label = "eq")
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.height(13.dp)
    ) {
        listOf(0, 180, 360).forEach { delayMs ->
            val scale by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700, delayMillis = delayMs, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$delayMs"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleY = scale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }
                    .background(color, RoundedCornerShape(1.dp))
            )
        }
    }
}


// One row in the tone picker list: play/preview lead, name + sub, radio check.
@Composable
internal fun TonePickerRow(
    item: ToneItem,
    selected: Boolean,
    playing: Boolean,
    onSelect: () -> Unit,
    onTogglePlay: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(
                color = if (selected) primary.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(13.dp)
            )
            .border(
                width = 1.dp,
                color = if (selected) primary.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(13.dp)
            )
            .clickable(onClick = onSelect)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Play / EQ lead button
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    color = if (playing) primary else primary.copy(alpha = 0.14f),
                    shape = CircleShape
                )
                .clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center
        ) {
            if (playing) {
                EqBars(color = Color.White)
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Preview ${item.name}",
                    tint = primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = item.sub,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        // Radio check
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    color = if (selected) primary else Color.Transparent,
                    shape = CircleShape
                )
                .border(
                    width = 2.dp,
                    color = if (selected) primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}


