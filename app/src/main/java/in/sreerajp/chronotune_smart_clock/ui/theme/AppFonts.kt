package `in`.sreerajp.chronotune_smart_clock.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import `in`.sreerajp.chronotune_smart_clock.R

/**
 * The fonts offered under Settings → Appearance.
 *
 * [SYSTEM] keeps the platform default (no bundled file). Every other entry maps to a bundled
 * OFL / Apache-2.0 licensed family in `res/font/`, shipped as static Regular + Bold weights so
 * it renders correctly on all supported API levels (minSdk 24 predates variable-font support).
 * Missing weights (Medium / SemiBold) synthesize from the nearest bundled weight.
 *
 * [key] is the stable value persisted in preferences — do not rename it. [displayName] is what
 * the user sees in the picker.
 */
enum class AppFont(val key: String, val displayName: String) {
    SYSTEM("system", "System Default"),
    INTER("inter", "Inter"),
    POPPINS("poppins", "Poppins"),
    NUNITO("nunito", "Nunito"),
    LATO("lato", "Lato"),
    ROBOTO_SLAB("roboto_slab", "Roboto Slab"),
    MERRIWEATHER("merriweather", "Merriweather");

    companion object {
        /** Resolve a persisted [key] back to an [AppFont], falling back to [SYSTEM]. */
        fun fromKey(key: String?): AppFont =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

private val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_bold, FontWeight.Bold),
)
private val PoppinsFamily = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_bold, FontWeight.Bold),
)
private val NunitoFamily = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_bold, FontWeight.Bold),
)
private val LatoFamily = FontFamily(
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_bold, FontWeight.Bold),
)
private val RobotoSlabFamily = FontFamily(
    Font(R.font.roboto_slab_regular, FontWeight.Normal),
    Font(R.font.roboto_slab_bold, FontWeight.Bold),
)
private val MerriweatherFamily = FontFamily(
    Font(R.font.merriweather_regular, FontWeight.Normal),
    Font(R.font.merriweather_bold, FontWeight.Bold),
)

/** The [FontFamily] for a given [AppFont]; [AppFont.SYSTEM] maps to the platform default. */
fun fontFamilyFor(font: AppFont): FontFamily = when (font) {
    AppFont.SYSTEM -> FontFamily.Default
    AppFont.INTER -> InterFamily
    AppFont.POPPINS -> PoppinsFamily
    AppFont.NUNITO -> NunitoFamily
    AppFont.LATO -> LatoFamily
    AppFont.ROBOTO_SLAB -> RobotoSlabFamily
    AppFont.MERRIWEATHER -> MerriweatherFamily
}

/**
 * Discrete font-size steps applied app-wide by multiplying `LocalDensity.fontScale`.
 * Index 1 (1.0x) is the default. Range 0.85x .. 1.30x stays within existing layouts.
 */
val FONT_SCALE_STEPS: List<Float> = listOf(0.85f, 1.0f, 1.15f, 1.22f, 1.30f)
val FONT_SCALE_LABELS: List<String> = listOf("Small", "Default", "Large", "Larger", "Largest")
const val FONT_SCALE_DEFAULT: Float = 1.0f

/** Nearest step index for a stored scale, so the slider snaps to a known step. */
fun fontScaleStepIndex(scale: Float): Int {
    var best = 1
    var bestDist = Float.MAX_VALUE
    FONT_SCALE_STEPS.forEachIndexed { i, s ->
        val d = kotlin.math.abs(s - scale)
        if (d < bestDist) { bestDist = d; best = i }
    }
    return best
}
