package `in`.sreerajp.chronotune_smart_clock

import android.annotation.SuppressLint
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import `in`.sreerajp.chronotune_smart_clock.audio.AudioEngine
import `in`.sreerajp.chronotune_smart_clock.ui.VoiceLanguageSupport
import `in`.sreerajp.chronotune_smart_clock.ui.theme.AccentSwatches
import `in`.sreerajp.chronotune_smart_clock.ui.theme.AppFont
import `in`.sreerajp.chronotune_smart_clock.ui.theme.ColorPickerDialog
import `in`.sreerajp.chronotune_smart_clock.ui.theme.FONT_SCALE_LABELS
import `in`.sreerajp.chronotune_smart_clock.ui.theme.FONT_SCALE_STEPS
import `in`.sreerajp.chronotune_smart_clock.ui.theme.fontFamilyFor
import `in`.sreerajp.chronotune_smart_clock.ui.theme.fontScaleStepIndex
import `in`.sreerajp.chronotune_smart_clock.ui.theme.onColorFor
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
import java.util.Locale
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.vector.ImageVector
import `in`.sreerajp.chronotune_smart_clock.config.AppConfig
import `in`.sreerajp.chronotune_smart_clock.config.ConfigService


// The settings hub is a list of cards; each card opens one of these pages.
private enum class SettingsSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    ABOUT("About", "App details, backup and restore", Icons.Default.Info),
    APPEARANCE("Appearance", "Theme, accent colour, fonts and widgets", Icons.Default.Palette),
    PERMISSIONS("Permissions", "Notification and exact alarm access", Icons.Default.Shield),
    ALARM("Alarm", "Fade-in, snooze, auto-silence and tone", Icons.Default.Alarm),
    HOLIDAYS("Holidays & work days", "Days your alarms can skip or work through", Icons.Default.BeachAccess),
    HISTORY("Alarm history", "What each alarm did, and any that never rang", Icons.Default.History),
    MUSIC("Music", "Crossfade and loudness for playlists", Icons.AutoMirrored.Filled.QueueMusic)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: `in`.sreerajp.chronotune_smart_clock.ui.ClockViewModel,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
    // True when settings was opened specifically to show the alarm history — the "See why"
    // button on the missed-alarm banner. Lands on that page instead of the hub.
    startOnHistory: Boolean = false
) {
    val context = LocalContext.current
    // null means the hub (card list) is showing.
    var openSection by remember {
        mutableStateOf<SettingsSection?>(if (startOnHistory) SettingsSection.HISTORY else null)
    }

    // Dynamic permissions check states
    var hasNotifications by remember { mutableStateOf(false) }
    var hasExactAlarm by remember { mutableStateOf(false) }
    // Whether the app is exempt from battery optimisation. Without the exemption, OEM battery
    // managers are free to kill the app, and Android throws away every alarm it had pending —
    // the single most common reason an alarm never rings.
    var ignoresBatteryOptimisation by remember { mutableStateOf(true) }
    
    // Read dynamic config details
    val appConfig = remember { ConfigService.loadAndVerify(context) }
    
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

        ignoresBatteryOptimisation = try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (_: Exception) {
            true
        }
    }
    
    // Device back returns to the hub first, and only then leaves settings.
    BackHandler(enabled = openSection != null) { openSection = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        openSection?.title ?: "Settings & Info",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (openSection != null) openSection = null else onBack() }) {
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
            val section = openSection
            if (section == null) {
                SettingsHub(onOpen = { openSection = it })
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    when (section) {
                    SettingsSection.ABOUT -> {
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
                            text = appConfig.appName,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = appConfig.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 24.dp)
                        )
                        
                        // Dynamic version number display card
                        InfoCard(
                            label = "Version",
                            value = "${appConfig.version} (${appConfig.build})",
                            icon = Icons.Default.CheckCircle,
                            badgeColor = MaterialTheme.colorScheme.tertiary
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))

                        // Dynamic details cards from app_config.json
                        appConfig.details.entries.forEach { (key, value) ->
                            if (key.isNotBlank() && value.isNotBlank()) {
                                val isEmail = key.trim().equals("email", ignoreCase = true)
                                val icon = when (key.trim().lowercase()) {
                                    "architect" -> Icons.Default.Architecture
                                    "author" -> Icons.Default.SmartToy
                                    "email" -> Icons.Default.Email
                                    "license" -> Icons.Default.Description
                                    "ai used" -> Icons.Default.AutoAwesome
                                    "ide used" -> Icons.Default.Computer
                                    else -> Icons.Default.Info
                                }
                                val badgeColor = when (key.trim().lowercase()) {
                                    "architect" -> MaterialTheme.colorScheme.primary
                                    "author" -> MaterialTheme.colorScheme.secondary
                                    "license" -> MaterialTheme.colorScheme.outline
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                InfoCard(
                                    label = key,
                                    value = value,
                                    icon = icon,
                                    badgeColor = badgeColor,
                                    onClick = if (isEmail) {
                                        {
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_SENDTO, "mailto:$value".toUri()))
                                            } catch (_: Exception) {}
                                        }
                                    } else null
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))

                        BackupRestoreCard(context = context, viewModel = viewModel)

                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Project framework designed under bespoke modern patterns.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    }
                    SettingsSection.APPEARANCE -> {
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

                            AccentColorCard(context = context)

                            Spacer(modifier = Modifier.height(14.dp))

                            FontFamilyCard(context = context)

                            Spacer(modifier = Modifier.height(14.dp))

                            FontSizeCard(context = context)

                            Spacer(modifier = Modifier.height(14.dp))

                            WeekStartCard(context = context)

                            Spacer(modifier = Modifier.height(14.dp))

                            VoiceLanguageCard(context = context)

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
                    SettingsSection.PERMISSIONS -> {
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
                        
                        Spacer(modifier = Modifier.height(12.dp))

                        // Permission 3: battery optimisation exemption. This is the one that
                        // decides whether the app survives long enough to ring at all.
                        PermissionItem(
                            title = "Unrestricted Battery Usage",
                            description = "Stops the phone from closing the app in the background. When the app is closed this way, Android throws away every alarm it had waiting.",
                            type = "Special Context System Permission",
                            isExplicit = true,
                            status = if (ignoresBatteryOptimisation) "Approved (Active)" else "Configure Settings",
                            isOk = ignoresBatteryOptimisation,
                            onClick = {
                                try {
                                    @SuppressLint("BatteryLife")
                                    val intent = Intent(
                                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        "package:${context.packageName}".toUri()
                                    )
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    // Some builds hide the direct dialog; the list screen is
                                    // the next best thing.
                                    try {
                                        context.startActivity(
                                            Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        )
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Battery settings could not be opened on this device.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Auto-start managers cannot be read or opened reliably from code, so
                        // the best we can do is tell the user what to look for.
                        Text(
                            text = "On Xiaomi, Oppo, Vivo, Realme, OnePlus and Samsung phones, also allow Auto-start for this app and remove it from the battery saver or app cleaner list. Otherwise the phone can still close the app at night and the alarm will not ring.",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    SettingsSection.ALARM -> {
                        // Page 4: Alarm behavior settings
                        val fadeInEnabled by AppPrefs.fadeInEnabled.collectAsStateWithLifecycle()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 18.dp, vertical = 4.dp)
                        ) {
                            ChunkyAppearanceToggle(
                                icon = Icons.Default.GraphicEq,
                                title = "Gradual volume (fade-in)",
                                subtitle = if (fadeInEnabled) "Alarms ramp up over 20 seconds"
                                           else "Alarms start at full volume",
                                checked = fadeInEnabled,
                                onCheckedChange = { AppPrefs.setFadeInEnabled(context, it) }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "When enabled, every alarm fades in gently from quiet to its set volume over about 20 seconds for a softer wake. Music schedules are unaffected.",
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            DefaultSnoozeCard(context = context)

                            Spacer(modifier = Modifier.height(14.dp))

                            DefaultAutoSilenceCard(context = context)

                            Spacer(modifier = Modifier.height(14.dp))

                            DefaultMaxSnoozeCard(context = context)

                            Spacer(modifier = Modifier.height(14.dp))

                            DefaultSnoozeModeCard(context = context)

                            Spacer(modifier = Modifier.height(14.dp))

                            DefaultToneCard(context = context)

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Snooze length, snooze limit, snooze style, auto-silence and tone apply to alarms you create from now on. Existing alarms keep their own settings. Timers use the auto-silence value too.",
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                    SettingsSection.HOLIDAYS -> {
                        // Page 5: the shared holiday / work-day list. Its own back arrow is
                        // hidden because this page already sits under the settings top bar.
                        HolidaysScreen(viewModel = viewModel, onBack = { openSection = null })
                    }
                    SettingsSection.HISTORY -> {
                        // Page 6: the alarm event log. Its own back arrow is hidden because
                        // this page already sits under the settings top bar.
                        HistoryScreen(viewModel = viewModel, onBack = { openSection = null })
                    }
                    SettingsSection.MUSIC -> {
                        // Page 6: Music Scheduler — playlist crossfade settings
                        MusicSchedulerSettings(context = context)
                    }
                    }
                }
            }
        }
    }
}


// Hub: one tappable card per settings page.
@Composable
private fun SettingsHub(onOpen: (SettingsSection) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Choose a section to open its settings.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 14.dp)
        )

        SettingsSection.entries.forEach { section ->
            SettingsHubCard(section = section, onClick = { onOpen(section) })
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}


@Composable
private fun SettingsHubCard(section: SettingsSection, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            ChunkyIconTile(icon = section.icon, contentDescription = null)

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = section.subtitle,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}


// Music Scheduler settings: how built-in tones / files blend across a Music schedule's playlist.
@Composable
private fun MusicSchedulerSettings(context: Context) {
    val crossfadeEnabled by AppPrefs.crossfadeEnabled.collectAsStateWithLifecycle()
    val crossfadeMs by AppPrefs.crossfadeMs.collectAsStateWithLifecycle()
    val curve by AppPrefs.crossfadeCurve.collectAsStateWithLifecycle()
    val normalize by AppPrefs.loudnessNormalize.collectAsStateWithLifecycle()

    // Live slider position so dragging feels smooth; committed to prefs on drag-end.
    var sliderMs by remember(crossfadeMs) { mutableIntStateOf(crossfadeMs) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 4.dp)
    ) {
        ChunkyAppearanceToggle(
            icon = Icons.Default.Tune,
            title = "Crossfade",
            subtitle = if (crossfadeEnabled) "Items blend smoothly into each other"
                       else "Items switch with a hard cut",
            checked = crossfadeEnabled,
            onCheckedChange = { AppPrefs.setCrossfadeEnabled(context, it) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Crossfade duration + curve — dimmed and disabled while crossfade is off.
        Card(
            modifier = Modifier.fillMaxWidth().alpha(if (crossfadeEnabled) 1f else 0.45f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChunkyIconTile(icon = Icons.Default.GraphicEq, contentDescription = "Crossfade duration")
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Crossfade length",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp
                        )
                        Text(
                            text = "Overlap between consecutive items.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp),
                            lineHeight = 16.sp
                        )
                    }
                    Text(
                        text = String.format("%.1f s", sliderMs / 1000f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                ChunkyValueSlider(
                    fraction = sliderMs.toFloat() / AppPrefs.CROSSFADE_MAX_MS,
                    enabled = crossfadeEnabled,
                    onFraction = { f ->
                        // Snap to 0.5 s steps for a tidy value.
                        val ms = (f * AppPrefs.CROSSFADE_MAX_MS / 500).toInt() * 500
                        sliderMs = ms.coerceIn(AppPrefs.CROSSFADE_MIN_MS, AppPrefs.CROSSFADE_MAX_MS)
                    },
                    onFractionFinished = { AppPrefs.setCrossfadeMs(context, sliderMs) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Blend curve",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CurveChip(
                        label = "Equal-power",
                        selected = curve == AudioEngine.CrossfadeCurve.EQUAL_POWER,
                        enabled = crossfadeEnabled
                    ) { AppPrefs.setCrossfadeCurve(context, AudioEngine.CrossfadeCurve.EQUAL_POWER) }
                    CurveChip(
                        label = "Linear",
                        selected = curve == AudioEngine.CrossfadeCurve.LINEAR,
                        enabled = crossfadeEnabled
                    ) { AppPrefs.setCrossfadeCurve(context, AudioEngine.CrossfadeCurve.LINEAR) }
                }
                Text(
                    text = if (curve == AudioEngine.CrossfadeCurve.EQUAL_POWER)
                               "Keeps loudness steady through the blend."
                           else "Simple linear ramp; may dip slightly mid-blend.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        ChunkyAppearanceToggle(
            icon = Icons.Default.Equalizer,
            title = "Loudness normalization",
            subtitle = if (normalize) "Even out volume differences between items"
                       else "Play each item at its own level",
            checked = normalize,
            onCheckedChange = { AppPrefs.setLoudnessNormalize(context, it) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "These settings apply to Music schedules only — alarms are unaffected. Normalization matches levels using a quick scan of each file's start.",
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}


// Small selectable pill used for the crossfade curve choice.
@Composable
private fun CurveChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        modifier = Modifier.clickable(enabled = enabled) { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}


// ---- About tab: backup & restore of alarms, world clocks, music schedules and timer presets ----
@Composable
private fun BackupRestoreCard(
    context: Context,
    viewModel: `in`.sreerajp.chronotune_smart_clock.ui.ClockViewModel
) {
    val backupEvent by viewModel.backupEvent.collectAsStateWithLifecycle()
    // Holds the picked import file until the user confirms Merge vs Replace.
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // Show a toast for the last export/import outcome, then clear it.
    LaunchedEffect(backupEvent) {
        when (val e = backupEvent) {
            is `in`.sreerajp.chronotune_smart_clock.ui.ClockViewModel.BackupEvent.Exported ->
                Toast.makeText(context, "Backup saved (${e.bytes} bytes)", Toast.LENGTH_SHORT).show()
            is `in`.sreerajp.chronotune_smart_clock.ui.ClockViewModel.BackupEvent.Imported ->
                Toast.makeText(
                    context,
                    "Restored ${e.result.total} items (${e.result.alarms} alarms, " +
                        "${e.result.worldClocks} clocks, ${e.result.musicSchedules} schedules, " +
                        "${e.result.timerPresets} presets, ${e.result.specialDays} marked days)",
                    Toast.LENGTH_LONG
                ).show()
            is `in`.sreerajp.chronotune_smart_clock.ui.ClockViewModel.BackupEvent.Failed ->
                Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
            null -> {}
        }
        if (backupEvent != null) viewModel.clearBackupEvent()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: android.net.Uri? ->
        if (uri != null) viewModel.exportBackup(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) pendingImportUri = uri
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChunkyIconTile(icon = Icons.Default.Backup, contentDescription = "Backup and restore")
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Backup & restore",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "Save or load alarms, world clocks, music schedules and timer presets.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        val stamp = java.text.SimpleDateFormat("yyyyMMdd", Locale.US)
                            .format(java.util.Date())
                        exportLauncher.launch("chronotune-backup-$stamp.json")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Running timers and stopwatch state are not included.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }

    // Merge / Replace choice after a file is picked for import.
    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Restore backup") },
            text = {
                Text(
                    "Merge adds the backup's items to what you already have. " +
                        "Replace clears your current alarms, world clocks, music schedules and " +
                        "timer presets first, then loads the backup."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importBackup(uri, `in`.sreerajp.chronotune_smart_clock.data.BackupManager.ImportMode.MERGE)
                    pendingImportUri = null
                }) { Text("Merge") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.importBackup(uri, `in`.sreerajp.chronotune_smart_clock.data.BackupManager.ImportMode.REPLACE)
                        pendingImportUri = null
                    }) { Text("Replace") }
                    TextButton(onClick = { pendingImportUri = null }) { Text("Cancel") }
                }
            }
        )
    }
}


// ---- Alarm tab: default snooze length ----
@Composable
private fun DefaultSnoozeCard(context: Context) {
    val snooze by AppPrefs.defaultSnoozeMinutes.collectAsStateWithLifecycle()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChunkyIconTile(icon = Icons.Default.Snooze, contentDescription = "Default snooze")
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Default snooze length",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "Used when you snooze a new alarm.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                        lineHeight = 16.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppPrefs.SNOOZE_CHOICES.forEach { m ->
                    CurveChip(
                        label = "$m min",
                        selected = snooze == m,
                        enabled = true
                    ) { AppPrefs.setDefaultSnoozeMinutes(context, m) }
                }
            }
        }
    }
}


