package `in`.sreerajp.chronotune_smart_clock.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding

private fun Color.lift(amount: Float): Color = Color(
    red = (red + amount).coerceIn(0f, 1f),
    green = (green + amount).coerceIn(0f, 1f),
    blue = (blue + amount).coerceIn(0f, 1f),
    alpha = alpha
)

@Composable
fun Button3D(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    color: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Color.White,
    enabled: Boolean = true,
    elevation: androidx.compose.ui.unit.Dp = 10.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Center,
    content: @Composable RowScope.() -> Unit
) {
    // Disabled state uses Material 3 standard disabled colors so the
    // distinction from the vermillion enabled state is clear in both themes.
    val disabledBg = MaterialTheme.colorScheme.onSurface
        .copy(alpha = 0.12f)
        .compositeOver(MaterialTheme.colorScheme.surface)
    val disabledContent = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    val effectiveColor = if (enabled) color else disabledBg
    val effectiveContentColor = if (enabled) contentColor else disabledContent
    val effectiveElevation = if (enabled) elevation else 0.dp

    val topShine = if (enabled) effectiveColor.lift(0.14f) else effectiveColor
    val midTone = effectiveColor
    val bottomShade = if (enabled) effectiveColor.lift(-0.18f) else effectiveColor

    Box(
        modifier = modifier
            .shadow(
                elevation = effectiveElevation,
                shape = shape,
                spotColor = if (enabled) color.copy(alpha = 0.55f) else Color.Transparent,
                ambientColor = if (enabled) color.copy(alpha = 0.30f) else Color.Transparent
            )
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(topShine, midTone, bottomShade)
                )
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides effectiveContentColor) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = horizontalArrangement,
                content = content
            )
        }
    }
}

@Composable
fun Modifier.button3DShadow(
    color: Color,
    shape: Shape = RoundedCornerShape(20.dp),
    elevation: androidx.compose.ui.unit.Dp = 8.dp
): Modifier = this.shadow(
    elevation = elevation,
    shape = shape,
    spotColor = color.copy(alpha = 0.55f),
    ambientColor = color.copy(alpha = 0.30f)
)
