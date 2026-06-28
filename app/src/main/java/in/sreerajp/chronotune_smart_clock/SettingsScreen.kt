package `in`.sreerajp.chronotune_smart_clock

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import `in`.sreerajp.chronotune_smart_clock.widget.AnalogClockWidgetProvider
import `in`.sreerajp.chronotune_smart_clock.widget.DigitalClockWidgetProvider
import `in`.sreerajp.chronotune_smart_clock.widget.WidgetPrefs
import android.app.AlarmManager
import java.io.InputStream
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.automirrored.filled.ArrowBack

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
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    onReset: () -> Unit
) {
    val isAtDefault = kotlin.math.abs(value - WidgetPrefs.DEFAULT_ALPHA) < 0.001f
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