// ---- Alarm tab: default auto-silence length ----
@Composable
private fun DefaultAutoSilenceCard(context: Context) {
    val autoSilence by AppPrefs.defaultAutoSilenceMinutes.collectAsStateWithLifecycle()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChunkyIconTile(icon = Icons.Default.Timer, contentDescription = "Default auto-silence")
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Default auto-silence",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "How long a ring sounds before stopping itself.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                        lineHeight = 16.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppPrefs.AUTO_SILENCE_CHOICES.forEach { m ->
                    CurveChip(
                        label = if (m == 0) "Never" else "$m min",
                        selected = autoSilence == m,
                        enabled = true
                    ) { AppPrefs.setDefaultAutoSilenceMinutes(context, m) }
                }
            }
        }
    }
}


// ---- Alarm tab: default snooze limit ----
@Composable
private fun DefaultMaxSnoozeCard(context: Context) {
    val maxSnooze by AppPrefs.defaultMaxSnoozeCount.collectAsStateWithLifecycle()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChunkyIconTile(icon = Icons.Default.Snooze, contentDescription = "Default snooze limit")
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Default snooze limit",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "How many times an alarm may be snoozed before Snooze goes away.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                        lineHeight = 16.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0, 1, 2, 3, 5).forEach { n ->
                    CurveChip(
                        label = if (n == 0) "Unlimited" else "$n",
                        selected = maxSnooze == n,
                        enabled = true
                    ) { AppPrefs.setDefaultMaxSnoozeCount(context, n) }
                }
            }
        }
    }
}


