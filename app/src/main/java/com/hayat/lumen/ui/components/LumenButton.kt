package com.hayat.lumen.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hayat.lumen.ui.theme.LumenColors

/**
 * A pill button. Frosted glass, white when focused. [primary] is the screen's main action
 * (Play): white even when not focused, like the Apple TV app.
 */
@Composable
fun LumenButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    @DrawableRes icon: Int? = null,
) {
    val glassBorder = Border(BorderStroke(1.dp, LumenColors.GlassEdge), shape = RoundedCornerShape(50))
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = if (text.isEmpty()) 14.dp else 24.dp, vertical = 10.dp),
        colors = ButtonDefaults.colors(
            containerColor = if (primary) LumenColors.TextPrimary else LumenColors.GlassStrong,
            contentColor = if (primary) LumenColors.Background else LumenColors.TextPrimary,
            focusedContainerColor = LumenColors.TextPrimary,
            focusedContentColor = LumenColors.Background,
            pressedContainerColor = LumenColors.TextSecondary,
            pressedContentColor = LumenColors.Background,
            disabledContainerColor = LumenColors.Glass,
            disabledContentColor = LumenColors.TextTertiary,
        ),
        border = ButtonDefaults.border(border = if (primary) Border.None else glassBorder, focusedBorder = Border.None),
        scale = ButtonDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = text.ifEmpty { null }, modifier = Modifier.size(20.dp))
                if (text.isNotEmpty()) Spacer(Modifier.width(8.dp))
            }
            // One line, always: squeezed for room, a button used to wrap its label ("TV gui / e").
            if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
        }
    }
}
