package com.hayat.lumen.ui.vod

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.BorderStroke
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.hayat.lumen.LocalAppContainer
import com.hayat.lumen.data.db.Poster
import com.hayat.lumen.data.db.VodCategoryRow
import com.hayat.lumen.data.db.VodKind
import com.hayat.lumen.data.vod.VodSync
import com.hayat.lumen.ui.components.LumenButton
import com.hayat.lumen.ui.components.QualityTag
import com.hayat.lumen.util.CategoryNames
import com.hayat.lumen.ui.components.PosterCard
import com.hayat.lumen.ui.components.SeeAllCard
import com.hayat.lumen.ui.components.Tab
import com.hayat.lumen.ui.components.TopTabs
import com.hayat.lumen.ui.components.Ambient
import com.hayat.lumen.ui.components.formatCount
import com.hayat.lumen.ui.theme.LumenColors
import com.hayat.lumen.ui.theme.SafeArea

/**
 * The Movies or Series tab, like the Apple TV app: the focused title fills the top of the
 * screen (backdrop, title, year, genre, plot), with rows of posters below. Continue watching
 * and My List come first, then the provider's categories. Hold OK on a poster to add it to
 * My List (or, in Continue watching, to remove it from there).
 */
@Composable
fun VodBrowseScreen(
    kind: VodKind,
    onTab: (Tab) -> Unit,
    onOpen: (Poster) -> Unit,
    onSeeAll: (VodCategoryRow) -> Unit,
    onSearch: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm = viewModel(key = kind.name) { VodBrowseViewModel(container.vod, kind) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val continueWatching by vm.continueWatching.collectAsStateWithLifecycle()
    val myList by vm.myList.collectAsStateWithLifecycle()
    val hiddenCount by vm.hiddenCount.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val hero by vm.hero.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val noun = if (kind == VodKind.MOVIE) "films" else "series"

    // Focus comes back to the poster you opened. Keys are "<row>|<poster>".
    var lastFocus by rememberSaveable { mutableStateOf<String?>(null) }
    val restore = remember { FocusRequester() }
    val first = remember { FocusRequester() }
    val tabsFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    fun toggleMyList(poster: Poster) = vm.toggleMyList(poster) { added ->
        Toast.makeText(context, if (added) "Added to My List" else "Removed from My List", Toast.LENGTH_SHORT).show()
    }

    @Composable
    fun Card(rowKey: String, poster: Poster, isFirst: Boolean, onLongClick: () -> Unit) {
        val key = "$rowKey|${poster.key}"
        PosterCard(
            poster = poster,
            onClick = { onOpen(poster) },
            onLongClick = onLongClick,
            modifier = Modifier
                .then(if (key == lastFocus) Modifier.focusRequester(restore) else Modifier)
                .then(if (isFirst) Modifier.focusRequester(first) else Modifier)
                .onFocusChanged {
                    if (it.isFocused) {
                        lastFocus = key
                        vm.focus(poster)
                    }
                },
        )
    }

    Ambient {
        // Big, like the Apple TV app: the focused title's backdrop fills most of the screen.
        Backdrop(hero, widthFraction = 1f, heightFraction = 0.85f, leftFade = 0.55f)
        Column(Modifier.fillMaxSize().padding(horizontal = SafeArea.Horizontal, vertical = SafeArea.Vertical)) {
            TopTabs(
                selected = if (kind == VodKind.MOVIE) Tab.MOVIES else Tab.SERIES,
                onSelect = { if (it == Tab.SEARCH) onSearch() else onTab(it) },
                modifier = Modifier.focusRequester(tabsFocus),
            ) {
                val status = when (val s = sync) {
                    is VodSync.Running -> "Updating films and series… ${formatCount(s.movies + s.series)}"
                    is VodSync.Failed -> s.message
                    VodSync.Idle -> ""
                }
                Text(status, style = MaterialTheme.typography.bodySmall, color = LumenColors.TextTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            val rows = categories
            when {
                rows == null -> Spacer(Modifier.weight(1f))
                rows.isEmpty() && continueWatching.isEmpty() && myList.isEmpty() -> {
                    Spacer(Modifier.height(96.dp))
                    Text(
                        if (sync is VodSync.Running) "Loading $noun…" else "No $noun yet",
                        style = MaterialTheme.typography.displaySmall,
                        color = LumenColors.TextPrimary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        when {
                            sync is VodSync.Running -> "The first load takes a minute. They'll appear here when it's done."
                            hiddenCount > 0 -> "Every category is hidden."
                            else -> "Films and series come from an Xtream login. Add one as a playlist, then come back here."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = LumenColors.TextSecondary,
                    )
                    if (hiddenCount > 0) {
                        Spacer(Modifier.height(24.dp))
                        LumenButton(text = "Show hidden categories", onClick = vm::showHidden)
                    }
                }
                else -> {
                    HeroText(hero, Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp), plotLines = 2)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().height(RowsHeight),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Every category as a small pill, a quick way to "See all" of one.
                        if (rows.size > 1 && showCategoryChips) {
                            item(key = "chips") {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth().focusRestorer(),
                                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(rows, key = { "${it.sourceId}/${it.categoryId}" }) { row ->
                                        CategoryChip(row.name, row.itemCount, onClick = { onSeeAll(row) })
                                    }
                                }
                            }
                        }
                        if (continueWatching.isNotEmpty()) {
                            item(key = "continue") {
                                PosterRow("Continue watching", "Hold OK to remove") {
                                    itemsIndexed(continueWatching, key = { _, p -> p.key }) { i, p ->
                                        Card("continue", p, isFirst = i == 0) { vm.removeFromContinue(p) }
                                    }
                                }
                            }
                        }
                        if (myList.isNotEmpty()) {
                            item(key = "mylist") {
                                PosterRow("My List", null) {
                                    itemsIndexed(myList, key = { _, p -> p.key }) { i, p ->
                                        Card("mylist", p, isFirst = i == 0 && continueWatching.isEmpty()) { toggleMyList(p) }
                                    }
                                }
                            }
                        }
                        val categoriesFirst = continueWatching.isEmpty() && myList.isEmpty()
                        itemsIndexed(rows, key = { _, r -> "${r.sourceId}/${r.categoryId}" }) { rowIndex, row ->
                            val rowKey = "${row.sourceId}/${row.categoryId}"
                            val posters by produceState<List<Poster>>(emptyList(), row.sourceId, row.categoryId, row.generation) {
                                value = vm.posters(row, fillsHero = rowIndex == 0)
                            }
                            val name = remember(row.name) { CategoryNames.display(row.name) }
                            PosterRow(name.title, (name.tags + formatCount(row.itemCount)).joinToString("  ·  ")) {
                                itemsIndexed(posters, key = { _, p -> p.key }) { i, p ->
                                    Card(rowKey, p, isFirst = categoriesFirst && rowIndex == 0 && i == 0) { toggleMyList(p) }
                                }
                                if (posters.isNotEmpty()) item(key = "all") { SeeAllCard(row.itemCount, onClick = { onSeeAll(row) }) }
                            }
                        }
                        if (hiddenCount > 0) {
                            item(key = "hidden") {
                                Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    LumenButton(
                                        text = if (hiddenCount == 1) "Show 1 hidden category" else "Show $hiddenCount hidden categories",
                                        onClick = vm::showHidden,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // First focus: the poster you came back from, else the first poster, else the tabs.
    var focusDone by remember { mutableStateOf(false) }
    LaunchedEffect(categories != null) {
        if (focusDone || categories == null) return@LaunchedEffect
        // A category row's posters arrive a few frames after the row itself.
        repeat(20) {
            withFrameNanos { }
            val target = if (lastFocus != null) restore else first
            if (runCatching { target.requestFocus() }.isSuccess) {
                focusDone = true
                return@LaunchedEffect
            }
        }
        focusDone = runCatching { tabsFocus.requestFocus() }.isSuccess
    }
}

/** A titled row of posters. focusRestorer: coming back to the row lands on the poster you left. */
@Composable
private fun PosterRow(
    title: String,
    detail: String?,
    content: LazyListScope.() -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = LumenColors.TextPrimary, maxLines = 1)
            if (detail != null) {
                Spacer(Modifier.width(12.dp))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = LumenColors.TextTertiary, maxLines = 1)
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth().focusRestorer(),
            // Room for the focused card's lift and shadow.
            contentPadding = PaddingValues(vertical = 14.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}

/** One row plus the next row's title peeking in, so you can tell there's more below. */
private val RowsHeight = 290.dp

/** Whether the category pills show. A switch only while Hayat compares the tab with and without them. */
internal var showCategoryChips = true

/** A category as a glass pill: its tidied name, its quality tag and how many titles it has. */
@Composable
private fun CategoryChip(name: String, count: Int, onClick: () -> Unit) {
    val display = remember(name) { CategoryNames.display(name) }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = LumenColors.Glass,
            contentColor = LumenColors.TextPrimary,
            focusedContainerColor = LumenColors.TextPrimary,
            focusedContentColor = LumenColors.Background,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, LumenColors.GlassEdge), shape = RoundedCornerShape(50)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(display.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            display.tags.forEach { QualityTag(it, color = LocalContentColor.current.copy(alpha = 0.7f)) }
            Text(formatCount(count), style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.55f))
        }
    }
}