// ---- Alarm tab: default snooze style (same gap each time, or shrinking) ----
@Composable
private fun DefaultSnoozeModeCard(context: Context) {
    val mode by AppPrefs.defaultSnoozeMode.collectAsStateWithLifecycle()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChunkyIconTile(icon = Icons.Default.Timelapse, contentDescription = "Default snooze style")
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Default snooze style",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "Getting shorter makes each snooze briefer than the one before.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                        lineHeight = 16.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    `in`.sreerajp.chronotune_smart_clock.data.Alarm.SNOOZE_MODE_FIXED to "Same each time",
                    `in`.sreerajp.chronotune_smart_clock.data.Alarm.SNOOZE_MODE_PROGRESSIVE to "Getting shorter"
                ).forEach { (key, label) ->
                    CurveChip(
                        label = label,
                        selected = mode == key,
                        enabled = true
                    ) { AppPrefs.setDefaultSnoozeMode(context, key) }
                }
            }
        }
    }
}


// ---- Alarm tab: default alarm tone (built-in melodies only) ----
@Composable
private fun DefaultToneCard(context: Context) {
    val tone by AppPrefs.defaultAlarmTone.collectAsStateWithLifecycle()
    val tones = listOf(
        "Morning Breeze" to "Soft ambient",
        "Cosmic Shimmer" to "Dreamy synth",
        "Ocean Zen" to "Ocean calm",
        "Digital Alarm" to "Alert buzzer",
        "Retro Chiptune" to "Upbeat scale",
        "Deep Lofi Lounge" to "Soothing arpeggio"
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChunkyIconTile(icon = Icons.Default.MusicNote, contentDescription = "Default tone")
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Default alarm tone",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "Preselected when you create a new alarm.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                        lineHeight = 16.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            tones.forEach { (name, sub) ->
                SelectableToneRow(
                    name = name,
                    sub = sub,
                    selected = tone == name,
                    onClick = { AppPrefs.setDefaultAlarmTone(context, name) }
                )
            }
        }
    }
}


