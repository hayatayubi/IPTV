package com.hayat.lumen.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** TV overscan-safe margins. Content inside these is visible on every TV. */
object SafeArea {
    val Horizontal = 48.dp
    val Vertical = 27.dp
}

/** One motion curve for the whole app: ease-out, nothing bouncy. */
object LumenMotion {
    private val EaseOut = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val FOCUS_MS = 180
    fun <T> focus(): TweenSpec<T> = tween(FOCUS_MS, easing = EaseOut)
}

private val LumenColorScheme = darkColorScheme(
    primary = LumenColors.Accent,
    onPrimary = LumenColors.TextPrimary,
    background = LumenColors.Background,
    onBackground = LumenColors.TextPrimary,
    surface = LumenColors.Surface,
    onSurface = LumenColors.TextPrimary,
    surfaceVariant = LumenColors.SurfaceFocused,
    onSurfaceVariant = LumenColors.TextSecondary,
    border = LumenColors.TextTertiary,
)

// Like a SwiftUI environment value set at the root: everything below reads
// MaterialTheme.colorScheme / .typography.
@Composable
fun LumenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LumenColorScheme,
        typography = LumenTypography,
        content = content,
    )
}
