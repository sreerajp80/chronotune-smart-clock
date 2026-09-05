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
import `in`.sreerajp.chronotune_smart_clock.data.SpecialDay
import `in`.sreerajp.chronotune_smart_clock.data.SpecialDayDao
import `in`.sreerajp.chronotune_smart_clock.data.AlarmEvent
import `in`.sreerajp.chronotune_smart_clock.data.AlarmEventDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ClockRepository(
    private val alarmDao: AlarmDao,
    private val worldClockDao: WorldClockDao,
    private val musicScheduleDao: MusicScheduleDao,
    private val timerDao: TimerDao,
    private val timerPresetDao: TimerPresetDao,
    // Optional: the background services that only ring alarms and timers never touch the
    // holiday list, so they can build a repository without it.
    private val specialDayDao: SpecialDayDao? = null,
    // Optional for the same reason. The ring path writes events through AlarmEventLog, which
    // goes straight to the DAO, so a service-side repository does not need this either.
    private val alarmEventDao: AlarmEventDao? = null
) {
    // Alarms
    val allAlarms: Flow<List<Alarm>> = alarmDao.getAllAlarms()
    
    suspend fun getAlarmById(id: Int): Alarm? = alarmDao.getAlarmById(id)

    suspend fun insertAlarm(alarm: Alarm): Long = alarmDao.insertAlarm(alarm)

    suspend fun updateAlarm(alarm: Alarm) = alarmDao.updateAlarm(alarm)

    suspend fun deleteAlarm(alarm: Alarm) = alarmDao.deleteAlarm(alarm)

    suspend fun getAllAlarmsOnce(): List<Alarm> = alarmDao.getAllAlarmsOnce()

    suspend fun deleteAllAlarms() = alarmDao.deleteAllAlarms()

    // World Clocks
    val allWorldClocks: Flow<List<WorldClock>> = worldClockDao.getAllWorldClocks()

    suspend fun insertWorldClock(clock: WorldClock): Long = worldClockDao.insertWorldClock(clock)

    suspend fun deleteWorldClock(clock: WorldClock) = worldClockDao.deleteWorldClock(clock)

    suspend fun getAllWorldClocksOnce(): List<WorldClock> = worldClockDao.getAllWorldClocksOnce()

    suspend fun deleteAllWorldClocks() = worldClockDao.deleteAllWorldClocks()

    // Music Schedules
    val allMusicSchedules: Flow<List<MusicSchedule>> = musicScheduleDao.getAllMusicSchedules()

    suspend fun getMusicScheduleById(id: Int): MusicSchedule? = musicScheduleDao.getMusicScheduleById(id)

    suspend fun insertMusicSchedule(schedule: MusicSchedule): Long = musicScheduleDao.insertMusicSchedule(schedule)

    suspend fun updateMusicSchedule(schedule: MusicSchedule) = musicScheduleDao.updateMusicSchedule(schedule)

    suspend fun deleteMusicSchedule(schedule: MusicSchedule) = musicScheduleDao.deleteMusicSchedule(schedule)

    suspend fun getAllMusicSchedulesOnce(): List<MusicSchedule> = musicScheduleDao.getAllMusicSchedulesOnce()

    suspend fun deleteAllMusicSchedules() = musicScheduleDao.deleteAllMusicSchedules()

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

    suspend fun getAllPresetsOnce(): List<TimerPreset> = timerPresetDao.getAllPresetsOnce()

    suspend fun deleteAllPresets() = timerPresetDao.deleteAllPresets()

    // Holidays / work days. The shared list every alarm consults, depending on its holidayMode.
    val allSpecialDays: Flow<List<SpecialDay>> =
        specialDayDao?.getAll() ?: flowOf(emptyList())

    suspend fun getAllSpecialDaysOnce(): List<SpecialDay> =
        specialDayDao?.getAllOnce() ?: emptyList()

    suspend fun insertSpecialDay(day: SpecialDay) {
        specialDayDao?.insert(day)
    }

    suspend fun deleteSpecialDay(epochDay: Long) {
        specialDayDao?.deleteByEpochDay(epochDay)
    }

    /** Drops marked days that are already in the past — they can't affect any future alarm. */
    suspend fun pruneSpecialDaysBefore(epochDay: Long) {
        specialDayDao?.deleteBefore(epochDay)
    }

    suspend fun deleteAllSpecialDays() {
        specialDayDao?.deleteAll()
    }

    // Alarm event history — what each alarm actually did.
    fun recentAlarmEvents(limit: Int): Flow<List<AlarmEvent>> =
        alarmEventDao?.getRecent(limit) ?: flowOf(emptyList())

    suspend fun getRecentAlarmEventsOnce(limit: Int): List<AlarmEvent> =
        alarmEventDao?.getRecentOnce(limit) ?: emptyList()

    suspend fun insertAlarmEvent(event: AlarmEvent) {
        alarmEventDao?.insert(event)
    }

    /** Arm records whose trigger time has passed, for the missed-alarm check. */
    suspend fun getDueArmRecords(after: Long, before: Long): List<AlarmEvent> =
        alarmEventDao?.getDueArmRecords(after, before) ?: emptyList()

    suspend fun getAlarmEventsForAlarmSince(alarmId: Int, since: Long): List<AlarmEvent> =
        alarmEventDao?.getForAlarmSince(alarmId, since) ?: emptyList()

    /** Drops log rows older than [cutoff]. Retention housekeeping, run at start-up. */
    suspend fun pruneAlarmEventsBefore(cutoff: Long) {
        alarmEventDao?.deleteBefore(cutoff)
    }

    suspend fun deleteAllAlarmEvents() {
        alarmEventDao?.deleteAll()
    }

    /** Replaces one source's days inside [days] — the calendar import's clean-slate step. */
    suspend fun replaceSpecialDays(source: String, days: LongRange, fresh: List<SpecialDay>) {
        val dao = specialDayDao ?: return
        dao.deleteBySourceInRange(source, days.first, days.last)
        fresh.forEach { dao.insert(it) }
    }
}