@Composable
private fun SelectableToneRow(
    name: String,
    sub: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) primary.copy(alpha = 0.12f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = sub,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) primary else Color.Transparent, CircleShape)
                .border(
                    2.dp,
                    if (selected) primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}


// ---- Appearance tab: theme accent color ----
@Composable
private fun AccentColorCard(context: Context) {
    val accent by AppPrefs.accentColor.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }
    val effective = if (accent == AppPrefs.ACCENT_DEFAULT) MaterialTheme.colorScheme.primary else Color(accent)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChunkyIconTile(icon = Icons.Default.Palette, contentDescription = "Accent color")
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Accent color",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "Highlights buttons, toggles and selections.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                        lineHeight = 16.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(effective, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AccentSwatches.forEach { c ->
                    val isSel = accent != AppPrefs.ACCENT_DEFAULT && accent == c.toArgb()
                    SwatchDot(color = c, selected = isSel) {
                        AppPrefs.setAccentColor(context, c.toArgb())
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showPicker = true }) {
                    Icon(Icons.Default.Colorize, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Custom…")
                }
                if (accent != AppPrefs.ACCENT_DEFAULT) {
                    TextButton(onClick = { AppPrefs.setAccentColor(context, AppPrefs.ACCENT_DEFAULT) }) {
                        Text("Reset")
                    }
                }
            }
        }
    }

    if (showPicker) {
        ColorPickerDialog(
            initial = effective,
            onDismiss = { showPicker = false },
            onConfirm = {
                AppPrefs.setAccentColor(context, it.toArgb())
                showPicker = false
            }
        )
    }
}


