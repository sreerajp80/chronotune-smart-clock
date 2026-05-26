package `in`.sreerajp.chronotune_smart_clock.widget

import android.content.Context
import `in`.sreerajp.chronotune_smart_clock.R

/**
 * Persists user-configurable widget appearance settings.
 *
 * The opacity values are stored as floats in [0f, 1f] and snapped to one
 * of five tiers at read time. Snapping keeps the runtime fast (no per-pixel
 * recomposition) and lets us back the digital widget's backdrop with a
 * pre-baked rounded-rect drawable per tier.
 */
object WidgetPrefs {
    private const val PREFS = "chronotune_widget_prefs"
    private const val KEY_DIGITAL_BG_ALPHA = "digital_bg_alpha"
    private const val KEY_ANALOG_BG_ALPHA = "analog_bg_alpha"

    const val DEFAULT_ALPHA = 0.6f

    fun getDigitalBgAlpha(context: Context): Float =
        prefs(context).getFloat(KEY_DIGITAL_BG_ALPHA, DEFAULT_ALPHA).coerceIn(0f, 1f)

    fun setDigitalBgAlpha(context: Context, alpha: Float) {
        prefs(context).edit().putFloat(KEY_DIGITAL_BG_ALPHA, alpha.coerceIn(0f, 1f)).apply()
    }

    fun getAnalogBgAlpha(context: Context): Float =
        prefs(context).getFloat(KEY_ANALOG_BG_ALPHA, DEFAULT_ALPHA).coerceIn(0f, 1f)

    fun setAnalogBgAlpha(context: Context, alpha: Float) {
        prefs(context).edit().putFloat(KEY_ANALOG_BG_ALPHA, alpha.coerceIn(0f, 1f)).apply()
    }

    /**
     * Snap a continuous alpha to one of five pre-baked backdrop tiers and
     * return the corresponding drawable resource. Used by the digital
     * widget where the backdrop must be a resource ID (RemoteViews limit).
     */
    fun digitalBackgroundRes(alpha: Float): Int {
        val tier = (alpha.coerceIn(0f, 1f) * 4f).toInt().coerceIn(0, 4)
        return when (tier) {
            0 -> R.drawable.widget_background_a00
            1 -> R.drawable.widget_background_a25
            2 -> R.drawable.widget_background_a50
            3 -> R.drawable.widget_background_a75
            else -> R.drawable.widget_background_a100
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
