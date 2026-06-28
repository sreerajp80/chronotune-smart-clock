package `in`.sreerajp.chronotune_smart_clock

import android.content.Context
import `in`.sreerajp.chronotune_smart_clock.audio.AudioEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppPrefs {
    private const val PREFS = "chronotune_app_prefs"
    private const val KEY_24H = "use_24_hour_format"
    private const val KEY_FADE_IN = "alarm_fade_in_enabled"

    // Alarm creation defaults (seed new alarms only; existing alarms are untouched).
    private const val KEY_DEFAULT_SNOOZE = "alarm_default_snooze_minutes"
    private const val KEY_DEFAULT_TONE = "alarm_default_tone_name"

    // App-wide appearance.
    private const val KEY_WEEK_START = "week_start_day"     // 1=Mon .. 7=Sun
    private const val KEY_ACCENT = "theme_accent_color"     // ARGB Int; 0 = use built-in theme default

    // Default-snooze choices (minutes) and the fallback value.
    const val DEFAULT_SNOOZE_MINUTES = 5
    val SNOOZE_CHOICES = listOf(5, 10, 15, 20, 30)

    // Fallback built-in tone for new alarms.
    const val DEFAULT_TONE_NAME = "Morning Breeze"

    // Week-start options (Monday default). 1=Mon .. 7=Sun, matching Scheduled.daysOfWeek.
    const val WEEK_START_DEFAULT = 1

    // 0 means "no accent override" → the per-theme built-in palette is used as-is.
    const val ACCENT_DEFAULT = 0

    // Music-schedule crossfade settings (alarms are unaffected).
    private const val KEY_XFADE_ENABLED = "music_crossfade_enabled"
    private const val KEY_XFADE_MS = "music_crossfade_ms"
    private const val KEY_XFADE_CURVE = "music_crossfade_curve" // "equal_power" | "linear"
    private const val KEY_NORMALIZE = "music_loudness_normalize"

    // Duration of the alarm volume ramp when fade-in is enabled.
    const val FADE_IN_MS = 20_000L

    // Crossfade duration bounds + default, in milliseconds.
    const val CROSSFADE_MIN_MS = 0
    const val CROSSFADE_MAX_MS = 12_000
    const val CROSSFADE_DEFAULT_MS = 3_000

    private const val CURVE_EQUAL_POWER = "equal_power"
    private const val CURVE_LINEAR = "linear"

    private val _is24Hour = MutableStateFlow(false)
    val is24Hour: StateFlow<Boolean> = _is24Hour.asStateFlow()

    private val _fadeInEnabled = MutableStateFlow(false)
    val fadeInEnabled: StateFlow<Boolean> = _fadeInEnabled.asStateFlow()

    private val _crossfadeEnabled = MutableStateFlow(true)
    val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled.asStateFlow()

    private val _crossfadeMs = MutableStateFlow(CROSSFADE_DEFAULT_MS)
    val crossfadeMs: StateFlow<Int> = _crossfadeMs.asStateFlow()

    private val _crossfadeCurve = MutableStateFlow(AudioEngine.CrossfadeCurve.EQUAL_POWER)
    val crossfadeCurve: StateFlow<AudioEngine.CrossfadeCurve> = _crossfadeCurve.asStateFlow()

    private val _loudnessNormalize = MutableStateFlow(false)
    val loudnessNormalize: StateFlow<Boolean> = _loudnessNormalize.asStateFlow()

    private val _defaultSnoozeMinutes = MutableStateFlow(DEFAULT_SNOOZE_MINUTES)
    val defaultSnoozeMinutes: StateFlow<Int> = _defaultSnoozeMinutes.asStateFlow()

    private val _defaultAlarmTone = MutableStateFlow(DEFAULT_TONE_NAME)
    val defaultAlarmTone: StateFlow<String> = _defaultAlarmTone.asStateFlow()

    private val _weekStartDay = MutableStateFlow(WEEK_START_DEFAULT)
    val weekStartDay: StateFlow<Int> = _weekStartDay.asStateFlow()

    private val _accentColor = MutableStateFlow(ACCENT_DEFAULT)
    val accentColor: StateFlow<Int> = _accentColor.asStateFlow()

    fun init(context: Context) {
        _is24Hour.value = prefs(context).getBoolean(KEY_24H, false)
        _fadeInEnabled.value = prefs(context).getBoolean(KEY_FADE_IN, false)
        _crossfadeEnabled.value = prefs(context).getBoolean(KEY_XFADE_ENABLED, true)
        _crossfadeMs.value = prefs(context).getInt(KEY_XFADE_MS, CROSSFADE_DEFAULT_MS)
        _crossfadeCurve.value = curveFromKey(prefs(context).getString(KEY_XFADE_CURVE, CURVE_EQUAL_POWER))
        _loudnessNormalize.value = prefs(context).getBoolean(KEY_NORMALIZE, false)
        _defaultSnoozeMinutes.value = prefs(context).getInt(KEY_DEFAULT_SNOOZE, DEFAULT_SNOOZE_MINUTES)
        _defaultAlarmTone.value = prefs(context).getString(KEY_DEFAULT_TONE, DEFAULT_TONE_NAME) ?: DEFAULT_TONE_NAME
        _weekStartDay.value = prefs(context).getInt(KEY_WEEK_START, WEEK_START_DEFAULT)
        _accentColor.value = prefs(context).getInt(KEY_ACCENT, ACCENT_DEFAULT)
    }

    fun setIs24Hour(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_24H, value).apply()
        _is24Hour.value = value
    }

    fun setFadeInEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_FADE_IN, value).apply()
        _fadeInEnabled.value = value
    }

    fun setCrossfadeEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_XFADE_ENABLED, value).apply()
        _crossfadeEnabled.value = value
    }

    fun setCrossfadeMs(context: Context, value: Int) {
        val clamped = value.coerceIn(CROSSFADE_MIN_MS, CROSSFADE_MAX_MS)
        prefs(context).edit().putInt(KEY_XFADE_MS, clamped).apply()
        _crossfadeMs.value = clamped
    }

    fun setCrossfadeCurve(context: Context, value: AudioEngine.CrossfadeCurve) {
        prefs(context).edit().putString(KEY_XFADE_CURVE, curveToKey(value)).apply()
        _crossfadeCurve.value = value
    }

    fun setLoudnessNormalize(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_NORMALIZE, value).apply()
        _loudnessNormalize.value = value
    }

    fun setDefaultSnoozeMinutes(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_DEFAULT_SNOOZE, value).apply()
        _defaultSnoozeMinutes.value = value
    }

    fun setDefaultAlarmTone(context: Context, value: String) {
        prefs(context).edit().putString(KEY_DEFAULT_TONE, value).apply()
        _defaultAlarmTone.value = value
    }

    fun setWeekStartDay(context: Context, value: Int) {
        val clamped = value.coerceIn(1, 7)
        prefs(context).edit().putInt(KEY_WEEK_START, clamped).apply()
        _weekStartDay.value = clamped
    }

    /** Set the accent ARGB color; pass [ACCENT_DEFAULT] (0) to clear the override. */
    fun setAccentColor(context: Context, argb: Int) {
        prefs(context).edit().putInt(KEY_ACCENT, argb).apply()
        _accentColor.value = argb
    }

    // ---- Cold-read getters (correct even before init(), e.g. an alarm firing in a cold process) ----

    // Effective crossfade window in ms: 0 when crossfade is disabled.
    fun getCrossfadeMs(context: Context): Long {
        val p = prefs(context)
        if (!p.getBoolean(KEY_XFADE_ENABLED, true)) return 0L
        return p.getInt(KEY_XFADE_MS, CROSSFADE_DEFAULT_MS)
            .coerceIn(CROSSFADE_MIN_MS, CROSSFADE_MAX_MS).toLong()
    }

    fun getCrossfadeCurve(context: Context): AudioEngine.CrossfadeCurve =
        curveFromKey(prefs(context).getString(KEY_XFADE_CURVE, CURVE_EQUAL_POWER))

    fun isLoudnessNormalize(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NORMALIZE, false)

    private fun curveToKey(curve: AudioEngine.CrossfadeCurve): String = when (curve) {
        AudioEngine.CrossfadeCurve.LINEAR -> CURVE_LINEAR
        AudioEngine.CrossfadeCurve.EQUAL_POWER -> CURVE_EQUAL_POWER
    }

    private fun curveFromKey(key: String?): AudioEngine.CrossfadeCurve =
        if (key == CURVE_LINEAR) AudioEngine.CrossfadeCurve.LINEAR else AudioEngine.CrossfadeCurve.EQUAL_POWER

    // Reads the fade-in setting straight from disk so it is correct even when called from a
    // cold process (an alarm firing via AlarmService before the in-memory flow is init-ed).
    // Returns the ramp duration in ms, or 0 when fade-in is off.
    fun getFadeInMs(context: Context): Long =
        if (prefs(context).getBoolean(KEY_FADE_IN, false)) FADE_IN_MS else 0L

    // Cold read for the default snooze length (used when creating an alarm).
    fun getDefaultSnoozeMinutes(context: Context): Int =
        prefs(context).getInt(KEY_DEFAULT_SNOOZE, DEFAULT_SNOOZE_MINUTES)

    /**
     * The seven day-numbers (1=Mon .. 7=Sun) rotated so the list begins at [start].
     * Display-order only — day numbering itself is unchanged.
     */
    fun orderedWeekDays(start: Int): List<Int> {
        val s = start.coerceIn(1, 7)
        return (0 until 7).map { ((s - 1 + it) % 7) + 1 }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
