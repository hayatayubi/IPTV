package com.hayat.lumen.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hayat.lumen.ui.components.LumenButton
import com.hayat.lumen.ui.components.LumenTextField
import com.hayat.lumen.ui.theme.LumenColors

/**
 * The card every Settings dialog sits on. A Dialog is its own window: it keeps focus inside
 * itself and Back closes it, like a SwiftUI .sheet.
 */
@Composable
fun SettingsDialog(
    title: String,
    onDismiss: () -> Unit,
    width: Dp = 480.dp,
    message: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(width)
                .background(LumenColors.Surface, RoundedCornerShape(24.dp))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = LumenColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = LumenColors.TextSecondary)
            }
            content()
        }
    }
}

/**
 * Rename a category or playlist. [original] is the provider's name: when there is one,
 * "Use original name" puts it back (and saving it unchanged does the same).
 */
@Composable
fun RenameDialog(
    title: String,
    current: String,
    original: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    val field = remember { FocusRequester() }
    SettingsDialog(
        title = title,
        onDismiss = onDismiss,
        message = original?.takeIf { it != current }?.let { "Your provider calls it “$it”." },
    ) {
        LumenTextField(value = text, onValueChange = { text = it }, label = "Name", modifier = Modifier.focusRequester(field))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LumenButton(text = "Save", onClick = { onSave(text) }, enabled = text.isNotBlank())
            if (original != null && original != current) {
                LumenButton(text = "Use original name", onClick = { onSave(original) })
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
}

/** A short list of actions, one full-width button each. */
@Composable
fun ActionsDialog(title: String, message: String?, actions: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    SettingsDialog(title = title, onDismiss = onDismiss, message = message) {
        actions.forEachIndexed { index, (label, action) ->
            LumenButton(
                text = label,
                onClick = action,
                modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(first) else Modifier),
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
