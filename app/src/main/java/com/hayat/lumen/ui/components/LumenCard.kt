package com.hayat.lumen.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import com.hayat.lumen.ui.theme.LumenColors
import com.hayat.lumen.ui.theme.LumenMotion

private val CardShape = RoundedCornerShape(12.dp)

/**
 * The app's one focusable tile. Focus is the main affordance: the focused card
 * scales up with a soft accent glow; unfocused cards sit slightly dimmed.
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
    val contentAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0.7f,
        animationSpec = LumenMotion.focus(),
        label = "cardDim",
    )

    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        interactionSource = interactionSource,
        shape = CardDefaults.shape(shape = CardShape),
        colors = CardDefaults.colors(
            containerColor = LumenColors.Surface,
            focusedContainerColor = LumenColors.SurfaceFocused,
            pressedContainerColor = LumenColors.SurfaceFocused,
        ),
        scale = CardDefaults.scale(focusedScale = 1.06f, pressedScale = 1.02f),
        border = CardDefaults.border(), // tvOS style: no focus ring, scale + glow only.
        glow = CardDefaults.glow(
            focusedGlow = Glow(elevationColor = LumenColors.Accent.copy(alpha = 0.35f), elevation = 16.dp),
        ),
    ) {
        Box(Modifier.graphicsLayer { alpha = contentAlpha }) {
            content()
        }
    }
}
