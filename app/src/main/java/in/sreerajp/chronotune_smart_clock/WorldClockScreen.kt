package `in`.sreerajp.chronotune_smart_clock

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import `in`.sreerajp.chronotune_smart_clock.data.*
import `in`.sreerajp.chronotune_smart_clock.ui.ClockViewModel
import `in`.sreerajp.chronotune_smart_clock.ui.theme.Button3D
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun WorldClockScreen(
    viewModel: ClockViewModel,
    isDark: Boolean,
    onOpenSettings: () -> Unit
) {
    val currentTime by viewModel.currentTime.collectAsStateWithLifecycle()
    val worldClocks by viewModel.worldClocks.collectAsStateWithLifecycle()
    val is24Hour by AppPrefs.is24Hour.collectAsStateWithLifecycle()
    @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
    var showAddClockDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Upper action bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Chronos Clock",
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

        Spacer(modifier = Modifier.height(24.dp))

        // MODERN ANALOG CLOCK — premium watch style
        val clockPrimary = MaterialTheme.colorScheme.primary
        val clockSecondary = MaterialTheme.colorScheme.secondary
        val clockOutline = MaterialTheme.colorScheme.outline
        val accentRed = if (isDark) Color(0xFFFF1744) else Color(0xFFC62828)
        val hubInset = if (isDark) Color(0xFF181D2A) else Color(0xFFFFFEFA)

        Box(
            modifier = Modifier
                .size(280.dp)
                .shadow(
                    elevation = if (isDark) 28.dp else 14.dp,
                    shape = CircleShape,
                    spotColor = clockPrimary.copy(alpha = if (isDark) 0.55f else 0.30f),
                    ambientColor = clockPrimary.copy(alpha = if (isDark) 0.25f else 0.12f)
                )
                .background(
                    brush = Brush.radialGradient(
                        colors = if (isDark) {
                            listOf(
                                Color(0xFF252B3D),
                                Color(0xFF181D2A),
                                Color(0xFF0A0C12)
                            )
                        } else {
                            listOf(
                                Color(0xFFFFFEFA),
                                Color(0xFFFAF1DD),
                                Color(0xFFE8D9C0)
                            )
                        }
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f

                // ===== Bezel: 3 concentric rings =====
                drawCircle(
                    color = clockPrimary,
                    radius = radius,
                    style = Stroke(width = 2.5.dp.toPx())
                )
                drawCircle(
                    color = clockPrimary.copy(alpha = 0.20f),
                    radius = radius - 5.dp.toPx(),
                    style = Stroke(width = 1.dp.toPx())
                )
                drawCircle(
                    color = clockOutline.copy(alpha = 0.18f),
                    radius = radius - 32.dp.toPx(),
                    style = Stroke(width = 0.7.dp.toPx())
                )

                // ===== 60 ticks (quarters big, hours medium, minutes thin) =====
                val tickOuterR = radius - 9.dp.toPx()
                for (i in 0 until 60) {
                    val angle = Math.toRadians(i * 6.0 - 90.0)
                    val isQuarter = i % 15 == 0
                    val isHour = i % 5 == 0
                    val tickLen = when {
                        isQuarter -> 18.dp.toPx()
                        isHour -> 11.dp.toPx()
                        else -> 4.5.dp.toPx()
                    }
                    val strokeW = when {
                        isQuarter -> 3.5.dp.toPx()
                        isHour -> 2.dp.toPx()
                        else -> 1.dp.toPx()
                    }
                    val tickColor = when {
                        isQuarter -> clockPrimary
                        isHour -> clockPrimary.copy(alpha = 0.70f)
                        else -> clockOutline.copy(alpha = 0.40f)
                    }
                    val innerR = tickOuterR - tickLen
                    val cosA = cos(angle).toFloat()
                    val sinA = sin(angle).toFloat()
                    drawLine(
                        color = tickColor,
                        start = Offset(center.x + innerR * cosA, center.y + innerR * sinA),
                        end = Offset(center.x + tickOuterR * cosA, center.y + tickOuterR * sinA),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                }

                // ===== Smooth time =====
                val cal = Calendar.getInstance()
                cal.timeInMillis = currentTime
                val ms = cal.get(Calendar.MILLISECOND)
                val secSmooth = cal.get(Calendar.SECOND) + ms / 1000.0
                val minSmooth = cal.get(Calendar.MINUTE) + secSmooth / 60.0
                val hrSmooth = (cal.get(Calendar.HOUR) % 12) + minSmooth / 60.0

                // ===== Helper: build a tapered hand path =====
                fun taperedHand(
                    angleDeg: Double,
                    length: Float,
                    tail: Float,
                    baseWidth: Float
                ): Path {
                    val a = Math.toRadians(angleDeg - 90.0)
                    val cosA = cos(a).toFloat()
                    val sinA = sin(a).toFloat()
                    val pcos = -sinA
                    val psin = cosA
                    val baseCx = center.x - tail * cosA
                    val baseCy = center.y - tail * sinA
                    val tipX = center.x + length * cosA
                    val tipY = center.y + length * sinA
                    val half = baseWidth / 2f
                    return Path().apply {
                        moveTo(baseCx + pcos * half, baseCy + psin * half)
                        lineTo(tipX, tipY)
                        lineTo(baseCx - pcos * half, baseCy - psin * half)
                        close()
                    }
                }

                // ===== Hour hand (tapered + glow) =====
                val hrPath = taperedHand(
                    angleDeg = hrSmooth * 30.0,
                    length = radius * 0.50f,
                    tail = radius * 0.13f,
                    baseWidth = 9.dp.toPx()
                )
                drawPath(
                    path = hrPath,
                    color = clockPrimary.copy(alpha = 0.28f),
                    style = Stroke(width = 10.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round)
                )
                drawPath(path = hrPath, color = clockPrimary)

                // ===== Minute hand (tapered + glow) =====
                val minPath = taperedHand(
                    angleDeg = minSmooth * 6.0,
                    length = radius * 0.74f,
                    tail = radius * 0.15f,
                    baseWidth = 7.dp.toPx()
                )
                drawPath(
                    path = minPath,
                    color = clockSecondary.copy(alpha = 0.24f),
                    style = Stroke(width = 8.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round)
                )
                drawPath(path = minPath, color = clockSecondary)

                // ===== Second hand (watch-style with lollipop) =====
                val secA = Math.toRadians(secSmooth * 6.0 - 90.0)
                val secCos = cos(secA).toFloat()
                val secSin = sin(secA).toFloat()
                val secLen = radius * 0.86f
                val secTail = radius * 0.22f
                val secTailEnd = Offset(center.x - secTail * secCos, center.y - secTail * secSin)
                val secTipEnd = Offset(center.x + secLen * secCos, center.y + secLen * secSin)

                drawLine(
                    color = accentRed,
                    start = secTailEnd,
                    end = secTipEnd,
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Counterbalance circle at tail
                drawCircle(
                    color = accentRed,
                    radius = 5.dp.toPx(),
                    center = secTailEnd
                )
                drawCircle(
                    color = hubInset,
                    radius = 2.dp.toPx(),
                    center = secTailEnd
                )
                // Lollipop dot near tip (outlined ring)
                val lolliCenter = Offset(
                    center.x + (secLen * 0.78f) * secCos,
                    center.y + (secLen * 0.78f) * secSin
                )
                drawCircle(
                    color = accentRed,
                    radius = 4.dp.toPx(),
                    center = lolliCenter,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // ===== Center hub: 4 layers =====
                drawCircle(color = clockPrimary.copy(alpha = 0.30f), radius = 13.dp.toPx())
                drawCircle(color = clockPrimary, radius = 8.dp.toPx())
                drawCircle(color = hubInset, radius = 4.5.dp.toPx())
                drawCircle(color = accentRed, radius = 2.2.dp.toPx())
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // MODERN DIGITAL TIME DISPLAY
        val cal = Calendar.getInstance()
        cal.timeInMillis = currentTime
        val displayHour = if (is24Hour) {
            cal.get(Calendar.HOUR_OF_DAY)
        } else {
            val h = cal.get(Calendar.HOUR)
            if (h == 0) 12 else h
        }
        val hh = String.format(Locale.ROOT, "%02d", displayHour)
        val mm = String.format(Locale.ROOT, "%02d", cal.get(Calendar.MINUTE))
        val ss = String.format(Locale.ROOT, "%02d", cal.get(Calendar.SECOND))
        val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "$hh:$mm",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = 2.sp
                )
                Text(
                    text = ":$ss",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                )
                if (!is24Hour) {
                    Text(
                        text = " $amPm",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                    )
                }
            }
        }

        // Timezone description
        Text(
            text = TimeZone.getDefault().displayName,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 6.dp),
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // MULTIPLE WORLD CLOCKS LOCATION BAR HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Locations",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Button3D(
                onClick = { showAddClockDialog = true },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Clock", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Location", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Scrolling lists of locations clocks
        if (worldClocks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Add clocks for other cities to compare time zones.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("world_clocks_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(worldClocks) { clock ->
                    WorldClockItem(
                        clock = clock,
                        referenceTime = currentTime,
                        is24Hour = is24Hour,
                        onDelete = { viewModel.deleteWorldClock(clock) }
                    )
                }
            }
        }
    }

    if (showAddClockDialog) {
        LocationSearchDialog(
            viewModel = viewModel,
            onDismiss = {
                @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
                showAddClockDialog = false
            }
        )
    }
}


@Composable
fun WorldClockItem(
    clock: WorldClock,
    referenceTime: Long,
    is24Hour: Boolean,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(clock.cityName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = getZoneOffsetFormatted(clock.timezoneId) + " | " + clock.timezoneId,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = getZoneTimeFormatted(referenceTime, clock.timezoneId, is24Hour),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove World Clock",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}


@Composable
fun LocationSearchDialog(
    viewModel: ClockViewModel,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredCities = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            viewModel.availableCities
        } else {
            viewModel.availableCities.filter {
                it.cityName.contains(searchQuery, ignoreCase = true) ||
                it.country.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Location", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search location...", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = { Icon(Icons.Default.Search, "Search Icon") },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredCities) { city ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.addWorldClock(city.cityName, city.timezoneId)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(city.cityName, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text(city.country, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = getZoneOffsetFormatted(city.timezoneId),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    }
                }
            }
        }
    }
}


// Helper timezone formatting utilities
fun getZoneTimeFormatted(timestamp: Long, zoneId: String, is24Hour: Boolean = false): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone(zoneId))
    cal.timeInMillis = timestamp
    val minute = cal.get(Calendar.MINUTE)
    val second = cal.get(Calendar.SECOND)
    if (is24Hour) {
        val hour24 = cal.get(Calendar.HOUR_OF_DAY)
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hour24, minute, second)
    }
    val displayedHour = cal.get(Calendar.HOUR)
    val hourToShow = if (displayedHour == 0) 12 else displayedHour
    val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
    return String.format(Locale.ROOT, "%02d:%02d:%02d %s", hourToShow, minute, second, amPm)
}


fun getZoneOffsetFormatted(zoneId: String): String {
    val tz = TimeZone.getTimeZone(zoneId)
    val offsetMs = tz.getOffset(System.currentTimeMillis())
    val offsetHrs = offsetMs / (3600 * 1000)
    val prefix = if (offsetHrs >= 0) "+" else ""
    return "GMT$prefix$offsetHrs"
}


// ==========================================
// 2. ALARMS SCREEN
// ==========================================


