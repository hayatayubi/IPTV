package com.hayat.lumen.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hayat.lumen.ui.theme.LumenColors

/** A pill button. Same focus language as list rows: light when focused. */
@Composable
fun LumenButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
        colors = ButtonDefaults.colors(
            containerColor = LumenColors.Surface,
            contentColor = LumenColors.TextPrimary,
            focusedContainerColor = LumenColors.TextPrimary,
            focusedContentColor = LumenColors.Background,
            pressedContainerColor = LumenColors.TextSecondary,
            pressedContentColor = LumenColors.Background,
            disabledContainerColor = LumenColors.Surface,
            disabledContentColor = LumenColors.TextTertiary,
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
