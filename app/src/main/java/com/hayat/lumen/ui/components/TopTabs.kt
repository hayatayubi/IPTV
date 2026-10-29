package com.hayat.lumen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hayat.lumen.LocalAppContainer
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

/** [SETTINGS] isn't a tab of its own: it opens the Settings screen on top. */
enum class Tab(val label: String) { LIVE("Live TV"), MOVIES("Movies"), SERIES("Series"), SETTINGS("Settings") }

/**
 * The Apple TV app's top bar: Live TV · Movies · Series · Settings. The open tab keeps a soft fill;
 * the focused one turns light like every other focusable in Lumen. Settings gets a blue dot
 * while an update is waiting.
 */
@Composable
fun TopTabs(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val update by LocalAppContainer.current.updates.available.collectAsStateWithLifecycle()
    Pills(
        labels = Tab.entries.map { it.label },
        selected = selected.ordinal,
        onSelect = { if (it != selected.ordinal) onSelect(Tab.entries[it]) },
        modifier = modifier,
        dots = if (update != null) setOf(Tab.SETTINGS.ordinal) else emptySet(),
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
    dots: Set<Int> = emptySet(),
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
                Row(Modifier.padding(PillPadding), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    if (index in dots) {
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(8.dp).background(LumenColors.Accent, CircleShape))
                    }
                }
            }
        }
    }
}

private val PillPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
