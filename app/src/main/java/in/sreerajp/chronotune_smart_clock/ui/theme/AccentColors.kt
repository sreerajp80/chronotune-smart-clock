package `in`.sreerajp.chronotune_smart_clock.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp

/**
 * Helpers for the user-chosen accent color. The accent is stored as a single ARGB Int in
 * AppPrefs (0 = "no override"); these pure functions derive the dependent Material roles
 * (onPrimary / primaryContainer / onPrimaryContainer) so any picked color stays legible.
 */

// Suggested quick-pick swatches shown above the custom picker. The first matches the
// app's built-in Vermillion so users have a one-tap "classic" choice.
val AccentSwatches: List<Color> = listOf(
    Color(0xFFD2552B), // Vermillion (classic)
    Color(0xFF2F6FED), // Blue
    Color(0xFF0E9F8E), // Teal
    Color(0xFF7C4DFF), // Violet
    Color(0xFFDAA520), // Gold
    Color(0xFF5E8C3E), // Sage
    Color(0xFFE0457B), // Pink
    Color(0xFF455A64)  // Slate
)

/** Black or white, whichever contrasts better against [bg]. Used for content on the accent. */
fun onColorFor(bg: Color): Color =
    if (bg.luminance() > 0.45f) Color.Black else Color.White

/** A soft tinted container derived from [accent]: lightened in light theme, darkened in dark theme. */
fun deriveContainer(accent: Color, isDark: Boolean): Color =
    lerp(accent, if (isDark) Color.Black else Color.White, if (isDark) 0.74f else 0.78f)

/** A readable on-container color: a deeper accent in light theme, a brighter one in dark theme. */
fun deriveOnContainer(accent: Color, isDark: Boolean): Color =
    lerp(accent, if (isDark) Color.White else Color.Black, if (isDark) 0.60f else 0.45f)
