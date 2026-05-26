package `in`.sreerajp.chronotune_smart_clock

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppPrefs {
    private const val PREFS = "chronotune_app_prefs"
    private const val KEY_24H = "use_24_hour_format"

    private val _is24Hour = MutableStateFlow(false)
    val is24Hour: StateFlow<Boolean> = _is24Hour.asStateFlow()

    fun init(context: Context) {
        _is24Hour.value = prefs(context).getBoolean(KEY_24H, false)
    }

    fun setIs24Hour(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_24H, value).apply()
        _is24Hour.value = value
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
