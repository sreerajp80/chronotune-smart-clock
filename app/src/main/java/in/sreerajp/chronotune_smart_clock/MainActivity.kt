package `in`.sreerajp.chronotune_smart_clock

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.core.net.toUri
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.sreerajp.chronotune_smart_clock.audio.AudioEngine
import `in`.sreerajp.chronotune_smart_clock.data.*
import `in`.sreerajp.chronotune_smart_clock.data.repository.ClockRepository
import `in`.sreerajp.chronotune_smart_clock.ui.ActiveAlarmState
import `in`.sreerajp.chronotune_smart_clock.ui.ClockViewModel
import `in`.sreerajp.chronotune_smart_clock.ui.theme.Button3D
import `in`.sreerajp.chronotune_smart_clock.ui.theme.MyApplicationTheme
import `in`.sreerajp.chronotune_smart_clock.widget.AnalogClockWidgetProvider
import `in`.sreerajp.chronotune_smart_clock.widget.DigitalClockWidgetProvider
import `in`.sreerajp.chronotune_smart_clock.widget.WidgetPrefs
import android.app.AlarmManager
import androidx.activity.viewModels
import java.io.InputStream
import android.net.Uri
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

// Config and URI helpers
data class AppConfig(val architect: String, val author: String, val version: String)

