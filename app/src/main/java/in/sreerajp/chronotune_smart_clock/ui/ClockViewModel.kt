package `in`.sreerajp.chronotune_smart_clock.ui

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `in`.sreerajp.chronotune_smart_clock.AppPrefs
import `in`.sreerajp.chronotune_smart_clock.StopwatchPrefs
import `in`.sreerajp.chronotune_smart_clock.data.Alarm
import `in`.sreerajp.chronotune_smart_clock.data.MusicSchedule
import `in`.sreerajp.chronotune_smart_clock.data.TimerItem
import `in`.sreerajp.chronotune_smart_clock.data.TimerPreset
import `in`.sreerajp.chronotune_smart_clock.data.WorldClock
import `in`.sreerajp.chronotune_smart_clock.data.repository.ClockRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import java.util.Calendar
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    // Alarm Scheduler helper
    private val scheduler = AlarmScheduler(context)

    // Current time trigger ticker
    private val _currentTime = MutableStateFlow(System.currentTimeMillis())
    val currentTime = _currentTime.asStateFlow()

    private var tickerJob: Job? = null
    private var lastTriggeredMinute = -1

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

    private fun startClocksTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                _currentTime.value = now
                
                // Safety real-time trigger evaluator for active sessions
                checkInAppTriggers(now)
                
                delay(100) // Ticks clocks every 100ms
            }
        }
    }

    private fun checkInAppTriggers(now: Long) {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        
        // Ensure trigger fires once per minute boundary
        if (minute == lastTriggeredMinute) return

        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sunday, 2=Monday...
        val mappedDay = when (dayOfWeek) {
            Calendar.SUNDAY -> 7
            else -> dayOfWeek - 1
        }

        // Evaluate Alarms
        alarms.value.forEach { alarm ->
            if (alarm.isEnabled && alarm.hour == hour && alarm.minute == minute && !alarm.isPausedNow()) {
                val repeatDays = alarm.getRepeatDaysList()
                if (repeatDays.isEmpty() || repeatDays.contains(mappedDay)) {
                    val active = ActiveAlarmState.ActiveAlarm(
                        id = alarm.id,
                        type = "ALARM",
                        label = alarm.label.ifBlank { "Alarm Ringing" },
                        tone = alarm.customToneName,
                        volume = alarm.volume,
                        uri = alarm.customToneUri
                    )
                    if (ActiveAlarmState.activeAlarm.value?.id != alarm.id) {
                        ActiveAlarmState.triggerAlarm(context, active)
                        lastTriggeredMinute = minute
                    }
                }
            }
        }

        // Evaluate Music Schedules
        musicSchedules.value.forEach { schedule ->
            if (schedule.isEnabled && schedule.hour == hour && schedule.minute == minute) {
                val repeatDays = schedule.getRepeatDaysList()
                if (repeatDays.isEmpty() || repeatDays.contains(mappedDay)) {
                    val active = ActiveAlarmState.ActiveAlarm(
                        id = schedule.id,
                        type = "MUSIC",
                        label = schedule.label.ifBlank { "Scheduled Music" },
                        tone = schedule.musicTrackName,
                        volume = schedule.volume,
                        durationMin = schedule.durationMinutes,
                        uri = schedule.customFileUri
                    )
                    if (ActiveAlarmState.activeAlarm.value?.id != schedule.id) {
                        ActiveAlarmState.triggerAlarm(context, active)
                        lastTriggeredMinute = minute
                    }
                }
            }
        }
    }

    // --- ALARM DATABASE OPERATIONS ---
    fun addAlarm(hour: Int, minute: Int, label: String, repeatDays: List<Int>, toneName: String, toneUri: String, volume: Float, isVibrate: Boolean, pauseStartMillis: Long = 0L, pauseEndMillis: Long = 0L) {
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
                snoozeMinutes = AppPrefs.getDefaultSnoozeMinutes(context),
                pauseStartMillis = pauseStartMillis,
                pauseEndMillis = pauseEndMillis
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

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            scheduler.cancelAlarm(alarm)
            repository.deleteAlarm(alarm)
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
}
