package `in`.sreerajp.chronotune_smart_clock.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC")
    fun getAllAlarms(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarmById(id: Int): Alarm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: Alarm): Long

    @Update
    suspend fun updateAlarm(alarm: Alarm)

    @Delete
    suspend fun deleteAlarm(alarm: Alarm)
}

@Dao
interface TimerDao {
    @Query("SELECT * FROM timers ORDER BY createdAt ASC")
    fun getAllTimers(): Flow<List<TimerItem>>

    @Query("SELECT * FROM timers WHERE id = :id")
    suspend fun getTimerById(id: Int): TimerItem?

    @Query("SELECT * FROM timers")
    suspend fun getAllTimersOnce(): List<TimerItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimer(timer: TimerItem): Long

    @Update
    suspend fun updateTimer(timer: TimerItem)

    @Delete
    suspend fun deleteTimer(timer: TimerItem)

    @Query("DELETE FROM timers WHERE id = :id")
    suspend fun deleteTimerById(id: Int)
}

@Dao
interface TimerPresetDao {
    @Query("SELECT * FROM timer_presets ORDER BY sortOrder ASC, id ASC")
    fun getAllPresets(): Flow<List<TimerPreset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: TimerPreset): Long

    @Update
    suspend fun updatePreset(preset: TimerPreset)

    @Delete
    suspend fun deletePreset(preset: TimerPreset)
}

@Dao
interface WorldClockDao {
    @Query("SELECT * FROM world_clocks")
    fun getAllWorldClocks(): Flow<List<WorldClock>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorldClock(clock: WorldClock): Long

    @Delete
    suspend fun deleteWorldClock(clock: WorldClock)
}

@Dao
interface MusicScheduleDao {
    @Query("SELECT * FROM music_schedules ORDER BY hour ASC, minute ASC")
    fun getAllMusicSchedules(): Flow<List<MusicSchedule>>

    @Query("SELECT * FROM music_schedules WHERE id = :id")
    suspend fun getMusicScheduleById(id: Int): MusicSchedule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMusicSchedule(schedule: MusicSchedule): Long

    @Update
    suspend fun updateMusicSchedule(schedule: MusicSchedule)

    @Delete
    suspend fun deleteMusicSchedule(schedule: MusicSchedule)
}
