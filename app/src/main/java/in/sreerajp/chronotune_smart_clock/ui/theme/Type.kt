package `in`.sreerajp.chronotune_smart_clock.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

/**
 * Build a [Typography] whose every style uses [fontFamily]. We start from Material 3's default
 * [Typography] (which carries all the standard sizes / weights / line-heights) and only swap the
 * family, so Material components keep their intended metrics while honoring the user's font choice.
 *
 * Note: many screens use plain `Text(..., fontSize = X.sp)` without a family; those are covered
 * separately by providing `LocalTextStyle` in `MyApplicationTheme`.
 */
fun appTypography(fontFamily: FontFamily): Typography {
    if (fontFamily == FontFamily.Default) return DefaultTypography
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = fontFamily),
    )
}

/** Material 3 defaults with the platform font — used when the user keeps System Default. */
val DefaultTypography = Typography()

// Backwards-compatible alias for any existing references to `Typography`.
val Typography = DefaultTypography
