package `in`.sreerajp.chronotune_smart_clock.data

import `in`.sreerajp.chronotune_smart_clock.data.repository.ClockRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * User-triggered JSON backup / restore of the app's persistent settings data: alarms, world
 * clocks, music schedules and timer presets. Running timers and stopwatch state are transient
 * and intentionally excluded.
 *
 * The JSON is self-describing and versioned so a future format change stays readable. On import,
 * primary-key ids are dropped so Room assigns fresh ones (no collision with existing rows).
 */
object BackupManager {

    const val FORMAT_VERSION = 1

    enum class ImportMode { MERGE, REPLACE }

    data class ImportResult(
        val alarms: Int,
        val worldClocks: Int,
        val musicSchedules: Int,
        val timerPresets: Int,
        val specialDays: Int = 0
    ) {
        val total: Int
            get() = alarms + worldClocks + musicSchedules + timerPresets + specialDays
    }

    // ---- Export --------------------------------------------------------------------------

    suspend fun exportToJson(repo: ClockRepository): String {
        val root = JSONObject()
        root.put("version", FORMAT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        val alarms = JSONArray()
        repo.getAllAlarmsOnce().forEach { alarms.put(alarmToJson(it)) }
        root.put("alarms", alarms)

        val clocks = JSONArray()
        repo.getAllWorldClocksOnce().forEach { clocks.put(worldClockToJson(it)) }
        root.put("worldClocks", clocks)

        val schedules = JSONArray()
        repo.getAllMusicSchedulesOnce().forEach { schedules.put(musicScheduleToJson(it)) }
        root.put("musicSchedules", schedules)

        val presets = JSONArray()
        repo.getAllPresetsOnce().forEach { presets.put(timerPresetToJson(it)) }
        root.put("timerPresets", presets)

        val specialDays = JSONArray()
        repo.getAllSpecialDaysOnce().forEach { specialDays.put(specialDayToJson(it)) }
        root.put("specialDays", specialDays)

        return root.toString(2)
    }

    // ---- Import --------------------------------------------------------------------------

    /**
     * Parses [json] and inserts the rows. In [ImportMode.REPLACE] the four tables are cleared
     * first. Ids in the file are ignored so Room auto-assigns new ones. Throws on malformed JSON.
     */
    suspend fun importFromJson(
        repo: ClockRepository,
        json: String,
        mode: ImportMode
    ): ImportResult {
        val root = JSONObject(json) // throws org.json.JSONException if not valid JSON

        if (mode == ImportMode.REPLACE) {
            repo.deleteAllAlarms()
            repo.deleteAllWorldClocks()
            repo.deleteAllMusicSchedules()
            repo.deleteAllPresets()
            repo.deleteAllSpecialDays()
        }

        var alarmCount = 0
        root.optJSONArray("alarms")?.let { arr ->
            for (i in 0 until arr.length()) {
                repo.insertAlarm(alarmFromJson(arr.getJSONObject(i)))
                alarmCount++
            }
        }

        var clockCount = 0
        root.optJSONArray("worldClocks")?.let { arr ->
            for (i in 0 until arr.length()) {
                repo.insertWorldClock(worldClockFromJson(arr.getJSONObject(i)))
                clockCount++
            }
        }

        var scheduleCount = 0
        root.optJSONArray("musicSchedules")?.let { arr ->
            for (i in 0 until arr.length()) {
                repo.insertMusicSchedule(musicScheduleFromJson(arr.getJSONObject(i)))
                scheduleCount++
            }
        }

        var presetCount = 0
        root.optJSONArray("timerPresets")?.let { arr ->
            for (i in 0 until arr.length()) {
                repo.insertPreset(timerPresetFromJson(arr.getJSONObject(i)))
                presetCount++
            }
        }

        var specialDayCount = 0
        root.optJSONArray("specialDays")?.let { arr ->
            for (i in 0 until arr.length()) {
                repo.insertSpecialDay(specialDayFromJson(arr.getJSONObject(i)))
                specialDayCount++
            }
        }

        return ImportResult(alarmCount, clockCount, scheduleCount, presetCount, specialDayCount)
    }

    // ---- Row <-> JSON --------------------------------------------------------------------

    private fun alarmToJson(a: Alarm): JSONObject = JSONObject().apply {
        put("hour", a.hour)
        put("minute", a.minute)
        put("label", a.label)
        put("isEnabled", a.isEnabled)
        put("daysOfWeek", a.daysOfWeek)
        put("customToneName", a.customToneName)
        put("customToneUri", a.customToneUri)
        put("volume", a.volume.toDouble())
        put("snoozeMinutes", a.snoozeMinutes)
        put("isVibrate", a.isVibrate)
        put("dismissChallenge", a.dismissChallenge)
        put("challengeDifficulty", a.challengeDifficulty)
        put("challengeCount", a.challengeCount)
        put("pauseStartMillis", a.pauseStartMillis)
        put("pauseEndMillis", a.pauseEndMillis)
        put("autoSilenceMinutes", a.autoSilenceMinutes)
        put("skipNextEpochDay", a.skipNextEpochDay)
        put("holidayMode", a.holidayMode)
        put("maxSnoozeCount", a.maxSnoozeCount)
        put("snoozeMode", a.snoozeMode)
        put("startEpochDay", a.startEpochDay)
    }

    // Ids are omitted on export and ignored on import so Room assigns fresh primary keys.
    private fun alarmFromJson(o: JSONObject): Alarm = Alarm(
        hour = o.getInt("hour"),
        minute = o.getInt("minute"),
        label = o.optString("label", ""),
        isEnabled = o.optBoolean("isEnabled", true),
        daysOfWeek = o.optString("daysOfWeek", ""),
        customToneName = o.optString("customToneName", "Morning Breeze"),
        customToneUri = o.optString("customToneUri", ""),
        volume = o.optDouble("volume", 0.8).toFloat(),
        snoozeMinutes = o.optInt("snoozeMinutes", 5),
        isVibrate = o.optBoolean("isVibrate", true),
        dismissChallenge = o.optString("dismissChallenge", "NONE"),
        challengeDifficulty = o.optString("challengeDifficulty", "EASY"),
        challengeCount = o.optInt("challengeCount", 1),
        pauseStartMillis = o.optLong("pauseStartMillis", 0L),
        pauseEndMillis = o.optLong("pauseEndMillis", 0L),
        autoSilenceMinutes = o.optInt("autoSilenceMinutes", 0),
        skipNextEpochDay = o.optLong("skipNextEpochDay", 0L),
        // Older backup files have none of these; the defaults reproduce the behavior those
        // alarms had when the file was written.
        holidayMode = o.optString("holidayMode", Alarm.HOLIDAY_MODE_ALL_DAYS),
        maxSnoozeCount = o.optInt("maxSnoozeCount", 0),
        snoozeMode = o.optString("snoozeMode", Alarm.SNOOZE_MODE_FIXED),
        startEpochDay = o.optLong("startEpochDay", 0L)
    )

    private fun specialDayToJson(d: SpecialDay): JSONObject = JSONObject().apply {
        put("epochDay", d.epochDay)
        put("name", d.name)
        put("kind", d.kind)
        put("source", d.source)
    }

    private fun specialDayFromJson(o: JSONObject): SpecialDay = SpecialDay(
        epochDay = o.getLong("epochDay"),
        name = o.optString("name", ""),
        kind = o.optString("kind", SpecialDay.KIND_HOLIDAY),
        source = o.optString("source", SpecialDay.SOURCE_MANUAL)
    )

    private fun worldClockToJson(c: WorldClock): JSONObject = JSONObject().apply {
        put("cityName", c.cityName)
        put("timezoneId", c.timezoneId)
    }

    private fun worldClockFromJson(o: JSONObject): WorldClock = WorldClock(
        cityName = o.getString("cityName"),
        timezoneId = o.getString("timezoneId")
    )

    private fun musicScheduleToJson(s: MusicSchedule): JSONObject = JSONObject().apply {
        put("hour", s.hour)
        put("minute", s.minute)
        put("durationMinutes", s.durationMinutes)
        put("label", s.label)
        put("isEnabled", s.isEnabled)
        put("daysOfWeek", s.daysOfWeek)
        put("musicTrackName", s.musicTrackName)
        put("customFileUri", s.customFileUri)
        put("volume", s.volume.toDouble())
    }

    private fun musicScheduleFromJson(o: JSONObject): MusicSchedule = MusicSchedule(
        hour = o.getInt("hour"),
        minute = o.getInt("minute"),
        durationMinutes = o.optInt("durationMinutes", 30),
        label = o.optString("label", ""),
        isEnabled = o.optBoolean("isEnabled", true),
        daysOfWeek = o.optString("daysOfWeek", ""),
        musicTrackName = o.optString("musicTrackName", "Lo-Fi Beats"),
        customFileUri = o.optString("customFileUri", ""),
        volume = o.optDouble("volume", 0.6).toFloat()
    )

    private fun timerPresetToJson(p: TimerPreset): JSONObject = JSONObject().apply {
        put("label", p.label)
        put("durationMs", p.durationMs)
        put("toneName", p.toneName)
        put("toneUri", p.toneUri)
        put("volume", p.volume.toDouble())
        put("sortOrder", p.sortOrder)
    }

    private fun timerPresetFromJson(o: JSONObject): TimerPreset = TimerPreset(
        label = o.getString("label"),
        durationMs = o.getLong("durationMs"),
        toneName = o.optString("toneName", "Cosmic Shimmer"),
        toneUri = o.optString("toneUri", ""),
        volume = o.optDouble("volume", 0.8).toFloat(),
        sortOrder = o.optInt("sortOrder", 0)
    )
}