private fun loadConfig(context: Context): AppConfig {
    return try {
        val inputStream: InputStream = context.assets.open("app_config.json")
        val size = inputStream.available()
        val buffer = ByteArray(size)
        inputStream.read(buffer)
        inputStream.close()
        val jsonStr = String(buffer, Charsets.UTF_8)
        val jsonObject = org.json.JSONObject(jsonStr)
        AppConfig(
            architect = jsonObject.optString("architect", "Sreeraj P"),
            author = jsonObject.optString("author", "Gemini 3.5 Flash"),
            version = jsonObject.optString("version", "1.5.0")
        )
    } catch (_: Exception) {
        AppConfig("Sreeraj P", "Gemini 3.5 Flash", "1.5.0")
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String {
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

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
    private val repository by lazy {
        ClockRepository(database.alarmDao(), database.worldClockDao(), database.musicScheduleDao())
    }
    private val viewModel: ClockViewModel by viewModels {
        ClockViewModel.Factory(applicationContext, repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        AppPrefs.init(applicationContext)

        setContent {
            val systemTheme = isSystemInDarkTheme()
            // Setup dynamic theme state with user toggle override
            @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
            var darkThemeOverride by remember { mutableStateOf<Boolean?>(null) }
            val isDark = darkThemeOverride ?: systemTheme

            MyApplicationTheme(darkTheme = isDark) {
                // Request critical notification permissions on Android 13+
                val context = LocalContext.current
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (!isGranted) {
                        Toast.makeText(context, "Notifications are required to sound background alarms", Toast.LENGTH_SHORT).show()
                    }
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    // Android 14+ no longer auto-grants USE_FULL_SCREEN_INTENT to arbitrary
                    // apps. Without it the alarm screen is downgraded to a heads-up.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        val nm = context.getSystemService(NotificationManager::class.java)
                        if (nm?.canUseFullScreenIntent() == false) {
                            Toast.makeText(
                                context,
                                "Enable 'Allow full-screen notifications' so alarms can take over the screen",
                                Toast.LENGTH_LONG
                            ).show()
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                                    .setData("package:${context.packageName}".toUri())
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (_: Exception) { /* settings screen unavailable */ }
                        }
                    }
                    // "Display over other apps" — required on Android 14+ for the alarm
                    // activity to actually appear when launched from the foreground
                    // service while the screen is unlocked. Without it the OS silently
                    // drops the startActivity() call and the user only sees the heads-up.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        !Settings.canDrawOverlays(context)
                    ) {
                        Toast.makeText(
                            context,
                            "Enable 'Display over other apps' so alarms can take over the screen",
                            Toast.LENGTH_LONG
                        ).show()
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                .setData("package:${context.packageName}".toUri())
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (_: Exception) { /* settings screen unavailable */ }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ClockAppScreen(
                        viewModel = viewModel,
                        isDark = isDark,
                        onToggleTheme = {
                            @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
                            darkThemeOverride = !isDark
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ClockAppScreen(
    viewModel: ClockViewModel,
    isDark: Boolean,
    onToggleTheme: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    var showSettings by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                // Hide navigation bar when viewing the Settings overlay
                AnimatedVisibility(
                    visible = !showSettings,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Default.Language, contentDescription = "Clock Tab") },
                            label = { Text("Clock", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Default.Alarm, contentDescription = "Alarms Tab") },
                            label = { Text("Alarms", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = { Icon(Icons.Default.Timer, contentDescription = "Stopwatch Tab") },
                            label = { Text("Stopwatch", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            icon = { Icon(Icons.Default.HourglassEmpty, contentDescription = "Timer Tab") },
                            label = { Text("Timer", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == 4,
                            onClick = { selectedTab = 4 },
                            icon = { Icon(Icons.Default.MusicNote, contentDescription = "Scheduler Tab") },
                            label = { Text("Schedules", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Animated modern transitions between screens
                AnimatedContent(
                    targetState = showSettings,
                    transitionSpec = {
                        if (targetState) {
                            (slideInHorizontally { width -> width / 2 } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width / 2 } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { width -> -width / 2 } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width / 2 } + fadeOut()
                            )
                        }
                    },
                    label = "Settings transition"
                ) { isSettingsVisible ->
                    if (isSettingsVisible) {
                        SettingsScreen(
                            isDark = isDark,
                            onToggleTheme = onToggleTheme,
                            onBack = { showSettings = false }
                        )
                    } else {
                        // Main screens with their respective Settings button actions
                        when (selectedTab) {
                            0 -> WorldClockScreen(viewModel, isDark, onOpenSettings = { showSettings = true })
                            1 -> AlarmsScreen(viewModel, onOpenSettings = { showSettings = true })
                            2 -> StopwatchScreen(viewModel, onOpenSettings = { showSettings = true })
                            3 -> TimerScreen(viewModel, onOpenSettings = { showSettings = true })
                            4 -> MusicSchedulerScreen(viewModel, onOpenSettings = { showSettings = true })
                        }
                    }
                }
            }
        }
        // Ringing UI now lives in the standalone AlarmActivity, launched by the alarm
        // notification's full-screen intent — keeps the alarm out of the main app surface.
    }
}

// ==========================================
// 1. CLOCK & WORLD TIME SCREEN
// ==========================================

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

@Composable
fun AlarmsScreen(
    viewModel: ClockViewModel,
    onOpenSettings: () -> Unit
) {
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val is24Hour by AppPrefs.is24Hour.collectAsStateWithLifecycle()
    @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<Alarm?>(null) }

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
                    text = "Alarms",
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
            Spacer(modifier = Modifier.height(16.dp))

            if (alarms.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No alarms configured.\nClick the '+' button below to create one.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(alarms) { alarm ->
                        AlarmCard(
                            alarm = alarm,
                            is24Hour = is24Hour,
                            onToggle = { viewModel.toggleAlarm(alarm) },
                            onDelete = { viewModel.deleteAlarm(alarm) },
                            onEdit = { editingAlarm = alarm }
                        )
                    }
                }
            }
        }

        // Add Alarm floating action button (FAB)
        Button3D(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(56.dp)
                .testTag("add_alarm_fab"),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            elevation = 12.dp,
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Alarm Action")
        }
    }

    if (showAddDialog) {
        AlarmEditDialog(
            existing = null,
            is24Hour = is24Hour,
            onDismiss = {
                @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
                showAddDialog = false
            },
            onSave = { hr, min, lbl, days, tone, uri, vol, vib ->
                viewModel.addAlarm(hr, min, lbl, days, tone, uri, vol, vib)
                @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
                showAddDialog = false
            }
        )
    }

    editingAlarm?.let { current ->
        AlarmEditDialog(
            existing = current,
            is24Hour = is24Hour,
            onDismiss = { editingAlarm = null },
            onSave = { hr, min, lbl, days, tone, uri, vol, vib ->
                val daysString = days.sorted().joinToString(",")
                viewModel.updateAlarm(
                    current.copy(
                        hour = hr,
                        minute = min,
                        label = lbl,
                        daysOfWeek = daysString,
                        customToneName = tone,
                        customToneUri = uri,
                        volume = vol,
                        isVibrate = vib
                    )
                )
                editingAlarm = null
            }
        )
    }
}

@Composable
fun AlarmCard(
    alarm: Alarm,
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
            containerColor = if (alarm.isEnabled) MaterialTheme.colorScheme.surfaceContainerHigh
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
                        text = alarm.getFormattedTime(is24Hour),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    if (alarm.label.isNotBlank()) {
                        Text(
                            text = alarm.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = alarm.isEnabled,
                        onCheckedChange = { onToggle() },
                        modifier = Modifier.testTag("alarm_switch_${alarm.id}")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete alarm",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Alarm attributes row (Selected Tone & repetition days)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Ringtone icon",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = alarm.customToneName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                // Repetition days representation
                val daysList = listOf("M", "T", "W", "T", "F", "S", "S")
                val activeDays = alarm.getRepeatDaysList()
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    daysList.forEachIndexed { index, day ->
                        val dayNum = index + 1
                        val active = activeDays.contains(dayNum)
                        Box(
                            modifier = Modifier
                                .size(22.dp)
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
                                fontSize = 10.sp,
                                lineHeight = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

// Filled rounded card used for hour/minute entry in the alarm dialog
@Composable
private fun TimeDigitBox(
    value: String,
    onValueChange: (String) -> Unit
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .size(width = 76.dp, height = 68.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp)
            ),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(
            textAlign = TextAlign.Center,
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold,
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

// Vertical pill button used for AM/PM selection
@Composable
private fun AmPmPill(
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

// Outlined pill used for day and tone selection chips in the alarm dialog.
// Two size variants: "sm" (compact day chips) and "md" (tone chips).
@Composable
private fun OutlinedPillChip(
    label: String,
    selected: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    size: String = "md"
) {
    val primary = MaterialTheme.colorScheme.primary
    val isSm = size == "sm"
    val hPad = if (isSm) 11.dp else 14.dp
    val vPad = if (isSm) 6.dp else 8.dp
    val fontSize = if (isSm) 12.sp else 12.sp
    val borderW = if (isSm) 1.2.dp else 1.3.dp

    // Day chips (sm) use a soft primary tint; tone chips (md) use primaryContainer.
    val selectedBg = if (isSm) primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.primaryContainer
    val selectedBorder = if (isSm) primary else MaterialTheme.colorScheme.primaryContainer
    val selectedText = if (isSm) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = Modifier
            .heightIn(min = if (isSm) 28.dp else 32.dp)
            .clip(shape)
            .background(
                color = if (selected) selectedBg else Color.Transparent,
                shape = shape
            )
            .border(
                width = borderW,
                color = if (selected) selectedBorder else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = hPad, vertical = vPad),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = fontSize,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) selectedText else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Dialog to Configure or edit alarms
@Composable
fun AlarmEditDialog(
    existing: Alarm? = null,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onSave: (hour: Int, minute: Int, label: String, repeatDays: List<Int>, tone: String, uri: String, volume: Float, isVibrate: Boolean) -> Unit
) {
    val initialHour24 = existing?.hour ?: 7
    val initialDisplayHour = if (is24Hour) {
        initialHour24
    } else when {
        initialHour24 == 0 -> 12
        initialHour24 > 12 -> initialHour24 - 12
        else -> initialHour24
    }
    var hourInput by remember { mutableStateOf(String.format(Locale.ROOT, "%02d", initialDisplayHour)) }
    var minuteInput by remember { mutableStateOf(String.format(Locale.ROOT, "%02d", existing?.minute ?: 30)) }
    var isPm by remember { mutableStateOf((existing?.hour ?: 7) >= 12) }
    var labelText by remember { mutableStateOf(existing?.label ?: "") }

    val selectedDays = remember {
        mutableStateListOf<Int>().apply {
            existing?.getRepeatDaysList()?.let { addAll(it) }
        }
    }
    var currentTone by remember { mutableStateOf(existing?.customToneName ?: "Morning Breeze") }
    var volumeScale by remember { mutableFloatStateOf(existing?.volume ?: 0.8f) }
    var vibrate by remember { mutableStateOf(existing?.isVibrate ?: true) }

    val tonesList = listOf("Morning Breeze", "Cosmic Shimmer", "Ocean Zen", "Digital Alarm", "Retro Chiptune", "Deep Lofi Lounge")

    val previewContext = LocalContext.current
    val previewEngine = remember { AudioEngine(previewContext.applicationContext) }
    var isPreviewing by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            previewEngine.stop()
        }
    }

    Dialog(onDismissRequest = {
        previewEngine.stop()
        onDismiss()
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp)
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
                Text(
                    text = if (existing == null) "Configure Alarm" else "Edit Alarm",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Input sanitizers respecting the active hour format
                val hourMax = if (is24Hour) 23 else 12
                val sanitizeHour: (String) -> String = { raw ->
                    val digits = raw.filter { it.isDigit() }.take(2)
                    when {
                        digits.isEmpty() -> ""
                        // Allow leading "0" while typing; reject only when the full value exceeds the cap
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

                // Time picker row — large filled cards with AM/PM stack on the right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimeDigitBox(
                        value = hourInput,
                        onValueChange = { hourInput = sanitizeHour(it) }
                    )
                    Text(
                        text = ":",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                    TimeDigitBox(
                        value = minuteInput,
                        onValueChange = { minuteInput = sanitizeMinute(it) }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // AM/PM Switcher (12-hour mode only)
                    if (!is24Hour) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AmPmPill(label = "AM", selected = !isPm, onClick = { isPm = false })
                            AmPmPill(label = "PM", selected = isPm, onClick = { isPm = true })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Label Outlines
                OutlinedTextField(
                    value = labelText,
                    onValueChange = { labelText = it },
                    placeholder = { Text("Alarm Label (e.g. Work)", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Repeat Days Selection
                Text(
                    "Repeat on Days",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.1.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))
                val dayChipsLabel = listOf("Mon" to 1, "Tue" to 2, "Wed" to 3, "Thu" to 4, "Fri" to 5, "Sat" to 6, "Sun" to 7)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    dayChipsLabel.forEach { dayPair ->
                        val daySelected = selectedDays.contains(dayPair.second)
                        OutlinedPillChip(
                            label = dayPair.first,
                            selected = daySelected,
                            shape = CircleShape,
                            size = "sm",
                            onClick = {
                                if (daySelected) selectedDays.remove(dayPair.second)
                                else selectedDays.add(dayPair.second)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Custom Tone Picker
                val context = LocalContext.current
                var customToneUri by remember { mutableStateOf(existing?.customToneUri ?: "") }

                // Custom Tones Selector Drops
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Alarm Audio Tone",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.1.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = {
                            if (isPreviewing) {
                                previewEngine.stop()
                                isPreviewing = false
                            } else {
                                previewEngine.playAudio(
                                    toneName = currentTone,
                                    uriString = if (customToneUri.isBlank()) null else customToneUri,
                                    volume = volumeScale,
                                    durationMs = 10_000L
                                )
                                isPreviewing = true
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPreviewing) "Stop preview" else "Preview tone",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tonesList.forEach { tone ->
                        OutlinedPillChip(
                            label = tone,
                            selected = currentTone == tone,
                            shape = RoundedCornerShape(10.dp),
                            onClick = {
                                currentTone = tone
                                customToneUri = ""
                                if (isPreviewing) {
                                    previewEngine.playAudio(
                                        toneName = tone,
                                        uriString = null,
                                        volume = volumeScale,
                                        durationMs = 10_000L
                                    )
                                }
                            }
                        )
                    }
                }

                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) {
                        try {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (_: Exception) {
                        }
                        customToneUri = uri.toString()
                        currentTone = getFileNameFromUri(context, uri)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { filePickerLauncher.launch(arrayOf("audio/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = "Upload icon",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (customToneUri.isBlank()) "Choose Custom File..." else "Change Custom File",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                if (customToneUri.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Picked: $currentTone",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        IconButton(onClick = { customToneUri = ""; currentTone = "Morning Breeze" }) {
                            Icon(Icons.Default.Clear, "Clear custom tone", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sound Volume Indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volume Icon",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Sound Volume",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Slider(
                    value = volumeScale,
                    onValueChange = { volumeScale = it },
                    valueRange = 0.1f..1.0f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.onSurface,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    )
                )

                // Vibrate checklist
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Vibration alerts",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(checked = vibrate, onCheckedChange = { vibrate = it })
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Dialog Buttons Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            "Cancel",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button3D(
                        onClick = {
                            var rawHour = hourInput.toIntOrNull() ?: 7
                            val rawMin = (minuteInput.toIntOrNull() ?: 30).coerceIn(0, 59)

                            if (is24Hour) {
                                rawHour = rawHour.coerceIn(0, 23)
                            } else {
                                // Convert standard 12H input back to raw 24H database hours representation
                                if (isPm) {
                                    if (rawHour < 12) rawHour += 12
                                } else {
                                    if (rawHour == 12) rawHour = 0
                                }
                                rawHour = rawHour.coerceIn(0, 23)
                            }
                            onSave(rawHour, rawMin, labelText, selectedDays.toList(), currentTone, customToneUri, volumeScale, vibrate)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        elevation = 8.dp,
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            "Save Alarm",
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
// 3. STOPWATCH SCREEN
// ==========================================

@Composable
fun StopwatchScreen(
    viewModel: ClockViewModel,
    onOpenSettings: () -> Unit
) {
    val stopwatchTime by viewModel.stopwatchTime.collectAsStateWithLifecycle()
    val stopwatchState by viewModel.stopwatchState.collectAsStateWithLifecycle()
    val laps by viewModel.laps.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Stopwatch",
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
        
        Spacer(modifier = Modifier.height(40.dp))

        // Large high-precision radial milliseconds timer
        Box(
            modifier = Modifier
                .size(240.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f), CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // High-precision clock values conversion
            val min = (stopwatchTime / 60000) % 60
            val sec = (stopwatchTime / 1000) % 60
            val milli = (stopwatchTime / 10) % 100
            val timeString = String.format(Locale.ROOT, "%02d:%02d.%02d", min, sec, milli)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = timeString,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "MIN:SEC.MS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Dynamic Interactive Controllers Box
        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Action / Reset button
            OutlinedButton(
                onClick = {
                    if (stopwatchState == ClockViewModel.StopwatchState.RUNNING) {
                        viewModel.recordLap()
                    } else {
                        viewModel.resetStopwatch()
                    }
                },
                enabled = stopwatchState != ClockViewModel.StopwatchState.IDLE,
                shape = CircleShape,
                modifier = Modifier.size(76.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = if (stopwatchState == ClockViewModel.StopwatchState.RUNNING) "Lap" else "Reset",
                    fontWeight = FontWeight.Bold
                )
            }

            // Center Primary Toggle button
            val isRunning = stopwatchState == ClockViewModel.StopwatchState.RUNNING
            val swColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            Button3D(
                onClick = {
                    if (isRunning) viewModel.pauseStopwatch() else viewModel.startStopwatch()
                },
                shape = CircleShape,
                color = swColor,
                contentColor = Color.White,
                elevation = 14.dp,
                modifier = Modifier
                    .size(92.dp)
                    .testTag("stopwatch_toggle_fab"),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Stopwatch Play Trigger",
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Split Laps Scroll List Section
        if (laps.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
            ) {
                LazyColumn(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(laps) { lap ->
                        val lMin = (lap.splitTimeMs / 60000) % 60
                        val lSec = (lap.splitTimeMs / 1000) % 60
                        val lMil = (lap.splitTimeMs / 10) % 100
                        val elapsedLapStr = String.format(Locale.ROOT, "%02d:%02d.%02d", lMin, lSec, lMil)

                        val dMin = (lap.lapTimeMs / 60000) % 60
                        val dSec = (lap.lapTimeMs / 1000) % 60
                        val dMil = (lap.lapTimeMs / 10) % 100
                        val deltaLapStr = String.format(Locale.ROOT, "+%02d:%02d.%02d", dMin, dSec, dMil)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Lap " + lap.number,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = elapsedLapStr,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = deltaLapStr,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    }
                }
            }
        }
    }
}


// ==========================================
// 4. TIMER SCREEN
// ==========================================

@Composable
fun TimerScreen(
    viewModel: ClockViewModel,
    onOpenSettings: () -> Unit
) {
    val duration by viewModel.timerDuration.collectAsStateWithLifecycle()
    val remaining by viewModel.timerRemaining.collectAsStateWithLifecycle()
    val state by viewModel.timerState.collectAsStateWithLifecycle()

    var pickerHours by remember { mutableIntStateOf(0) }
    var pickerMinutes by remember { mutableIntStateOf(10) }
    var pickerSeconds by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
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

        Spacer(modifier = Modifier.height(40.dp))

        // MAIN RADIAL REMAINING-TIME DIAL
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            val progressRatio = if (duration > 0L) remaining.toFloat() / duration.toFloat() else 1.0f
            val outlineColor = MaterialTheme.colorScheme.primary
            val backgroundOutlineColor = MaterialTheme.colorScheme.surfaceVariant

            // Drawing circular countdown timing arpeggio
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokePx = 10.dp.toPx()
                drawCircle(
                    color = backgroundOutlineColor,
                    style = Stroke(width = strokePx)
                )
                drawArc(
                    color = outlineColor,
                    startAngle = -90f,
                    sweepAngle = progressRatio * 360f,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }

            // Numeric Timer Indicators or Inputs
            if (state == ClockViewModel.TimerState.IDLE) {
                // Interactive Scroll Selectors
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
            } else {
                // Large Static Countdown Remaining strings
                val h = (remaining / 3600000)
                val m = (remaining / 60000) % 60
                val s = (remaining / 1000) % 60
                val timerString = String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s)

                Text(
                    text = timerString,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Timer actions bar
        Row(
            modifier = Modifier.fillMaxWidth(0.8f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel/Reset Button
            OutlinedButton(
                onClick = { viewModel.resetTimer() },
                enabled = state != ClockViewModel.TimerState.IDLE,
                shape = CircleShape,
                modifier = Modifier.size(80.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Cancel", fontWeight = FontWeight.Medium)
            }

            // Play / Pause Button
            val isRunning = state == ClockViewModel.TimerState.RUNNING
            val tmColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            Button3D(
                onClick = {
                    if (state == ClockViewModel.TimerState.IDLE) {
                        viewModel.setTimer(pickerHours, pickerMinutes, pickerSeconds)
                        viewModel.startTimer()
                    } else if (isRunning) {
                        viewModel.pauseTimer()
                    } else {
                        viewModel.startTimer()
                    }
                },
                shape = CircleShape,
                color = tmColor,
                contentColor = Color.White,
                elevation = 14.dp,
                modifier = Modifier
                    .size(92.dp)
                    .testTag("timer_play_action"),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Trigger Timer Activity",
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
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
    androidx.compose.foundation.text.BasicTextField(
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

@Composable
fun AlarmRingingOverlay(
    alarm: ActiveAlarmState.ActiveAlarm,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse animation")
    val pulseRatio by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse indicator"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Center ringing logo animation
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(180.dp),
                contentAlignment = Alignment.Center
            ) {
                // Throbbing background arpeggiator circles
                Box(
                    modifier = Modifier
                        .size(110.dp * pulseRatio)
                        .background(
                            color = if (alarm.type == "ALARM") Color.Red.copy(alpha = 0.15f) 
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = CircleShape
                        )
                )

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            color = if (alarm.type == "ALARM") Color.Red else MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (alarm.type == "ALARM") Icons.Default.NotificationsActive
                                      else Icons.Default.MusicNote,
                        contentDescription = "Ringing alarm icon",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Text headings descriptions
            Text(
                text = if (alarm.type == "ALARM") "ALARM RINGING" else "MUSIC PLAYING",
                fontSize = 14.sp,
                color = if (alarm.type == "ALARM") Color.Red else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 3.sp
            )

            Text(
                text = alarm.label,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Track: " + alarm.tone,
                fontSize = 14.sp,
                color = Color.LightGray,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Action dismiss/snooze configurations
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button3D(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(60.dp)
                    .testTag("dismiss_ring_overlay_button"),
                color = if (alarm.type == "ALARM") Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(30.dp),
                elevation = 14.dp
            ) {
                Text(
                    text = "DISMISS",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }

            if (alarm.type == "ALARM") {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(52.dp)
                        .testTag("snooze_ring_overlay_button"),
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text(
                        text = "SNOOZE (5 MIN)",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    
    // Dynamic permissions check states
    var hasNotifications by remember { mutableStateOf(false) }
    var hasExactAlarm by remember { mutableStateOf(false) }
    
    // Read dynamic config details
    val appConfig = remember { loadConfig(context) }
    
    // Launcher for critical permission notifications trigger
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotifications = isGranted
    }
    
    // Re-check permission states dynamically
    LaunchedEffect(Unit) {
        hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        
        hasExactAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Info", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // High-end Material 3 Tabs switcher
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("About", fontWeight = FontWeight.Medium, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Info, contentDescription = "About tab", modifier = Modifier.size(20.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Appearance", fontWeight = FontWeight.Medium, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Palette, contentDescription = "Appearance tab", modifier = Modifier.size(20.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Permissions", fontWeight = FontWeight.Medium, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Shield, contentDescription = "Permissions tab", modifier = Modifier.size(20.dp)) }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> {
                    // Page 1: About Page
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // High-contrast modern futuristic emblem
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f), CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timeline,
                                contentDescription = "App graphic icon badge",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(46.dp)
                            )
                        }
                        
                        Text(
                            text = "Chronos Clock Studio",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "A modern state-of-the-art Material 3 clock suite delivering precise alarm scheduling and automated background music playback.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 24.dp)
                        )
                        
                        // Architect configuration display card
                        InfoCard(
                            label = "Architect",
                            value = appConfig.architect,
                            icon = Icons.Default.Architecture,
                            badgeColor = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Author configuration display card
                        InfoCard(
                            label = "Author",
                            value = appConfig.author,
                            icon = Icons.Default.SmartToy,
                            badgeColor = MaterialTheme.colorScheme.secondary
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Dynamic version number display card
                        InfoCard(
                            label = "Version Level",
                            value = appConfig.version,
                            icon = Icons.Default.CheckCircle,
                            badgeColor = MaterialTheme.colorScheme.tertiary
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = "Project framework designed under bespoke modern patterns.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    }
                    1 -> {
                        // Page 2: Appearance — V1 refined chunky cards
                        val is24Hour by AppPrefs.is24Hour.collectAsStateWithLifecycle()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 18.dp, vertical = 4.dp)
                        ) {
                            ChunkyAppearanceToggle(
                                icon = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                                title = "Dark Mode",
                                subtitle = if (isDark) "Dark theme is active" else "Light theme is active",
                                checked = isDark,
                                onCheckedChange = { onToggleTheme() }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            ChunkyAppearanceToggle(
                                icon = Icons.Default.AccessTime,
                                title = "24-hour time",
                                subtitle = if (is24Hour) "Using 24-hour format (e.g. 18:30)"
                                           else "Using 12-hour format (e.g. 06:30 PM)",
                                checked = is24Hour,
                                onCheckedChange = { AppPrefs.setIs24Hour(context, it) }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Theme and time format apply to every screen — Clock, Alarms, Stopwatch, Timer, and Schedules.",
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            WidgetAppearanceCard(context = context)

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Slide all the way left for a fully transparent backdrop. Text stays readable with a built-in halo.",
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                    2 -> {
                    // Page 3: System Permissions lists
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Security Configuration Logs",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Listed below represent the dynamic security profile with explicit and implicit access levels utilized in this application.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
                        )
                        
                        // SECTION header: EXPLICIT PERMISSIONS
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "EXPLICIT PERMISSIONS (REQUIRES USER ACTION/GRANT)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        // Permission 1: Real-time dynamic checks notification (Explicit)
                        PermissionItem(
                            title = "Post Notifications Approval",
                            description = "Utilized to sound and present custom alerts overlay and heads-up notifications.",
                            type = "Runtime / Explicit Permission (Requires approval)",
                            isExplicit = true,
                            status = if (hasNotifications) "Granted (Active)" else "Tap to request",
                            isOk = hasNotifications,
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    Toast.makeText(context, "Notifications are automatically approved on your device build version.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Permission 2: Special exact alarms request launch context (Explicit)
                        PermissionItem(
                            title = "Exact Alarm Timing Approval",
                            description = "Required to schedule exact time wake alarms on modern Android editions. Tap to open system page.",
                            type = "Special Context System Permission",
                            isExplicit = true,
                            status = if (hasExactAlarm) "Approved (Active)" else "Configure Settings",
                            isOk = hasExactAlarm,
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    try {
                                        val intent = Intent(
                                            android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            "package:${context.packageName}".toUri()
                                        )
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Exact alarm intent is unavailable on your target device settings launch context.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Exact alarms are implicit and pre-approved on your system version.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // SECTION header: IMPLICIT PERMISSIONS
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.secondary, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "IMPLICIT PERMISSIONS (GRANTED AUTOMATICALLY)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        
                        // Permission 3: Implicit exact alarm background scheduler usage (Implicit)
                        PermissionItem(
                            title = "Background Alarm Tracker",
                            description = "Required to play scheduled music files on schedule without timing drifts.",
                            type = "Self-Approved Manifest (Implicit)",
                            isExplicit = false,
                            status = "Always Active",
                            isOk = true,
                            onClick = {}
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Permission 4: Device CPU woke state wake locking (Implicit)
                        PermissionItem(
                            title = "Wake Lock CPU Sleep Override",
                            description = "Allows synthesized audio drivers to keep sounding smoothly during phone standby.",
                            type = "Self-Approved Manifest (Implicit)",
                            isExplicit = false,
                            status = "Always Active",
                            isOk = true,
                            onClick = {}
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Permission 5: Vibrations sensory motors feedback (Implicit)
                        PermissionItem(
                            title = "Haptic Vibration Systems",
                            description = "Enables device vibration during alarm sound sequences.",
                            type = "Self-Approved Manifest (Implicit)",
                            isExplicit = false,
                            status = "Always Active",
                            isOk = true,
                            onClick = {}
                        )
                    }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(badgeColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = label, tint = badgeColor, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = value,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ChunkyIconTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ChunkyAppearanceToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChunkyIconTile(icon = icon, contentDescription = "$title icon")
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                    lineHeight = 16.sp
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
fun WidgetAppearanceCard(context: Context) {
    var digitalAlpha by remember {
        mutableFloatStateOf(WidgetPrefs.getDigitalBgAlpha(context))
    }
    var analogAlpha by remember {
        mutableFloatStateOf(WidgetPrefs.getAnalogBgAlpha(context))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChunkyIconTile(icon = Icons.Default.Widgets, contentDescription = "Widget appearance")
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Widget Background",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "Set opacity for each home-screen widget.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ChunkyWidgetOpacityRow(
                label = "Digital widget",
                value = digitalAlpha,
                defaultValue = WidgetPrefs.DEFAULT_ALPHA,
                onValueChange = { digitalAlpha = it },
                onValueChangeFinished = {
                    WidgetPrefs.setDigitalBgAlpha(context, digitalAlpha)
                    DigitalClockWidgetProvider.renderAll(context)
                },
                onReset = {
                    digitalAlpha = WidgetPrefs.DEFAULT_ALPHA
                    WidgetPrefs.setDigitalBgAlpha(context, WidgetPrefs.DEFAULT_ALPHA)
                    DigitalClockWidgetProvider.renderAll(context)
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            ChunkyWidgetOpacityRow(
                label = "Analog widget",
                value = analogAlpha,
                defaultValue = WidgetPrefs.DEFAULT_ALPHA,
                onValueChange = { analogAlpha = it },
                onValueChangeFinished = {
                    WidgetPrefs.setAnalogBgAlpha(context, analogAlpha)
                    AnalogClockWidgetProvider.renderAll(context)
                },
                onReset = {
                    analogAlpha = WidgetPrefs.DEFAULT_ALPHA
                    WidgetPrefs.setAnalogBgAlpha(context, WidgetPrefs.DEFAULT_ALPHA)
                    AnalogClockWidgetProvider.renderAll(context)
                }
            )
        }
    }
}

@Composable
private fun ChunkyWidgetOpacityRow(
    label: String,
    value: Float,
    defaultValue: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    onReset: () -> Unit
) {
    val isAtDefault = kotlin.math.abs(value - defaultValue) < 0.001f
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${(value * 100).toInt()}%",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onReset,
                enabled = !isAtDefault,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = "Reset $label opacity to default",
                    tint = if (isAtDefault) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    ChunkyOpacitySlider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished
    )
}

@Composable
private fun ChunkyOpacitySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val remainingTrack = Color(0xFF4A2516)
    val whiteDot = Color.White.copy(alpha = 0.40f)
    val accentDot = primary.copy(alpha = 0.55f)
    var trackWidthPx by remember { mutableFloatStateOf(0f) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (trackWidthPx > 0f) {
                        val v = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                        onValueChange(v)
                        onValueChangeFinished()
                    }
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { onValueChangeFinished() }
                ) { change, _ ->
                    if (trackWidthPx > 0f) {
                        val v = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                        onValueChange(v)
                    }
                }
            }
    ) {
        val trackH = 8.dp.toPx()
        val trackR = 6.dp.toPx()
        val gap = 4.dp.toPx()
        val thumbW = 2.dp.toPx()
        val thumbH = 16.dp.toPx()
        val dotR = 2.dp.toPx()
        val centerY = size.height / 2f

        val v = value.coerceIn(0f, 1f)
        val thumbX = size.width * v

        val leftEnd = (thumbX - thumbW / 2f - gap / 2f).coerceAtLeast(0f)
        if (leftEnd > 0f) {
            drawRoundRect(
                color = primary,
                topLeft = Offset(0f, centerY - trackH / 2f),
                size = Size(leftEnd, trackH),
                cornerRadius = CornerRadius(trackR, trackR)
            )
        }

        val rightStart = (thumbX + thumbW / 2f + gap / 2f).coerceAtMost(size.width)
        if (rightStart < size.width) {
            drawRoundRect(
                color = remainingTrack,
                topLeft = Offset(rightStart, centerY - trackH / 2f),
                size = Size(size.width - rightStart, trackH),
                cornerRadius = CornerRadius(trackR, trackR)
            )
        }

        // Decorative tonal dot inside the filled portion
        if (leftEnd > dotR * 4) {
            drawCircle(
                color = whiteDot,
                radius = dotR,
                center = Offset(leftEnd * 0.5f, centerY)
            )
        }
        // Decorative accent dot inside the remaining portion
        if (size.width - rightStart > dotR * 4) {
            drawCircle(
                color = accentDot,
                radius = dotR,
                center = Offset((rightStart + size.width) / 2f, centerY)
            )
        }

        // Thumb — vertical bar sitting on the split line
        drawRoundRect(
            color = primary,
            topLeft = Offset(thumbX - thumbW / 2f, centerY - thumbH / 2f),
            size = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(thumbW / 2f, thumbW / 2f)
        )
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    type: String,
    isExplicit: Boolean,
    status: String,
    isOk: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isOk || title.contains("Exact Alarm")) { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOk) MaterialTheme.colorScheme.surface 
                             else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isOk) MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isExplicit) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    ) {
                        Text(
                            text = if (isExplicit) "EXPLICIT PERMISSION" else "IMPLICIT PERMISSION",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            color = if (isExplicit) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isOk) Color(0xFF10B981).copy(alpha = 0.15f) 
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOk) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            Text(
                text = description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            Text(
                text = "Classification: $type",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

