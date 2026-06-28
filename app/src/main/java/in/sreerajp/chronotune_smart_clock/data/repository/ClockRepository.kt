package `in`.sreerajp.chronotune_smart_clock.data.repository

import `in`.sreerajp.chronotune_smart_clock.data.Alarm
import `in`.sreerajp.chronotune_smart_clock.data.AlarmDao
import `in`.sreerajp.chronotune_smart_clock.data.WorldClock
import `in`.sreerajp.chronotune_smart_clock.data.WorldClockDao
import `in`.sreerajp.chronotune_smart_clock.data.MusicSchedule
import `in`.sreerajp.chronotune_smart_clock.data.MusicScheduleDao
import `in`.sreerajp.chronotune_smart_clock.data.TimerItem
import `in`.sreerajp.chronotune_smart_clock.data.TimerDao
import `in`.sreerajp.chronotune_smart_clock.data.TimerPreset
import `in`.sreerajp.chronotune_smart_clock.data.TimerPresetDao
import kotlinx.coroutines.flow.Flow

class ClockRepository(
    private val alarmDao: AlarmDao,
    private val worldClockDao: WorldClockDao,
    private val musicScheduleDao: MusicScheduleDao,
    private val timerDao: TimerDao,
    private val timerPresetDao: TimerPresetDao
) {
    // Alarms
    val allAlarms: Flow<List<Alarm>> = alarmDao.getAllAlarms()
    
    suspend fun getAlarmById(id: Int): Alarm? = alarmDao.getAlarmById(id)
    
    suspend fun insertAlarm(alarm: Alarm): Long = alarmDao.insertAlarm(alarm)
    
    suspend fun updateAlarm(alarm: Alarm) = alarmDao.updateAlarm(alarm)
    
    suspend fun deleteAlarm(alarm: Alarm) = alarmDao.deleteAlarm(alarm)

    // World Clocks
    val allWorldClocks: Flow<List<WorldClock>> = worldClockDao.getAllWorldClocks()
    
    suspend fun insertWorldClock(clock: WorldClock): Long = worldClockDao.insertWorldClock(clock)
    
    suspend fun deleteWorldClock(clock: WorldClock) = worldClockDao.deleteWorldClock(clock)

    // Music Schedules
    val allMusicSchedules: Flow<List<MusicSchedule>> = musicScheduleDao.getAllMusicSchedules()
    
    suspend fun getMusicScheduleById(id: Int): MusicSchedule? = musicScheduleDao.getMusicScheduleById(id)
    
    suspend fun insertMusicSchedule(schedule: MusicSchedule): Long = musicScheduleDao.insertMusicSchedule(schedule)
    
    suspend fun updateMusicSchedule(schedule: MusicSchedule) = musicScheduleDao.updateMusicSchedule(schedule)
    
    suspend fun deleteMusicSchedule(schedule: MusicSchedule) = musicScheduleDao.deleteMusicSchedule(schedule)

    // Timers
    val allTimers: Flow<List<TimerItem>> = timerDao.getAllTimers()

    suspend fun getTimerById(id: Int): TimerItem? = timerDao.getTimerById(id)

    suspend fun getAllTimersOnce(): List<TimerItem> = timerDao.getAllTimersOnce()

    suspend fun insertTimer(timer: TimerItem): Long = timerDao.insertTimer(timer)

    suspend fun updateTimer(timer: TimerItem) = timerDao.updateTimer(timer)

    suspend fun deleteTimer(timer: TimerItem) = timerDao.deleteTimer(timer)

    suspend fun deleteTimerById(id: Int) = timerDao.deleteTimerById(id)

    // Timer presets
    val allTimerPresets: Flow<List<TimerPreset>> = timerPresetDao.getAllPresets()

    suspend fun insertPreset(preset: TimerPreset): Long = timerPresetDao.insertPreset(preset)

    suspend fun updatePreset(preset: TimerPreset) = timerPresetDao.updatePreset(preset)

    suspend fun deletePreset(preset: TimerPreset) = timerPresetDao.deletePreset(preset)
}
