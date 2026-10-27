package com.hayat.lumen.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.hayat.lumen.ui.theme.LumenColors

/**
 * A TV-friendly text field. A plain text field grabs focus *and* pops the keyboard
 * as soon as the D-pad passes over it. Here the field is a normal focusable row,
 * and the keyboard opens only when you press OK on it.
 */
@Composable
fun LumenTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var editing by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    val outer = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = LumenColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        Surface(
            onClick = { editing = true },
            modifier = Modifier.fillMaxWidth().focusRequester(outer),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = LumenColors.Surface,
                contentColor = LumenColors.TextPrimary,
                focusedContainerColor = LumenColors.SurfaceFocused,
                focusedContentColor = LumenColors.TextPrimary,
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, LumenColors.Accent)),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = LumenColors.TextPrimary),
                cursorBrush = SolidColor(LumenColors.Accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onDone = {
                    editing = false
                    keyboard?.hide()
                    outer.requestFocus()
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .focusRequester(field)
                    // Only reachable while editing, so D-pad navigation skips over it.
                    .focusProperties { canFocus = editing }
                    .onFocusChanged { if (!it.isFocused && editing) editing = false },
                decorationBox = { inner ->
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = LumenColors.TextTertiary)
                    }
                    inner()
                },
            )
        }
    }

    LaunchedEffect(editing) {
        if (editing) {
            field.requestFocus()
            keyboard?.show()
        }
    }
}
