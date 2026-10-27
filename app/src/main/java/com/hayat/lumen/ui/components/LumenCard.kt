package com.hayat.lumen.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import com.hayat.lumen.ui.theme.LumenColors
import com.hayat.lumen.ui.theme.LumenMotion
import kotlinx.coroutines.delay

private val CardShape = RoundedCornerShape(16.dp)

/**
 * The app's one focusable tile, Apple TV style. Focus is the main affordance: the focused
 * card lifts (scales up over a soft dark shadow) and a sheen of light sweeps across it once.
 * Unfocused cards stay at full brightness.
 *
 * tv-material's Card already handles D-pad OK (click) and long-press OK (onLongClick).
 */
@Composable
fun LumenCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // Unlike SwiftUI's @FocusState, Compose exposes focus as a stream of
    // interactions; collectIsFocusedAsState turns it into a Boolean.
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    // The sheen's position across the card, -1 (off the left) to 1 (off the right).
    val sheen = remember { Animatable(-1f) }
    LaunchedEffect(focused) {
        if (focused) {
            sheen.snapTo(-1f)
            delay(LumenMotion.FOCUS_MS.toLong()) // after the lift
            sheen.animateTo(1f, tween(SHEEN_MS, easing = LinearOutSlowInEasing))
        } else {
            sheen.snapTo(-1f)
        }
    }

    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        interactionSource = interactionSource,
        shape = CardDefaults.shape(shape = CardShape),
        colors = CardDefaults.colors(
            containerColor = LumenColors.Surface,
            focusedContainerColor = LumenColors.Surface,
            pressedContainerColor = LumenColors.Surface,
        ),
        scale = CardDefaults.scale(focusedScale = 1.1f, pressedScale = 1.04f),
        border = CardDefaults.border(), // tvOS style: no focus ring, lift + shadow only.
        glow = CardDefaults.glow(
            focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.7f), elevation = 24.dp),
        ),
    ) {
        Box {
            content()
            // Read in the draw phase only, so the sweep redraws without recomposing the card.
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val v = sheen.value
                        if (v <= -1f || v >= 1f) return@drawBehind
                        val w = size.width
                        val x = v * w * 1.5f
                        drawRect(
                            Brush.linearGradient(
                                0f to Color.Transparent,
                                0.5f to Color.White.copy(alpha = 0.16f),
                                1f to Color.Transparent,
                                start = Offset(x - w * 0.35f, 0f),
                                end = Offset(x + w * 0.35f, size.height),
                            ),
                        )
                    },
            )
        }
    }
}

private const val SHEEN_MS = 700
