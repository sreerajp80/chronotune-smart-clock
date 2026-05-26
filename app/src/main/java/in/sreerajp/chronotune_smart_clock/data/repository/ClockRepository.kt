package `in`.sreerajp.chronotune_smart_clock.data.repository

import `in`.sreerajp.chronotune_smart_clock.data.Alarm
import `in`.sreerajp.chronotune_smart_clock.data.AlarmDao
import `in`.sreerajp.chronotune_smart_clock.data.WorldClock
import `in`.sreerajp.chronotune_smart_clock.data.WorldClockDao
import `in`.sreerajp.chronotune_smart_clock.data.MusicSchedule
import `in`.sreerajp.chronotune_smart_clock.data.MusicScheduleDao
import kotlinx.coroutines.flow.Flow

class ClockRepository(
    private val alarmDao: AlarmDao,
    private val worldClockDao: WorldClockDao,
    private val musicScheduleDao: MusicScheduleDao
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
}
