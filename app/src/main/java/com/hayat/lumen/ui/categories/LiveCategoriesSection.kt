package com.hayat.lumen.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
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
import com.hayat.lumen.data.LiveRepository
import com.hayat.lumen.data.db.CategoryRow
import com.hayat.lumen.data.db.SourceEntity
import com.hayat.lumen.data.epg.EpgRepository
import com.hayat.lumen.ui.components.LumenButton
import com.hayat.lumen.ui.components.LumenListItem
import com.hayat.lumen.ui.components.LumenTextField
import com.hayat.lumen.ui.theme.LumenColors
import com.hayat.lumen.ui.settings.RenameDialog
import com.hayat.lumen.ui.settings.CategoryName
import com.hayat.lumen.ui.settings.tickColor
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.NumberFormat

/** One playlist and its categories, filtered by the search. [categories] is empty while the playlist is hidden. */
data class CategorySection(
    val source: SourceEntity,
    val shown: Int,
    val total: Int,
    val categories: List<CategoryRow>,
)

class ManageCategoriesViewModel(private val live: LiveRepository, private val epg: EpgRepository) : ViewModel() {
    val query = MutableStateFlow("")

    private val all: StateFlow<List<CategoryRow>> =
        live.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val sources: StateFlow<List<SourceEntity>> =
        live.sources.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sections: StateFlow<List<CategorySection>> = combine(sources, all, query) { sources, all, query ->
        val bySource = all.groupBy { it.sourceId }
        val q = query.trim()
        sources.map { source ->
            val rows = bySource[source.id].orEmpty()
            CategorySection(
                source = source,
                shown = rows.count { !it.hidden },
                total = rows.size,
                categories = if (source.hidden) emptyList() else rows.filter {
                    q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.name.contains(q, ignoreCase = true)
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // What was hidden when the screen opened, to know on leaving whether the guide needs those channels.
    private var hiddenAtStart: Set<Pair<Long, String>>? = null
    private var hiddenSourcesAtStart: Set<Long>? = null

    init {
        viewModelScope.launch {
            combine(sources, all) { s, a -> s to a }.collect { (s, a) ->
                if (hiddenAtStart == null && s.isNotEmpty() && a.isNotEmpty()) {
                    hiddenAtStart = a.filter { it.hidden }.map { it.sourceId to it.name }.toSet()
                    hiddenSourcesAtStart = s.filter { it.hidden }.map { it.id }.toSet()
                }
            }
        }
    }

    fun toggle(row: CategoryRow) {
        viewModelScope.launch {
            if (row.hidden) live.showGroups(row.sourceId, listOf(row.name)) else live.hideGroups(row.sourceId, listOf(row.name))
        }
    }

    fun rename(row: CategoryRow, label: String) {
        viewModelScope.launch { live.renameGroup(row.sourceId, row.name, label) }
    }

    fun toggleSource(source: SourceEntity) {
        viewModelScope.launch { live.setSourceHidden(source.id, !source.hidden) }
    }

    /** Shows or hides every category in the list as filtered now (all of them when there's no search). */
    fun setAll(hidden: Boolean) {
        val sections = sections.value
        viewModelScope.launch {
            for (section in sections) {
                val names = section.categories.filter { it.hidden != hidden }.map { it.name }
                if (names.isEmpty()) continue
                if (hidden) live.hideGroups(section.source.id, names) else live.showGroups(section.source.id, names)
            }
        }
    }

    /**
     * Hidden categories are left out of guide downloads. If any came back (or a whole playlist did),
     * reload that playlist's guide once, on leaving, rather than on every tick.
     */
    override fun onCleared() {
        val hiddenBefore = hiddenAtStart ?: return
        val hiddenNow = all.value.filter { it.hidden }.map { it.sourceId to it.name }.toSet()
        val sourcesBefore = hiddenSourcesAtStart.orEmpty()
        val needGuide = (hiddenBefore - hiddenNow).map { it.first }.toSet() +
            sources.value.filter { !it.hidden && it.id in sourcesBefore }.map { it.id }
        needGuide.forEach(epg::updateInBackground) // runs on the app scope, so it outlives this screen
    }
}

/**
 * Settings → Live categories: tick which of the provider's categories show in Live, rename them
 * (hold OK), or hide a whole playlist. Search, then "Hide all" / "Show all" acts on just the
 * matches: handy for providers with hundreds of categories.
 */
@Composable
fun LiveCategoriesSection(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val vm = viewModel { ManageCategoriesViewModel(container.live, container.epg) }
    val sections by vm.sections.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val searching = query.isNotBlank()
    var renaming by remember { mutableStateOf<CategoryRow?>(null) }

    Column(modifier) {
        Text(
            "OK shows or hides a category in Live; hold OK to rename it. OK on a playlist hides all of it.",
            style = MaterialTheme.typography.bodyLarge,
            color = LumenColors.TextSecondary,
        )
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LumenTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                label = "Search",
                placeholder = "e.g. Sports, UK, Arabic",
                modifier = Modifier.weight(1f),
            )
            LumenButton(text = if (searching) "Show these" else "Show all", onClick = { vm.setAll(hidden = false) })
            LumenButton(text = if (searching) "Hide these" else "Hide all", onClick = { vm.setAll(hidden = true) })
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            sections.forEachIndexed { index, section ->
                item(key = "source/${section.source.id}") {
                    SourceRow(
                        section = section,
                        onClick = { vm.toggleSource(section.source) },
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 16.dp),
                    )
                }
                items(section.categories, key = { "group/${it.sourceId}/${it.name}" }) { row ->
                    CategoryItem(row = row, onClick = { vm.toggle(row) }, onLongClick = { renaming = row })
                }
                if (searching && !section.source.hidden && section.categories.isEmpty()) {
                    item(key = "empty/${section.source.id}") {
                        Text(
                            "No categories match “${query.trim()}”.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LumenColors.TextTertiary,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }

    renaming?.let { row ->
        RenameDialog(
            title = "Rename category",
            current = row.label,
            original = row.name,
            onSave = {
                vm.rename(row, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

/** The playlist row: OK hides or shows the whole playlist. */
@Composable
private fun SourceRow(section: CategorySection, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val count = NumberFormat.getIntegerInstance()
    LumenListItem(onClick = onClick, selected = true, modifier = modifier, minHeight = 64.dp) {
        Column(Modifier.weight(1f)) {
            Text(
                section.source.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (section.source.hidden) "Playlist hidden. Press OK to show it."
                else "${count.format(section.shown)} of ${count.format(section.total)} categories shown",
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.7f),
            )
        }
        Text(
            if (section.source.hidden) "Hidden" else "Shown",
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** A category row: a tick when it shows in Live. OK toggles it, hold OK renames it. */
@Composable
private fun CategoryItem(row: CategoryRow, onClick: () -> Unit, onLongClick: () -> Unit) {
    LumenListItem(onClick = onClick, onLongClick = onLongClick) {
        // Fixed-width slot so names line up whether or not the tick is there.
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            if (!row.hidden) Text("✓", style = MaterialTheme.typography.titleMedium, color = tickColor())
        }
        // Hidden rows are dimmed; LocalContentColor still flips to dark on the focused (light) row.
        val color = LocalContentColor.current.let { if (row.hidden) it.copy(alpha = 0.5f) else it }
        CategoryName(row.label, original = row.name.takeIf { it != row.label }, color = color, modifier = Modifier.weight(1f))
        Text(NumberFormat.getIntegerInstance().format(row.channelCount), style = MaterialTheme.typography.labelMedium, color = color)
    }
}
