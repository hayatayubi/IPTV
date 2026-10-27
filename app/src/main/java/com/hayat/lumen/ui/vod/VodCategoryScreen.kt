package com.hayat.lumen.ui.vod

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hayat.lumen.LocalAppContainer
import com.hayat.lumen.data.db.Poster
import com.hayat.lumen.data.db.VodCategoryRow
import com.hayat.lumen.ui.components.LumenButton
import com.hayat.lumen.ui.components.PosterCard
import com.hayat.lumen.ui.components.formatCount
import com.hayat.lumen.ui.theme.LumenColors
import com.hayat.lumen.ui.theme.SafeArea

/** "See all": every poster in one category, in a grid. Also where a category can be hidden. */
@Composable
fun VodCategoryScreen(row: VodCategoryRow, onOpen: (Poster) -> Unit, onHidden: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { VodCategoryViewModel(container.vod, row) }
    val posters by vm.posters.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var lastFocus by rememberSaveable { mutableIntStateOf(0) }
    val restore = remember { FocusRequester() }

    Column(
        Modifier
            .fillMaxSize()
            .background(LumenColors.Background)
            .padding(horizontal = SafeArea.Horizontal, vertical = SafeArea.Vertical),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.name,
                style = MaterialTheme.typography.headlineMedium,
                color = LumenColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(16.dp))
            Text(formatCount(row.itemCount), style = MaterialTheme.typography.bodyLarge, color = LumenColors.TextTertiary)
            Spacer(Modifier.weight(1f))
            LumenButton(text = "Hide category", onClick = {
                vm.hide(row)
                Toast.makeText(context, "Hidden. Show it again at the bottom of the tab.", Toast.LENGTH_LONG).show()
                onHidden()
            })
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(130.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(vertical = 20.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            itemsIndexed(posters.orEmpty(), key = { _, p -> p.key }) { i, p ->
                PosterCard(
                    poster = p,
                    onClick = { onOpen(p) },
                    onLongClick = {
                        vm.toggleMyList(p) { added ->
                            Toast.makeText(context, if (added) "Added to My List" else "Removed from My List", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .then(if (i == lastFocus) Modifier.focusRequester(restore) else Modifier)
                        .onFocusChanged { if (it.isFocused) lastFocus = i },
                )
            }
        }
    }

    LaunchedEffect(posters != null) {
        if (posters.isNullOrEmpty()) return@LaunchedEffect
        repeat(10) {
            withFrameNanos { }
            if (runCatching { restore.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
}
