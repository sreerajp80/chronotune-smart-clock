package `in`.sreerajp.chronotune_smart_clock.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme =
  darkColorScheme(
    primary = VermillionDark,
    onPrimary = Color.White,
    primaryContainer = VermillionDarkContainer,
    onPrimaryContainer = Color(0xFFFFE0D6),
    secondary = GoldDark,
    onSecondary = Color(0xFF1A1408),
    secondaryContainer = Color(0xFF3D2E10),
    onSecondaryContainer = Color(0xFFFFE9B8),
    tertiary = SageDark,
    onTertiary = Color(0xFF0F1A0A),
    background = DarkBg,
    onBackground = Color.White,
    surface = DarkSurface,
    onSurface = Color.White,
    surfaceVariant = DarkSurfaceLow,
    onSurfaceVariant = GhostGray,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerLow = DarkSurfaceLow,
    outline = GhostGray
  )

private val LightColorScheme =
  lightColorScheme(
    primary = VermillionLight,
    onPrimary = Color.White,
    primaryContainer = VermillionLightContainer,
    onPrimaryContainer = VermillionLightOnContainer,
    secondary = GoldLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCEFC9),
    onSecondaryContainer = Color(0xFF6B4F08),
    tertiary = SageLight,
    onTertiary = Color.White,
    background = LightBg,
    onBackground = WarmSlate,
    surface = LightSurface,
    onSurface = WarmSlate,
    surfaceVariant = LightSurfaceLow,
    onSurfaceVariant = WarmGray,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerLow = LightSurfaceLow,
    outline = WarmTan
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Custom bespoke palette wins over dynamic system colors
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  // System status / navigation bar icon contrast tracking the active theme
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as Activity).window
      val controller = WindowCompat.getInsetsController(window, view)
      controller.isAppearanceLightStatusBars = !darkTheme
      controller.isAppearanceLightNavigationBars = !darkTheme
    }
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
