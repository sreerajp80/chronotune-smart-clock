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
