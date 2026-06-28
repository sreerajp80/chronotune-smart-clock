package `in`.sreerajp.chronotune_smart_clock.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * A lightweight HSV color picker (saturation/value box + hue bar + hex field) with no external
 * dependency. Returns the chosen color via [onConfirm].
 */
@Composable
fun ColorPickerDialog(
    initial: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit
) {
    val startHsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(startHsv[0]) }
    var sat by remember { mutableFloatStateOf(startHsv[1]) }
    var value by remember { mutableFloatStateOf(startHsv[2]) }

    val current = Color.hsv(hue, sat, value)
    var hexInput by remember { mutableStateOf(current.toHex()) }
    // Keep the hex field in sync when the picker moves.
    LaunchedEffect(hue, sat, value) { hexInput = current.toHex() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Custom accent color",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(16.dp))

                // ---- Saturation / Value box ----
                var boxSize by remember { mutableStateOf(IntSizeZero) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .onSizeChanged { boxSize = it.width to it.height }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Saturation: white -> full-hue, left to right.
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color.White, Color.hsv(hue, 1f, 1f))
                            )
                        )
                        // Value: transparent -> black, top to bottom.
                        drawRect(
                            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { o ->
                                    sat = (o.x / size.width).coerceIn(0f, 1f)
                                    value = (1f - o.y / size.height).coerceIn(0f, 1f)
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    sat = (change.position.x / size.width).coerceIn(0f, 1f)
                                    value = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                                }
                            }
                    )
                    // Thumb
                    if (boxSize.first > 0) {
                        val tx = sat * boxSize.first
                        val ty = (1f - value) * boxSize.second
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(tx.toInt() - 9, ty.toInt() - 9) }
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(current)
                                .border(2.dp, Color.White, CircleShape)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ---- Hue bar ----
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                (0..6).map { Color.hsv((it * 60).toFloat().coerceAtMost(360f), 1f, 1f) }
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { o ->
                                    hue = (o.x / size.width).coerceIn(0f, 1f) * 360f
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                                }
                            }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ---- Preview + hex ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(current)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.width(14.dp))
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { raw ->
                            hexInput = raw
                            parseHex(raw)?.let { c ->
                                val out = FloatArray(3)
                                android.graphics.Color.colorToHSV(c.toArgb(), out)
                                hue = out[0]; sat = out[1]; value = out[2]
                            }
                        },
                        label = { Text("Hex") },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace, fontSize = 15.sp
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onConfirm(current) }) { Text("Apply") }
                }
            }
        }
    }
}

private val IntSizeZero = 0 to 0

private fun Color.toHex(): String {
    val argb = this.toArgb()
    return String.format("#%06X", 0xFFFFFF and argb)
}

private fun parseHex(raw: String): Color? {
    val cleaned = raw.trim().removePrefix("#")
    if (cleaned.length != 6) return null
    val v = cleaned.toLongOrNull(16) ?: return null
    val r = ((v shr 16) and 0xFF).toInt()
    val g = ((v shr 8) and 0xFF).toInt()
    val b = (v and 0xFF).toInt()
    return Color(r, g, b)
}
