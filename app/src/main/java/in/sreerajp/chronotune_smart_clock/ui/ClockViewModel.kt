package `in`.sreerajp.chronotune_smart_clock.ui

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `in`.sreerajp.chronotune_smart_clock.AppPrefs
import `in`.sreerajp.chronotune_smart_clock.StopwatchPrefs
import `in`.sreerajp.chronotune_smart_clock.data.Alarm
import `in`.sreerajp.chronotune_smart_clock.data.BackupManager
import `in`.sreerajp.chronotune_smart_clock.data.MusicSchedule
import `in`.sreerajp.chronotune_smart_clock.data.TimerItem
import `in`.sreerajp.chronotune_smart_clock.data.TimerPreset
import `in`.sreerajp.chronotune_smart_clock.data.WorldClock
import `in`.sreerajp.chronotune_smart_clock.data.repository.ClockRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CityZone(val cityName: String, val timezoneId: String, val region: String)

class ClockViewModel(
    private val context: Context,
    private val repository: ClockRepository
) : ViewModel() {

    // Available world clock database list
    val worldClocks: StateFlow<List<WorldClock>> = repository.allWorldClocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Available alarms database list
    val alarms: StateFlow<List<Alarm>> = repository.allAlarms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Available automated music schedules database list
    val musicSchedules: StateFlow<List<MusicSchedule>> = repository.allMusicSchedules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Days marked as holidays / working days. Shared by every alarm; an alarm only chooses
    // whether to consult the list via its holidayMode.
    val specialDays: StateFlow<List<`in`.sreerajp.chronotune_smart_clock.data.SpecialDay>> =
        repository.allSpecialDays
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Alarm Scheduler helper
    private val scheduler = AlarmScheduler(context)

    /**
     * Everything the alarms have done lately, newest first. This is the whole log — arms,
     * rings, snoozes, dismisses and the system's own re-arms — and the history screen decides
     * what to show from it.
     */
    val alarmEvents: StateFlow<List<`in`.sreerajp.chronotune_smart_clock.data.AlarmEvent>> =
        repository.recentAlarmEvents(AppPrefs.EVENT_PAGE_SIZE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // The newest alarm found to have not rung, once per miss. Null when there is nothing to
    // report or the user has already dismissed the banner for it.
    private val _missedAlarm =
        MutableStateFlow<`in`.sreerajp.chronotune_smart_clock.data.AlarmEvent?>(null)
    val missedAlarm: StateFlow<`in`.sreerajp.chronotune_smart_clock.data.AlarmEvent?> =
        _missedAlarm.asStateFlow()

    // Current time trigger ticker
    private val _currentTime = MutableStateFlow(System.currentTimeMillis())
    val currentTime = _currentTime.asStateFlow()

    private var tickerJob: Job? = null

    // Full searchable zone catalog, built once from the IANA timezone database
    // shipped with Android (TimeZone.getAvailableIDs() — API 1, unlike java.time's
    // ZoneId which needs API 26). Filtered to clean "Region/City" entries and
    // sorted by current UTC offset, then city name.
    val availableCities: List<CityZone> by lazy {
        // Real continent/ocean regions only — drops Etc/*, SystemV/*, US/*, and
        // bare aliases (GMT, UTC, Egypt, Cuba, ...) so the picker stays clean.
        val regions = setOf(
            "Africa", "America", "Antarctica", "Arctic", "Asia",
            "Atlantic", "Australia", "Europe", "Indian", "Pacific"
        )
        val now = System.currentTimeMillis()
        java.util.TimeZone.getAvailableIDs()
            .filter { id -> id.substringBefore('/') in regions && id.contains('/') }
            .map { id ->
                val segments = id.split('/')
                val cityName = segments.last().replace('_', ' ')
                // For 3-segment IDs (e.g. America/Argentina/Buenos_Aires) append the
                // middle segment to disambiguate; otherwise just the continent.
                val region = if (segments.size >= 3) {
                    segments[0] + " · " + segments[1].replace('_', ' ')
                } else {
                    segments[0]
                }
                CityZone(cityName, id, region)
            }
            .sortedWith(
                compareBy(
                    { java.util.TimeZone.getTimeZone(it.timezoneId).getOffset(now) },
                    { it.cityName }
                )
            )
    }

    // --- STOPWATCH STATE (persistent: backed by StopwatchPrefs hub + ChronometerService) ---
    enum class StopwatchState { IDLE, RUNNING, PAUSED }
    data class Lap(val number: Int, val splitTimeMs: Long, val lapTimeMs: Long)

    // Smoothly-ticked elapsed value, always derived from the persisted elapsedRealtime base.
    private val _stopwatchTime = MutableStateFlow(StopwatchPrefs.snapshot.value.elapsedNow())
    val stopwatchTime = _stopwatchTime.asStateFlow()

    val stopwatchState: StateFlow<StopwatchState> = StopwatchPrefs.snapshot
        .map {
            when (it.state) {
                StopwatchPrefs.STATE_RUNNING -> StopwatchState.RUNNING
                StopwatchPrefs.STATE_PAUSED -> StopwatchState.PAUSED
                else -> StopwatchState.IDLE
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StopwatchState.IDLE)

    val laps: StateFlow<List<Lap>> = StopwatchPrefs.snapshot
        .map { snap -> snap.laps.map { Lap(it.number, it.splitTimeMs, it.lapTimeMs) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- TIMER STATE (persistent multi-timer list + named presets) ---
    val timers: StateFlow<List<TimerItem>> = repository.allTimers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val timerPresets: StateFlow<List<TimerPreset>> = repository.allTimerPresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Monotonic "now" tick the Timer UI uses to recompute each running timer's remaining time.
    private val _nowElapsed = MutableStateFlow(SystemClock.elapsedRealtime())
    val nowElapsed = _nowElapsed.asStateFlow()

    private var chronoTickerJob: Job? = null

    class Factory(
        private val context: Context,
        private val repository: ClockRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ClockViewModel(context.applicationContext, repository) as T
    }

    init {
        StopwatchPrefs.init(context)
        startClocksTicker()
        startChronoTicker()
        startSpecialDaySync()
        repairAlarmsOnStart()
        checkForMissedAlarms()
    }

    /**
     * Works out which alarms were armed for a time that has passed but never rang, records each
     * one, and surfaces the newest for the banner on the alarms screen.
     *
     * This is the whole point of writing an arm record for every alarm: without it, an alarm
     * that never went off leaves no trace at all and looks exactly like a quiet morning.
     *
     * Also prunes log rows past the retention window while it is here.
     */
    private fun checkForMissedAlarms() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    repository.pruneAlarmEventsBefore(now - AppPrefs.EVENT_RETENTION_MS)

                    val armed = repository.getDueArmRecords(
                        after = now - AppPrefs.MISSED_LOOKBACK_MS,
                        // Only judge firings that are properly past due, so an alarm ringing
                        // right now is never mistaken for one that failed.
                        before = now - `in`.sreerajp.chronotune_smart_clock.data.MISSED_TOLERANCE_MS
                    )
                    if (armed.isEmpty()) return@withContext

                    val others = armed.map { it.alarmId }.distinct().flatMap { alarmId ->
                        repository.getAlarmEventsForAlarmSince(
                            alarmId, now - AppPrefs.MISSED_LOOKBACK_MS
                        )
                    }
                    val missed = `in`.sreerajp.chronotune_smart_clock.data.findMissedArmRecords(
                        armed, others
                    )

                    missed.forEach { armRecord ->
                        `in`.sreerajp.chronotune_smart_clock.data.AlarmEventLog.recordNow(
                            context,
                            armRecord.copy(
                                id = 0,
                                event = `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent.MISSED,
                                actualAt = 0L,   // stamped with "now", i.e. when it was noticed
                                detail = "No ring was recorded for this alarm"
                            )
                        )
                    }

                    val newest = missed.maxByOrNull { it.scheduledAt }
                    if (newest != null &&
                        newest.scheduledAt > AppPrefs.getMissedBannerSeenAt(context)
                    ) {
                        _missedAlarm.value = newest
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ClockViewModel", "Missed-alarm check failed: ${e.message}")
            }
        }
    }

    /** Hides the missed-alarm banner and remembers not to show that one again. */
    fun dismissMissedAlarmBanner() {
        val current = _missedAlarm.value
        _missedAlarm.value = null
        if (current != null) {
            AppPrefs.setMissedBannerSeenAt(context, current.scheduledAt)
        }
    }

    /** Empties the alarm history. */
    fun clearAlarmHistory() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.deleteAllAlarmEvents() }
                _missedAlarm.value = null
            } catch (e: Exception) {
                android.util.Log.e("ClockViewModel", "Could not clear history: ${e.message}")
            }
        }
    }

    /**
     * Writes the history to [uri] as plain text, so a bad night can be shared or kept.
     * Reports through the existing backup event channel, which the settings UI already shows.
     */
    fun exportAlarmHistory(uri: Uri) {
        viewModelScope.launch {
            try {
                val rows = withContext(Dispatchers.IO) {
                    repository.getRecentAlarmEventsOnce(AppPrefs.EVENT_PAGE_SIZE)
                }
                val text = BackupManager.formatAlarmEventLog(rows)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(text.toByteArray())
                    } ?: throw IllegalStateException("Could not open the chosen file")
                }
                _backupEvent.value = BackupEvent.Exported(text.toByteArray().size)
            } catch (e: Exception) {
                _backupEvent.value = BackupEvent.Failed(e.message ?: "Export failed")
            }
        }
    }

    /**
     * Re-arms alarms the system has silently thrown away, and starts the watchdog that keeps
     * doing so while the app is closed.
     *
     * Android drops every pending alarm an app owns when the app is force-stopped, killed by an
     * OEM battery cleaner, or hibernated for being unused — with no notice of any kind. Before
     * this, opening the app did nothing to repair that: the alarm stayed lost until the user
     * rebooted or edited it by hand, which is the most likely reason an alarm "sometimes does
     * not ring".
     *
     * Only alarms whose PendingIntent has actually gone are rebuilt, so a normal launch does
     * almost no work.
     */
    private fun repairAlarmsOnStart() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val repaired = `in`.sreerajp.chronotune_smart_clock.ui.WatchdogReceiver
                        .repairMissingAlarms(context, scheduler)
                    if (repaired > 0) {
                        android.util.Log.d("ClockViewModel", "Start-up re-armed $repaired alarm(s)")
                    }
                    scheduler.scheduleWatchdog()
                }
            } catch (e: Exception) {
                // Never let start-up housekeeping break the app opening.
                android.util.Log.e("ClockViewModel", "Alarm repair on start failed: ${e.message}")
            }

            // Put back an alarm-stream level left at maximum by a ring whose process was
            // killed before it could restore it. Skipped while something is actually ringing,
            // so this can never quieten a live alarm.
            try {
                if (ActiveAlarmState.activeAlarm.value == null) {
                    withContext(Dispatchers.IO) {
                        `in`.sreerajp.chronotune_smart_clock.audio.AudioEngine(context)
                            .recoverAlarmStream()
                    }
                }
            } catch (_: Exception) { /* housekeeping only */ }
        }
    }

    /**
     * Keeps [`in`.sreerajp.chronotune_smart_clock.data.SpecialDayRegistry] — the map the
     * scheduling path reads — in step with the database while the app is alive.
     *
     * Marked days are also pruned once at start-up: a day in the past can never match a future
     * alarm again, so keeping it only makes the list longer.
     */
    private fun startSpecialDaySync() {
        viewModelScope.launch {
            try {
                repository.pruneSpecialDaysBefore(Alarm.todayEpochDay())
            } catch (_: Exception) { /* pruning is housekeeping — never block start-up */ }
            repository.allSpecialDays.collect { days ->
                `in`.sreerajp.chronotune_smart_clock.data.SpecialDayRegistry.set(days)
            }
        }
    }

    // Drives the smooth stopwatch readout and the timer "now" tick from the persisted bases.
    // Ticks at ~60fps only while something is actually counting; otherwise idles slowly.
    private fun startChronoTicker() {
        chronoTickerJob?.cancel()
        chronoTickerJob = viewModelScope.launch {
            while (isActive) {
                _stopwatchTime.value = StopwatchPrefs.snapshot.value.elapsedNow()
                _nowElapsed.value = SystemClock.elapsedRealtime()
                val swRunning = StopwatchPrefs.snapshot.value.state == StopwatchPrefs.STATE_RUNNING
                val anyTimerRunning = timers.value.any { it.state == TimerItem.STATE_RUNNING }
                delay(if (swRunning || anyTimerRunning) 16L else 250L)
            }
        }
    }

    // Drives the clock readout only. Alarms and music schedules are rung solely by
    // AlarmManager -> AlarmReceiver -> AlarmService. There used to be a second, in-app
    // evaluator here that rang anything matching the current hour + minute, but it had no
    // memory of what had already rung: dismissing an alarm and then opening the app inside
    // the same clock minute made it ring all over again, with default settings instead of
    // the alarm's own. The scheduler path fires whether the app is open, backgrounded or
    // killed, so nothing is lost by leaving the ringing entirely to it.
    private fun startClocksTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                _currentTime.value = System.currentTimeMillis()
                delay(100) // Ticks clocks every 100ms
            }
        }
    }

    // --- ALARM DATABASE OPERATIONS ---
    fun addAlarm(hour: Int, minute: Int, label: String, repeatDays: List<Int>, toneName: String, toneUri: String, volume: Float, isVibrate: Boolean, pauseStartMillis: Long = 0L, pauseEndMillis: Long = 0L, snoozeMinutes: Int = AppPrefs.getDefaultSnoozeMinutes(context), dismissChallenge: String = "NONE", challengeDifficulty: String = "EASY", challengeCount: Int = 1, autoSilenceMinutes: Int = AppPrefs.getDefaultAutoSilenceMinutes(context), holidayMode: String = Alarm.HOLIDAY_MODE_ALL_DAYS, maxSnoozeCount: Int = AppPrefs.getDefaultMaxSnoozeCount(context), snoozeMode: String = AppPrefs.getDefaultSnoozeMode(context), startEpochDay: Long = 0L) {
        val daysString = repeatDays.sorted().joinToString(",")
        viewModelScope.launch {
            val alarm = Alarm(
                hour = hour,
                minute = minute,
                label = label,
                daysOfWeek = daysString,
                customToneName = toneName,
                customToneUri = toneUri,
                volume = volume,
                isVibrate = isVibrate,
                isEnabled = true,
                snoozeMinutes = snoozeMinutes,
                dismissChallenge = dismissChallenge,
                challengeDifficulty = challengeDifficulty,
                challengeCount = challengeCount,
                pauseStartMillis = pauseStartMillis,
                pauseEndMillis = pauseEndMillis,
                autoSilenceMinutes = autoSilenceMinutes,
                holidayMode = holidayMode,
                maxSnoozeCount = maxSnoozeCount,
                snoozeMode = snoozeMode,
                startEpochDay = startEpochDay
            )
            val dbId = repository.insertAlarm(alarm).toInt()
            scheduler.scheduleAlarm(alarm.copy(id = dbId))
        }
    }

    fun toggleAlarm(alarm: Alarm) {
        viewModelScope.launch {
            val updated = alarm.copy(isEnabled = !alarm.isEnabled)
            repository.updateAlarm(updated)
            if (updated.isEnabled) {
                scheduler.scheduleAlarm(updated)
            } else {
                scheduler.cancelAlarm(updated)
            }
        }
    }

    fun updateAlarm(alarm: Alarm) {
        viewModelScope.launch {
            repository.updateAlarm(alarm)
            if (alarm.isEnabled) {
                scheduler.scheduleAlarm(alarm)
            } else {
                scheduler.cancelAlarm(alarm)
            }
        }
    }

    /**
     * Toggles "skip next alarm" for a repeating alarm. When [skip] is true we compute the very
     * next occurrence (ignoring any current skip) and store its epoch day, so the alarm jumps
     * over that one firing and resumes on the following selected day. When false we clear it.
     */
    fun setSkipNext(alarm: Alarm, skip: Boolean) {
        viewModelScope.launch {
            val skipDay = if (skip) {
                val next = `in`.sreerajp.chronotune_smart_clock.data.nextTriggerTime(
                    alarm.hour, alarm.minute, alarm.getRepeatDaysList(),
                    alarm.pauseStartMillis, alarm.pauseEndMillis
                )
                Alarm.localCalendarToEpochDay(next)
            } else {
                0L
            }
            val updated = alarm.copy(skipNextEpochDay = skipDay)
            repository.updateAlarm(updated)
            if (updated.isEnabled) {
                scheduler.scheduleAlarm(updated)
            } else {
                scheduler.cancelAlarm(updated)
            }
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            scheduler.cancelAlarm(alarm)
            repository.deleteAlarm(alarm)
        }
    }

    // --- HOLIDAY / WORK-DAY LIST ---

    /**
     * Marks a calendar day as a holiday or a working day.
     *
     * [epochDay] uses the same UTC-midnight basis as the Material date picker and
     * [Alarm.localCalendarToEpochDay], so a value straight out of the picker can be passed in.
     */
    fun addSpecialDay(
        epochDay: Long,
        name: String,
        kind: String = `in`.sreerajp.chronotune_smart_clock.data.SpecialDay.KIND_HOLIDAY
    ) {
        viewModelScope.launch {
            repository.insertSpecialDay(
                `in`.sreerajp.chronotune_smart_clock.data.SpecialDay(
                    epochDay = epochDay,
                    name = name.trim(),
                    kind = kind
                )
            )
            refreshDaysAndReschedule()
        }
    }

    fun deleteSpecialDay(epochDay: Long) {
        viewModelScope.launch {
            repository.deleteSpecialDay(epochDay)
            refreshDaysAndReschedule()
        }
    }

    // --- CALENDAR IMPORT ---

    /** What the Holidays screen shows while and after an import runs. */
    sealed interface ImportState {
        data object Idle : ImportState
        data object Running : ImportState
        data class Done(val added: Int, val calendarName: String) : ImportState
        data class Failed(val message: String) : ImportState
    }

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    /** The calendars on the device. Empty when READ_CALENDAR has not been granted. */
    suspend fun availableCalendars(): List<`in`.sreerajp.chronotune_smart_clock.data.CalendarHolidayImporter.CalendarInfo> =
        withContext(Dispatchers.IO) {
            `in`.sreerajp.chronotune_smart_clock.data.CalendarHolidayImporter.listCalendars(context)
        }

    /**
     * Reads all-day events from [calendarId] into the holiday list, replacing whatever a
     * previous import put there. Hand-entered days are untouched.
     */
    fun importHolidaysFromCalendar(calendarId: Long, calendarName: String) {
        viewModelScope.launch {
            _importState.value = ImportState.Running
            try {
                val importer = `in`.sreerajp.chronotune_smart_clock.data.CalendarHolidayImporter
                val fresh = withContext(Dispatchers.IO) {
                    importer.readHolidays(context, calendarId)
                }
                repository.replaceSpecialDays(
                    source = `in`.sreerajp.chronotune_smart_clock.data.SpecialDay.SOURCE_CALENDAR,
                    days = importer.importWindowDays(),
                    fresh = fresh
                )
                // Same rule as a manual edit: an import that marks next Monday a holiday has to
                // reach the alarm already pending for that Monday.
                refreshDaysAndReschedule()
                _importState.value = ImportState.Done(fresh.size, calendarName)
            } catch (e: Exception) {
                _importState.value = ImportState.Failed(e.message ?: "Import failed")
            }
        }
    }

    /**
     * Reloads the registry and re-arms every enabled alarm.
     *
     * This is the part that is easy to miss: an alarm already handed to AlarmManager keeps its
     * old trigger time, so marking a day as a holiday would not affect the alarm that is already
     * pending for that very day. Re-arming after each edit is what makes the setting take effect
     * immediately instead of one firing late.
     */
    private suspend fun refreshDaysAndReschedule() {
        `in`.sreerajp.chronotune_smart_clock.data.SpecialDayRegistry.set(
            repository.getAllSpecialDaysOnce()
        )
        withContext(Dispatchers.IO) {
            repository.getAllAlarmsOnce().forEach { alarm ->
                // Only holiday-aware alarms can change their trigger time, so leave the rest
                // alone rather than churning every pending intent on the device.
                if (alarm.isEnabled && alarm.holidayMode != Alarm.HOLIDAY_MODE_ALL_DAYS) {
                    scheduler.scheduleAlarm(alarm)
                }
            }
        }
    }

    // --- BACKUP / RESTORE ---

    sealed interface BackupEvent {
        data class Exported(val bytes: Int) : BackupEvent
        data class Imported(val result: BackupManager.ImportResult) : BackupEvent
        data class Failed(val message: String) : BackupEvent
    }

    private val _backupEvent = MutableStateFlow<BackupEvent?>(null)
    val backupEvent: StateFlow<BackupEvent?> = _backupEvent.asStateFlow()

    /** Clears the last backup event after the UI has shown it. */
    fun clearBackupEvent() { _backupEvent.value = null }

    /** Writes a full JSON backup to the user-picked [uri]. */
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    val text = BackupManager.exportToJson(repository)
                    val data = text.toByteArray(Charsets.UTF_8)
                    // "rwt" truncates any existing content so we never leave a stale tail.
                    context.contentResolver.openOutputStream(uri, "rwt")?.use { it.write(data) }
                        ?: throw IllegalStateException("Could not open the file for writing")
                    data.size
                }
                _backupEvent.value = BackupEvent.Exported(bytes)
            } catch (e: Exception) {
                _backupEvent.value = BackupEvent.Failed(e.message ?: "Export failed")
            }
        }
    }

    /** Restores data from the user-picked [uri] using the chosen merge/replace [mode]. */
    fun importBackup(uri: Uri, mode: BackupManager.ImportMode) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: throw IllegalStateException("Could not open the file for reading")
                    // On replace, cancel the currently-scheduled alarms/music before their rows
                    // are deleted, so no stale AlarmManager entry can fire after the restore.
                    if (mode == BackupManager.ImportMode.REPLACE) {
                        repository.getAllAlarmsOnce().forEach { scheduler.cancelAlarm(it) }
                        repository.getAllMusicSchedulesOnce().forEach { scheduler.cancelMusic(it) }
                    }
                    BackupManager.importFromJson(repository, text, mode)
                }
                // Arm everything that is now in the database. The restored holiday list has to
                // be in the registry first, or a holiday-aware alarm would be armed against the
                // day list from before the restore.
                withContext(Dispatchers.IO) {
                    `in`.sreerajp.chronotune_smart_clock.data.SpecialDayRegistry.set(
                        repository.getAllSpecialDaysOnce()
                    )
                    repository.getAllAlarmsOnce().forEach { if (it.isEnabled) scheduler.scheduleAlarm(it) }
                    repository.getAllMusicSchedulesOnce().forEach { if (it.isEnabled) scheduler.scheduleMusic(it) }
                }
                _backupEvent.value = BackupEvent.Imported(result)
            } catch (e: Exception) {
                _backupEvent.value = BackupEvent.Failed(e.message ?: "Import failed")
            }
        }
    }

    // --- WORLD CLOCK DATABASE OPERATIONS ---
    fun addWorldClock(cityName: String, timezoneId: String) {
        viewModelScope.launch {
            repository.insertWorldClock(WorldClock(cityName = cityName, timezoneId = timezoneId))
        }
    }

    fun deleteWorldClock(clock: WorldClock) {
        viewModelScope.launch {
            repository.deleteWorldClock(clock)
        }
    }

    // --- MUSIC SCHEDULE DATABASE OPERATIONS ---
    fun addMusicSchedule(hour: Int, minute: Int, durationMin: Int, label: String, repeatDays: List<Int>, trackName: String, fileUri: String, volume: Float) {
        val daysString = repeatDays.sorted().joinToString(",")
        viewModelScope.launch {
            val schedule = MusicSchedule(
                hour = hour,
                minute = minute,
                durationMinutes = durationMin,
                label = label,
                daysOfWeek = daysString,
                musicTrackName = trackName,
                customFileUri = fileUri,
                volume = volume,
                isEnabled = true
            )
            val dbId = repository.insertMusicSchedule(schedule).toInt()
            scheduler.scheduleMusic(schedule.copy(id = dbId))
        }
    }

    fun toggleMusicSchedule(schedule: MusicSchedule) {
        viewModelScope.launch {
            val updated = schedule.copy(isEnabled = !schedule.isEnabled)
            repository.updateMusicSchedule(updated)
            if (updated.isEnabled) {
                scheduler.scheduleMusic(updated)
            } else {
                scheduler.cancelMusic(updated)
            }
        }
    }

    fun updateMusicSchedule(schedule: MusicSchedule) {
        viewModelScope.launch {
            repository.updateMusicSchedule(schedule)
            if (schedule.isEnabled) {
                scheduler.scheduleMusic(schedule)
            } else {
                scheduler.cancelMusic(schedule)
            }
        }
    }

    fun deleteMusicSchedule(schedule: MusicSchedule) {
        viewModelScope.launch {
            scheduler.cancelMusic(schedule)
            repository.deleteMusicSchedule(schedule)
        }
    }

    // --- STOPWATCH CONTROLLER ---
    // All mutations go through the StopwatchPrefs hub, which persists the elapsedRealtime base
    // and refreshes the foreground ChronometerService so the live notification stays in sync.
    fun startStopwatch() = StopwatchPrefs.start(context)

    fun pauseStopwatch() = StopwatchPrefs.pause(context)

    fun resetStopwatch() = StopwatchPrefs.reset(context)

    fun recordLap() = StopwatchPrefs.lap(context)

    // --- TIMER CONTROLLER (multiple concurrent persistent timers) ---
    fun addTimer(durationMs: Long, label: String, toneName: String, toneUri: String, volume: Float) {
        if (durationMs <= 0L) return
        viewModelScope.launch {
            TimerEngine.addAndStart(repository, context, durationMs, label, toneName, toneUri, volume)
        }
    }

    fun pauseTimer(id: Int) = viewModelScope.launch { TimerEngine.pause(repository, context, id) }

    fun resumeTimer(id: Int) = viewModelScope.launch { TimerEngine.resume(repository, context, id) }

    fun addMinuteToTimer(id: Int) = viewModelScope.launch { TimerEngine.addMinute(repository, context, id) }

    fun cancelTimer(id: Int) = viewModelScope.launch { TimerEngine.cancel(repository, context, id) }

    // Dismisses a finished timer: stop any ongoing ring and drop it from the list.
    fun dismissTimer(id: Int) {
        viewModelScope.launch {
            try {
                context.startService(AlarmService.stopIntent(context))
            } catch (_: Exception) {
                ActiveAlarmState.dismiss(context)
            }
            repository.deleteTimerById(id)
            ChronometerService.refresh(context)
        }
    }

    // --- TIMER PRESETS ---
    fun startTimerFromPreset(preset: TimerPreset) {
        viewModelScope.launch {
            TimerEngine.addAndStart(
                repository, context, preset.durationMs, preset.label,
                preset.toneName, preset.toneUri, preset.volume
            )
        }
    }

    fun addPreset(label: String, durationMs: Long, toneName: String, toneUri: String, volume: Float) {
        if (durationMs <= 0L) return
        viewModelScope.launch {
            val order = (timerPresets.value.maxOfOrNull { it.sortOrder } ?: -1) + 1
            repository.insertPreset(
                TimerPreset(
                    label = label.ifBlank { "Preset" },
                    durationMs = durationMs,
                    toneName = toneName,
                    toneUri = toneUri,
                    volume = volume,
                    sortOrder = order
                )
            )
        }
    }

    fun deletePreset(preset: TimerPreset) = viewModelScope.launch { repository.deletePreset(preset) }

    // --- VOICE COMMANDS ---
    /**
     * Carries out a command that came from speech — either the in-app microphone or a voice
     * assistant through [VoiceIntentActivity]. Everything is created with the user's normal
     * defaults, exactly as if they had used the Add button.
     *
     * Returns true when something was created or changed, so the caller can confirm it.
     */
    fun applyVoiceCommand(command: VoiceCommand): Boolean = when (command) {
        is VoiceCommand.SetAlarm -> {
            addAlarm(
                hour = command.hour,
                minute = command.minute,
                label = command.label,
                repeatDays = command.days,
                toneName = AppPrefs.defaultAlarmTone.value,
                toneUri = "",
                volume = 0.8f,
                isVibrate = true
            )
            true
        }

        is VoiceCommand.SetTimer -> {
            addTimer(
                durationMs = command.durationMs,
                label = command.label,
                toneName = "Cosmic Shimmer",
                toneUri = "",
                volume = 0.8f
            )
            true
        }

        is VoiceCommand.DismissAlarm -> {
            ActiveAlarmState.dismiss(context)
            true
        }

        is VoiceCommand.SnoozeAlarm -> {
            ActiveAlarmState.snooze(
                context,
                command.minutes ?: AppPrefs.getDefaultSnoozeMinutes(context)
            )
            true
        }

        // Navigation-only commands are handled by the caller, which owns the tab state.
        VoiceCommand.ShowAlarms, VoiceCommand.ShowTimers -> true
        is VoiceCommand.Unknown -> false
    }
}
