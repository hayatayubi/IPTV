package com.hayat.lumen.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.hayat.lumen.ui.theme.LumenColors

/**
 * A focusable list row, tvOS style: the focused row turns light with dark text;
 * the "selected" row (e.g. the open group) keeps a subtle fill when focus moves away.
 * Content colour flows down automatically (tv Surface sets LocalContentColor).
 */
@Composable
fun LumenListItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    minHeight: Dp = 56.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) LumenColors.GlassStrong else Color.Transparent,
            contentColor = if (selected) LumenColors.TextPrimary else LumenColors.TextSecondary,
            focusedContainerColor = LumenColors.TextPrimary,
            focusedContentColor = LumenColors.Background,
            pressedContainerColor = LumenColors.TextSecondary,
            pressedContentColor = LumenColors.Background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = minHeight)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}
