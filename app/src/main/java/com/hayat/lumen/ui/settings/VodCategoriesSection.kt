package com.hayat.lumen.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hayat.lumen.LocalAppContainer
import com.hayat.lumen.data.db.VodCategorySetting
import com.hayat.lumen.data.db.VodKind
import com.hayat.lumen.data.vod.VodRepository
import com.hayat.lumen.ui.components.LumenButton
import com.hayat.lumen.ui.components.LumenListItem
import com.hayat.lumen.ui.components.LumenTextField
import com.hayat.lumen.ui.theme.LumenColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.NumberFormat

class VodCategoriesViewModel(private val vod: VodRepository, kind: VodKind) : ViewModel() {
    val query = MutableStateFlow("")

    val categories: StateFlow<List<VodCategorySetting>> = combine(vod.categorySettings(kind), query) { all, query ->
        val q = query.trim()
        if (q.isEmpty()) all else all.filter { it.name.contains(q, ignoreCase = true) || it.providerName.contains(q, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggle(c: VodCategorySetting) {
        viewModelScope.launch { vod.setCategoryHidden(c, !c.hidden) }
    }

    /** Shows or hides every category in the list as filtered now. */
    fun setAll(hidden: Boolean) {
        val list = categories.value.filter { it.hidden != hidden }
        viewModelScope.launch { list.forEach { vod.setCategoryHidden(it, hidden) } }
    }

    fun rename(c: VodCategorySetting, label: String) {
        viewModelScope.launch { vod.renameCategory(c, label) }
    }
}

/** Settings → Movie / Series categories: which rows the tab shows, and what they're called. */
@Composable
fun VodCategoriesSection(kind: VodKind, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val vm = viewModel(key = "vod-categories-$kind") { VodCategoriesViewModel(container.vod, kind) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val searching = query.isNotBlank()
    var renaming by remember { mutableStateOf<VodCategorySetting?>(null) }
    val tab = if (kind == VodKind.MOVIE) "Movies" else "Series"

    Column(modifier) {
        Text(
            "OK shows or hides a category on the $tab tab; hold OK to rename it.",
            style = MaterialTheme.typography.bodyLarge,
            color = LumenColors.TextSecondary,
        )
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LumenTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                label = "Search",
                placeholder = "e.g. Action, Kids",
                modifier = Modifier.weight(1f),
            )
            LumenButton(text = if (searching) "Show these" else "Show all", onClick = { vm.setAll(hidden = false) })
            LumenButton(text = if (searching) "Hide these" else "Hide all", onClick = { vm.setAll(hidden = true) })
        }
        Spacer(Modifier.height(16.dp))
        if (categories.isEmpty()) {
            Text(
                if (searching) "No categories match “${query.trim()}”."
                else "No ${tab.lowercase()} yet. They come from Xtream logins, and load in the background after a refresh.",
                style = MaterialTheme.typography.bodyMedium,
                color = LumenColors.TextTertiary,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(categories, key = { "${it.sourceId}/${it.categoryId}" }) { c ->
                LumenListItem(onClick = { vm.toggle(c) }, onLongClick = { renaming = c }) {
                    Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                        if (!c.hidden) Text("✓", style = MaterialTheme.typography.titleMedium, color = tickColor())
                    }
                    val color = LocalContentColor.current.let { if (c.hidden) it.copy(alpha = 0.5f) else it }
                    CategoryName(c.name, original = c.providerName.takeIf { it != c.name }, color = color, modifier = Modifier.weight(1f))
                    Text(NumberFormat.getIntegerInstance().format(c.itemCount), style = MaterialTheme.typography.labelMedium, color = color)
                }
            }
        }
    }

    renaming?.let { c ->
        RenameDialog(
            title = "Rename category",
            current = c.name,
            original = c.providerName,
            onSave = {
                vm.rename(c, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

/** A category's name, with the provider's original underneath when it was renamed. */
@Composable
fun CategoryName(name: String, original: String?, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(name, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (original != null) {
            Text(
                "Was “$original”",
                style = MaterialTheme.typography.bodySmall,
                color = color.copy(alpha = color.alpha * 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The accent blue reads on the dark row but not on the light focused one, so follow the content colour there. */
@Composable
fun tickColor() = if (LocalContentColor.current == LumenColors.Background) LumenColors.Background else LumenColors.Accent
