package `in`.sreerajp.chronotune_smart_clock.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `in`.sreerajp.chronotune_smart_clock.data.Alarm
import `in`.sreerajp.chronotune_smart_clock.data.MusicSchedule
import `in`.sreerajp.chronotune_smart_clock.data.WorldClock
import `in`.sreerajp.chronotune_smart_clock.data.repository.ClockRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

data class CityZone(val cityName: String, val timezoneId: String, val country: String)

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

    // Pre-seeded searchable major cities zone list
    val availableCities = listOf(
        CityZone("London", "Europe/London", "United Kingdom"),
        CityZone("New York", "America/New_York", "United States"),
        CityZone("Tokyo", "Asia/Tokyo", "Japan"),
        CityZone("Mumbai", "Asia/Kolkata", "India"),
        CityZone("Sydney", "Australia/Sydney", "Australia"),
        CityZone("Paris", "Europe/Paris", "France"),
        CityZone("Cairo", "Africa/Cairo", "Egypt"),
        CityZone("Dubai", "Asia/Dubai", "United Arab Emirates"),
        CityZone("Singapore", "Asia/Singapore", "Singapore"),
        CityZone("São Paulo", "America/Sao_Paulo", "Brazil"),
        CityZone("Moscow", "Europe/Moscow", "Russia"),
        CityZone("Cape Town", "Africa/Johannesburg", "South Africa"),
        CityZone("Los Angeles", "America/Los_Angeles", "United States"),
        CityZone("Reykjavík", "Atlantic/Reykjavik", "Iceland"),
        CityZone("Nairobi", "Africa/Nairobi", "Kenya"),
        CityZone("Bangkok", "Asia/Bangkok", "Thailand")
    )

    // --- STOPWATCH STATE ---
    private var stopwatchJob: Job? = null
    private var stopwatchBaseTime = 0L
    private var stopwatchAccumulated = 0L

    private val _stopwatchTime = MutableStateFlow(0L)
    val stopwatchTime = _stopwatchTime.asStateFlow()

    private val _stopwatchState = MutableStateFlow(StopwatchState.IDLE)
    val stopwatchState = _stopwatchState.asStateFlow()

    private val _laps = MutableStateFlow<List<Lap>>(emptyList())
    val laps = _laps.asStateFlow()

    enum class StopwatchState { IDLE, RUNNING, PAUSED }
    data class Lap(val number: Int, val splitTimeMs: Long, val lapTimeMs: Long)

    // --- TIMER STATE ---
    private var timerJob: Job? = null
    private val _timerDuration = MutableStateFlow(0L) // original total milliseconds
    val timerDuration = _timerDuration.asStateFlow()

    private val _timerRemaining = MutableStateFlow(0L) // current countdown remaining ms
    val timerRemaining = _timerRemaining.asStateFlow()

    private val _timerState = MutableStateFlow(TimerState.IDLE)
    val timerState = _timerState.asStateFlow()

    enum class TimerState { IDLE, RUNNING, PAUSED }

    class Factory(
        private val context: Context,
        private val repository: ClockRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ClockViewModel(context.applicationContext, repository) as T
    }

    init {
        startClocksTicker()
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
            if (alarm.isEnabled && alarm.hour == hour && alarm.minute == minute) {
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
    fun addAlarm(hour: Int, minute: Int, label: String, repeatDays: List<Int>, toneName: String, toneUri: String, volume: Float, isVibrate: Boolean) {
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
                isEnabled = true
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
    fun startStopwatch() {
        if (_stopwatchState.value == StopwatchState.RUNNING) return
        
        _stopwatchState.value = StopwatchState.RUNNING
        stopwatchBaseTime = System.currentTimeMillis()
        
        stopwatchJob = viewModelScope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - stopwatchBaseTime + stopwatchAccumulated
                _stopwatchTime.value = elapsed
                delay(16) // Tick at ~60fps for hyper-smooth subsecond animations!
            }
        }
    }

    fun pauseStopwatch() {
        if (_stopwatchState.value != StopwatchState.RUNNING) return
        
        _stopwatchState.value = StopwatchState.PAUSED
        stopwatchJob?.cancel()
        stopwatchAccumulated = _stopwatchTime.value
    }

    fun resetStopwatch() {
        stopwatchJob?.cancel()
        _stopwatchState.value = StopwatchState.IDLE
        stopwatchAccumulated = 0L
        _stopwatchTime.value = 0L
        _laps.value = emptyList()
    }

    fun recordLap() {
        val totalElapsed = _stopwatchTime.value
        val lapCount = _laps.value.size + 1
        
        val lastLapSplit = if (_laps.value.isEmpty()) 0L else _laps.value.first().splitTimeMs
        val lapTime = totalElapsed - lastLapSplit
        
        val newLap = Lap(lapCount, totalElapsed, lapTime)
        _laps.value = listOf(newLap) + _laps.value // Store in descending order to keep newest visible
    }

    // --- TIMER CONTROLLER ---
    fun setTimer(hours: Int, minutes: Int, seconds: Int) {
        val ms = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L
        _timerDuration.value = ms
        _timerRemaining.value = ms
        _timerState.value = TimerState.IDLE
        timerJob?.cancel()
    }

    fun startTimer() {
        if (_timerState.value == TimerState.RUNNING || _timerRemaining.value <= 0L) return
        
        _timerState.value = TimerState.RUNNING
        val timerTargetTime = System.currentTimeMillis() + _timerRemaining.value
        
        timerJob = viewModelScope.launch {
            while (isActive) {
                val rem = timerTargetTime - System.currentTimeMillis()
                if (rem <= 0L) {
                    _timerRemaining.value = 0L
                    _timerState.value = TimerState.IDLE
                    
                    // Alert!
                    ActiveAlarmState.triggerAlarm(
                        context,
                        ActiveAlarmState.ActiveAlarm(
                            id = 99999,
                            type = "ALARM",
                            label = "Timer Finished",
                            tone = "Cosmic Shimmer",
                            volume = 0.8f
                        )
                    )
                    break
                }
                _timerRemaining.value = rem
                delay(50)
            }
        }
    }

    fun pauseTimer() {
        if (_timerState.value != TimerState.RUNNING) return
        _timerState.value = TimerState.PAUSED
        timerJob?.cancel()
    }

    fun resetTimer() {
        timerJob?.cancel()
        _timerState.value = TimerState.IDLE
        _timerRemaining.value = _timerDuration.value
    }
}
