package com.hayat.lumen.ui.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hayat.lumen.LocalAppContainer
import com.hayat.lumen.data.db.Poster
import com.hayat.lumen.data.vod.VodRepository
import com.hayat.lumen.ui.components.LumenTextField
import com.hayat.lumen.ui.components.PosterCard
import com.hayat.lumen.ui.theme.LumenColors
import com.hayat.lumen.ui.theme.SafeArea
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

data class SearchResults(val query: String, val movies: List<Poster>, val series: List<Poster>)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class VodSearchViewModel(private val vod: VodRepository) : ViewModel() {
    val query = MutableStateFlow("")

    /** Searches a moment after typing stops. Matches every word, in order, anywhere in the title. */
    val results: StateFlow<SearchResults?> = query
        .debounce(300)
        .mapLatest { q ->
            if (q.isBlank()) null
            else vod.search(q).let { (movies, series) -> SearchResults(q, movies, series) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** One search across films and series. Press OK on the field to type. */
@Composable
fun VodSearchScreen(onOpen: (Poster) -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { VodSearchViewModel(container.vod) }
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val fieldFocus = remember { FocusRequester() }

    Column(
        Modifier
            .fillMaxSize()
            .background(LumenColors.Background)
            .padding(horizontal = SafeArea.Horizontal, vertical = SafeArea.Vertical),
    ) {
        LumenTextField(
            value = query,
            onValueChange = { vm.query.value = it },
            label = "Search films and series",
            placeholder = "Press OK to type a title",
            modifier = Modifier.widthIn(max = 640.dp).focusRequester(fieldFocus),
        )
        Spacer(Modifier.height(16.dp))
        val r = results
        if (r != null && r.movies.isEmpty() && r.series.isEmpty()) {
            Text("Nothing found for “${r.query}”.", style = MaterialTheme.typography.bodyLarge, color = LumenColors.TextSecondary)
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (r != null && r.movies.isNotEmpty()) item(key = "movies") { ResultRow("Films", r.movies, onOpen) }
            if (r != null && r.series.isNotEmpty()) item(key = "series") { ResultRow("Series", r.series, onOpen) }
        }
    }

    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
}

@Composable
private fun ResultRow(title: String, posters: List<Poster>, onOpen: (Poster) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, color = LumenColors.TextPrimary)
        LazyRow(
            modifier = Modifier.fillMaxWidth().focusRestorer(),
            contentPadding = PaddingValues(vertical = 14.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(posters, key = { it.key }) { p -> PosterCard(p, onClick = { onOpen(p) }) }
        }
    }
}
