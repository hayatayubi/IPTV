package com.hayat.lumen.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.hayat.lumen.ui.theme.LumenColors

enum class Tab(val label: String) { LIVE("Live TV"), MOVIES("Movies"), SERIES("Series") }

/**
 * The Apple TV app's top bar: Live TV · Movies · Series. The open tab keeps a soft fill;
 * the focused one turns light like every other focusable in Lumen.
 */
@Composable
fun TopTabs(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Pills(
        labels = Tab.entries.map { it.label },
        selected = selected.ordinal,
        onSelect = { if (it != selected.ordinal) onSelect(Tab.entries[it]) },
        modifier = modifier,
    )
}

/** A row of pill-shaped choices (tabs, seasons). [onFocus] lets a choice follow focus, like tvOS segmented controls. */
@Composable
fun Pills(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onFocus: ((Int) -> Unit)? = null,
) {
    // Lazy so a series with twenty seasons scrolls instead of overflowing.
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(labels) { index, label ->
            val isSelected = index == selected
            Surface(
                onClick = { onSelect(index) },
                modifier = if (onFocus != null) Modifier.onFocusChanged { if (it.isFocused) onFocus(index) } else Modifier,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) LumenColors.Surface else Color.Transparent,
                    contentColor = if (isSelected) LumenColors.TextPrimary else LumenColors.TextSecondary,
                    focusedContainerColor = LumenColors.TextPrimary,
                    focusedContentColor = LumenColors.Background,
                    pressedContainerColor = LumenColors.TextSecondary,
                    pressedContentColor = LumenColors.Background,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(PillPadding))
            }
        }
    }
}

private val PillPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