@Composable
private fun SwatchDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(color, CircleShape)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
                tint = onColorFor(color),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}


// ---- Appearance tab: font family ----
@Composable
private fun FontFamilyCard(context: Context) {
    val selected by AppPrefs.appFont.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChunkyIconTile(icon = Icons.Default.FontDownload, contentDescription = "Font")
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Font",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "Typeface used across the whole app.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AppFont.entries.forEach { font ->
                FontRow(
                    font = font,
                    selected = font == selected,
                    onClick = { AppPrefs.setAppFont(context, font) }
                )
            }
        }
    }
}

@Composable
private fun FontRow(font: AppFont, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary
                 else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = border,
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Live preview: the font's own name rendered in that font.
        Text(
            text = font.displayName,
            fontSize = 17.sp,
            fontFamily = fontFamilyFor(font),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}


// ---- Appearance tab: font size ----
@Composable
private fun FontSizeCard(context: Context) {
    val scale by AppPrefs.fontScale.collectAsStateWithLifecycle()
    val lastIndex = FONT_SCALE_STEPS.lastIndex
    var stepIndex by remember(scale) { mutableIntStateOf(fontScaleStepIndex(scale)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChunkyIconTile(icon = Icons.Default.FormatSize, contentDescription = "Font size")
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Font size",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "Scales text everywhere in the app.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                        lineHeight = 16.sp
                    )
                }
                Text(
                    text = FONT_SCALE_LABELS[stepIndex],
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            ChunkyValueSlider(
                fraction = if (lastIndex == 0) 0f else stepIndex.toFloat() / lastIndex,
                enabled = true,
                onFraction = { f ->
                    stepIndex = Math.round(f * lastIndex).coerceIn(0, lastIndex)
                },
                onFractionFinished = {
                    AppPrefs.setFontScale(context, FONT_SCALE_STEPS[stepIndex])
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "The quick brown fox jumps over the lazy dog.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}


// ---- Appearance tab: week-start day ----
@Composable
private fun WeekStartCard(context: Context) {
    val start by AppPrefs.weekStartDay.collectAsStateWithLifecycle()
    val options = listOf(1 to "Monday", 7 to "Sunday", 6 to "Saturday")
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChunkyIconTile(icon = Icons.Default.DateRange, contentDescription = "Week start")
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Week starts on",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "Orders the day picker in Alarms and Schedules.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                        lineHeight = 16.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (num, label) ->
                    CurveChip(
                        label = label,
                        selected = start == num,
                        enabled = true
                    ) { AppPrefs.setWeekStartDay(context, num) }
                }
            }
        }
    }
}


/**
 * Picks the language the microphone listens in, and warns when Malayalam speech is not
 * actually installed on the phone.
 *
 * Malayalam speech-to-text needs a voice model that many phones ship without. If we said
 * nothing, the recogniser would quietly return English words and the user would blame the
 * app, so the missing model is called out here with a way to go and install it.
 */
@Composable
private fun VoiceLanguageCard(context: Context) {
    val selected by AppPrefs.voiceLanguage.collectAsStateWithLifecycle()
    var malayalamSupport by remember {
        mutableStateOf(VoiceLanguageSupport.Support.UNKNOWN)
    }
    var showMalayalamNotice by remember { mutableStateOf(false) }

    // Re-ask every time Settings is opened: the user may have just installed the model.
    LaunchedEffect(Unit) {
        VoiceLanguageSupport.refresh()
        VoiceLanguageSupport.check(context, "ml") { malayalamSupport = it }
    }

    val options = listOf(
        VoiceLanguage.AUTO to stringResource(R.string.voice_language_auto),
        VoiceLanguage.ENGLISH to stringResource(R.string.voice_language_english),
        VoiceLanguage.MALAYALAM to stringResource(R.string.voice_language_malayalam)
    )

    fun openVoiceSettings() {
        if (!VoiceLanguageSupport.openVoiceInputSettings(context)) {
            Toast.makeText(
                context, R.string.voice_ml_settings_unavailable, Toast.LENGTH_LONG
            ).show()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChunkyIconTile(icon = Icons.Default.Mic, contentDescription = "Voice commands")
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = stringResource(R.string.voice_language_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "The language the microphone listens in.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (language, label) ->
                    CurveChip(
                        label = label,
                        selected = selected == language,
                        enabled = true
                    ) {
                        AppPrefs.setVoiceLanguage(context, language)
                        // Tell the user once, the first time they choose Malayalam on a
                        // phone that cannot (or may not) understand it.
                        if (language == VoiceLanguage.MALAYALAM &&
                            malayalamSupport != VoiceLanguageSupport.Support.SUPPORTED &&
                            !AppPrefs.wasMalayalamVoiceNoticeShown(context)
                        ) {
                            showMalayalamNotice = true
                            AppPrefs.markMalayalamVoiceNoticeShown(context)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (malayalamSupport) {
                VoiceLanguageSupport.Support.NOT_SUPPORTED -> {
                    // Stated as fact: the recogniser answered and Malayalam was not listed.
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openVoiceSettings() }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.voice_ml_missing_title),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = stringResource(R.string.voice_ml_missing_body),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                text = stringResource(R.string.voice_ml_open_settings),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                else -> {
                    // Supported, or we could not find out — a plain reminder either way.
                    Text(
                        text = stringResource(R.string.voice_ml_hint),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showMalayalamNotice) {
        AlertDialog(
            onDismissRequest = { showMalayalamNotice = false },
            title = { Text(stringResource(R.string.voice_ml_missing_title)) },
            text = {
                Text(
                    stringResource(
                        if (malayalamSupport == VoiceLanguageSupport.Support.NOT_SUPPORTED)
                            R.string.voice_ml_missing_body
                        else R.string.voice_ml_maybe_missing_body
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    openVoiceSettings()
                    showMalayalamNotice = false
                }) { Text(stringResource(R.string.voice_ml_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showMalayalamNotice = false }) {
                    Text(stringResource(R.string.voice_ok))
                }
            }
        )
    }
}

@Composable
fun InfoCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Info,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
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
            Column(modifier = Modifier.weight(1f)) {
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


// Same chunky canvas track as the opacity slider, generalized to a 0..1 fraction with an enabled flag.
@Composable
private fun ChunkyValueSlider(
    fraction: Float,
    enabled: Boolean,
    onFraction: (Float) -> Unit,
    onFractionFinished: () -> Unit
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
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    if (trackWidthPx > 0f) {
                        onFraction((offset.x / trackWidthPx).coerceIn(0f, 1f))
                        onFractionFinished()
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = { onFractionFinished() }
                ) { change, _ ->
                    if (trackWidthPx > 0f) {
                        onFraction((change.position.x / trackWidthPx).coerceIn(0f, 1f))
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

        val v = fraction.coerceIn(0f, 1f)
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

        if (leftEnd > dotR * 4) {
            drawCircle(color = whiteDot, radius = dotR, center = Offset(leftEnd * 0.5f, centerY))
        }
        if (size.width - rightStart > dotR * 4) {
            drawCircle(color = accentDot, radius = dotR, center = Offset((rightStart + size.width) / 2f, centerY))
        }

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


