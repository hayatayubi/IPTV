package com.hayat.lumen.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.hayat.lumen.ui.theme.LumenColors

/**
 * Frosted glass: a translucent light fill with a faint edge, over the [Ambient] background.
 * (Real background blur is too heavy for a 2 GB TV; the ambient background is already soft,
 * so a light fill reads the same.) Like SwiftUI's .background(.ultraThinMaterial, in: shape).
 */
fun Modifier.glass(shape: Shape = RoundedCornerShape(24.dp), fill: Color = LumenColors.Glass): Modifier =
    this
        .background(fill, shape)
        .border(1.dp, LumenColors.GlassEdge, shape)

/** [Ambient] as a plain modifier, for screens whose root is already a Row or Column. */
fun Modifier.ambient(cool: Color = LumenColors.AmbientCool, warm: Color = LumenColors.AmbientWarm): Modifier =
    this
        .background(LumenColors.Background)
        .drawBehind { drawGlows(cool, warm) }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlows(cool: Color, warm: Color) {
    drawRect(
        Brush.radialGradient(
            listOf(cool.copy(alpha = 0.55f), cool.copy(alpha = 0.18f), Color.Transparent),
            center = Offset(size.width * 0.15f, 0f),
            radius = size.width * 0.8f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            listOf(warm.copy(alpha = 0.40f), warm.copy(alpha = 0.12f), Color.Transparent),
            center = Offset(size.width * 0.9f, size.height),
            radius = size.width * 0.7f,
        ),
    )
}

/**
 * The background of every main screen: dark, with two big soft glows of colour, so Lumen isn't
 * flat black. [tint] replaces the cool glow (the focused channel's or film's colour) and fades
 * when it changes.
 */
@Composable
fun Ambient(
    modifier: Modifier = Modifier,
    tint: Color? = null,
    warm: Color = LumenColors.AmbientWarm,
    content: @Composable BoxScope.() -> Unit,
) {
    val cool by animateColorAsState(tint ?: LumenColors.AmbientCool, tween(700), label = "ambient")
    Box(
        modifier
            .fillMaxSize()
            .background(LumenColors.Background)
            // Drawn, not composed: changing colour only redraws.
            .drawBehind { drawGlows(cool, warm) },
        content = content,
    )
}
