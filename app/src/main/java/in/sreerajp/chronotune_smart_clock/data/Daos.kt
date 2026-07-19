package `in`.sreerajp.chronotune_smart_clock.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC")
    fun getAllAlarms(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarmById(id: Int): Alarm?

    @Query("SELECT * FROM alarms")
    suspend fun getAllAlarmsOnce(): List<Alarm>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: Alarm): Long

    @Update
    suspend fun updateAlarm(alarm: Alarm)

    @Delete
    suspend fun deleteAlarm(alarm: Alarm)

    @Query("DELETE FROM alarms")
    suspend fun deleteAllAlarms()
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

    @Query("SELECT * FROM timer_presets ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllPresetsOnce(): List<TimerPreset>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: TimerPreset): Long

    @Update
    suspend fun updatePreset(preset: TimerPreset)

    @Delete
    suspend fun deletePreset(preset: TimerPreset)

    @Query("DELETE FROM timer_presets")
    suspend fun deleteAllPresets()
}

@Dao
interface WorldClockDao {
    @Query("SELECT * FROM world_clocks")
    fun getAllWorldClocks(): Flow<List<WorldClock>>

    @Query("SELECT * FROM world_clocks")
    suspend fun getAllWorldClocksOnce(): List<WorldClock>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorldClock(clock: WorldClock): Long

    @Delete
    suspend fun deleteWorldClock(clock: WorldClock)

    @Query("DELETE FROM world_clocks")
    suspend fun deleteAllWorldClocks()
}

@Dao
interface MusicScheduleDao {
    @Query("SELECT * FROM music_schedules ORDER BY hour ASC, minute ASC")
    fun getAllMusicSchedules(): Flow<List<MusicSchedule>>

    @Query("SELECT * FROM music_schedules WHERE id = :id")
    suspend fun getMusicScheduleById(id: Int): MusicSchedule?

    @Query("SELECT * FROM music_schedules")
    suspend fun getAllMusicSchedulesOnce(): List<MusicSchedule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMusicSchedule(schedule: MusicSchedule): Long

    @Update
    suspend fun updateMusicSchedule(schedule: MusicSchedule)

    @Delete
    suspend fun deleteMusicSchedule(schedule: MusicSchedule)

    @Query("DELETE FROM music_schedules")
    suspend fun deleteAllMusicSchedules()
}

@Dao
interface SpecialDayDao {
    @Query("SELECT * FROM special_days ORDER BY epochDay ASC")
    fun getAll(): Flow<List<SpecialDay>>

    @Query("SELECT * FROM special_days")
    suspend fun getAllOnce(): List<SpecialDay>

    // REPLACE, so re-marking a date the user already marked just updates it in place.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(day: SpecialDay)

    @Query("DELETE FROM special_days WHERE epochDay = :epochDay")
    suspend fun deleteByEpochDay(epochDay: Long)

    // Drops every day older than the given day. Used to keep the list from growing forever
    // with dates that can no longer affect any future alarm.
    @Query("DELETE FROM special_days WHERE epochDay < :epochDay")
    suspend fun deleteBefore(epochDay: Long)

    // Clears one source's rows within a day range. Used before a calendar import so re-importing
    // replaces the previous import instead of stacking on top of it — and, because it filters on
    // source, days the user typed in by hand are left alone. The range keeps an older import
    // outside the current window from being silently erased.
    @Query("DELETE FROM special_days WHERE source = :source AND epochDay BETWEEN :fromDay AND :toDay")
    suspend fun deleteBySourceInRange(source: String, fromDay: Long, toDay: Long)

    @Query("DELETE FROM special_days")
    suspend fun deleteAll()
}
