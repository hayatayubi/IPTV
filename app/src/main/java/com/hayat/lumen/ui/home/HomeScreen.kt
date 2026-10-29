package com.hayat.lumen.ui.home

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hayat.lumen.LocalAppContainer
import com.hayat.lumen.data.db.FavouriteRow
import com.hayat.lumen.data.epg.NowNext
import com.hayat.lumen.data.epg.Programme
import com.hayat.lumen.playback.PlayerHolder
import com.hayat.lumen.ui.components.ChannelArt
import com.hayat.lumen.ui.components.LumenButton
import com.hayat.lumen.ui.components.LumenCard
import com.hayat.lumen.ui.components.ProgrammeProgress
import com.hayat.lumen.ui.components.QualityTag
import com.hayat.lumen.ui.components.Tab
import com.hayat.lumen.ui.components.TopTabs
import com.hayat.lumen.ui.components.formatTime
import com.hayat.lumen.ui.components.timeRange
import com.hayat.lumen.ui.theme.LumenColors
import com.hayat.lumen.ui.theme.SafeArea
import com.hayat.lumen.util.ChannelNames
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The launch screen, Apple TV style: the focused favourite fills the top of the screen
 * (its name, what's on now and next, on a wash of the channel's colour), with the
 * favourites as a row of cards below. Resting on a card for a moment plays it muted
 * inside the card; OK opens it full screen (zapping through favourites), holding OK
 * offers Move (the arrows carry the card, OK or Back puts it down) and Remove.
 * "All channels" opens Live, "TV guide" the guide grid. The tabs at the top open Movies
 * and Series. When a newer Lumen is on GitHub, an "Update to …" button appears.
 */
@Composable
fun HomeScreen(
    onPlay: (channelId: Long) -> Unit,
    onBrowse: () -> Unit,
    onOpenGuide: () -> Unit,
    onTab: (Tab) -> Unit,
) {
    val container = LocalAppContainer.current
    val update by container.updates.available.collectAsStateWithLifecycle()
    // Look for a newer Lumen when Home shows (at most every few hours; the TV keeps apps alive for days).
    LaunchedEffect(Unit) { container.updates.checkInBackground() }
    val vm = viewModel { HomeViewModel(container.live, container.epg) }
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val guide by vm.nowNext.collectAsStateWithLifecycle()
    val saved = favourites
    if (saved == null) {
        Box(Modifier.fillMaxSize().background(LumenColors.Background))
        return
    }

    // Moving a favourite: [moving] is the card being carried, [order] the order on screen until
    // it's saved. (Room's list catches up a moment after saving; until then we show ours.)
    var menuFor by remember { mutableStateOf<FavouriteRow?>(null) }
    var moving by remember { mutableStateOf<String?>(null) }
    var order by remember { mutableStateOf<List<FavouriteRow>?>(null) }
    LaunchedEffect(saved) { if (moving == null) order = null }
    val list = order ?: saved
    val scope = rememberCoroutineScope()
    // Set when OK places a card, so the same press's key-up doesn't also open the channel.
    var swallowOkUp by remember { mutableStateOf(false) }

    fun drop() {
        val moved = order
        moving = null
        if (moved != null && moved != saved) vm.saveOrder(moved) else order = null
    }
    BackHandler(enabled = moving != null) { drop() }

    var focusedId by remember { mutableStateOf<Long?>(null) }
    val preview = rememberLivePreview(focusedId, vm::streamUrl)
    // Each card reports its logo's colour; the focused one tints the screen behind the hero.
    val tints = remember { mutableStateMapOf<Long, Color>() }

    val gridState = rememberLazyGridState()
    val cardFocus = remember { FocusRequester() }
    val buttonFocus = remember { FocusRequester() }
    // Coming back from the player: focus the card of the channel that was playing.
    val returnTo = remember { container.lastChannelId.value }
    var initialFocusDone by remember { mutableStateOf(false) }
    val focusIndex = list.indexOfFirst { it.id == returnTo }.coerceAtLeast(0)

    // The hero keeps showing the last focused card while focus is up on the buttons.
    var heroId by remember { mutableStateOf(returnTo) }
    LaunchedEffect(focusedId) { focusedId?.let { heroId = it } }
    val hero = list.firstOrNull { it.id == heroId } ?: list.firstOrNull()
    val wash by animateColorAsState(
        hero?.let { tints[it.id] } ?: LumenColors.Background,
        animationSpec = tween(600),
        label = "wash",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(LumenColors.Background)
            .drawBehind {
                // A soft glow of the channel's colour from the top left, fading into the background.
                drawRect(
                    Brush.radialGradient(
                        listOf(wash.copy(alpha = 0.55f), wash.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.2f, 0f),
                        radius = size.width * 0.75f,
                    ),
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = SafeArea.Horizontal, vertical = SafeArea.Vertical),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TopTabs(selected = Tab.LIVE, onSelect = onTab)
                // A weighted child is measured last in a Row (like a low compression-resistance
                // priority in Auto Layout), so the buttons always get their full width first.
                Spacer(Modifier.weight(1f))
                LumenButton(text = "All channels", onClick = onBrowse, modifier = Modifier.focusRequester(buttonFocus))
                Spacer(Modifier.width(12.dp))
                LumenButton(text = "TV guide", onClick = onOpenGuide)
            }

            if (list.isEmpty()) {
                Spacer(Modifier.height(96.dp))
                Text("No favourites yet", style = MaterialTheme.typography.displaySmall, color = LumenColors.TextPrimary)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Open All channels, then hold OK on a channel to add it here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = LumenColors.TextSecondary,
                )
            } else {
                Hero(hero, hero?.let { guide[it.id] }, Modifier.weight(1f).fillMaxWidth())
                // The hint sits with the cards it's about (it used to share the top line, where an
                // "Update to …" button ran over it).
                val ready = update
                Text(
                    when {
                        moving != null -> "Move with the arrows, then press OK"
                        ready != null -> "Lumen ${ready.version} is ready. Open Settings to update."
                        else -> "Hold OK on a favourite to move or remove it"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ready != null && moving == null) LumenColors.Accent else LumenColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = GridPadding),
                )
                // Four across, so up to eight favourites show at once; more than that scroll.
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cardWidth = (maxWidth - GridPadding * 2 - GridSpacing * (GridColumns - 1)) / GridColumns
                    val twoRows = cardWidth * 9 / 16 * 2 + GridSpacing + GridPadding * 2
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(GridColumns),
                        state = gridState,
                        // focusRestorer: coming back down from the buttons lands on the last focused card.
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = twoRows)
                            .focusRestorer()
                            // While a card is being moved, the arrows move it instead of focus.
                            // (onPreviewKeyEvent sees keys before the focused card does, like
                            // overriding pressesBegan on a parent view in UIKit.)
                            .onPreviewKeyEvent { event ->
                                val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter
                                if (isOk && event.type == KeyEventType.KeyUp && swallowOkUp) {
                                    swallowOkUp = false
                                    return@onPreviewKeyEvent true
                                }
                                val key = moving ?: return@onPreviewKeyEvent false
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent isOk
                                if (isOk) {
                                    swallowOkUp = true
                                    drop()
                                    return@onPreviewKeyEvent true
                                }
                                val direction = when (event.key) {
                                    Key.DirectionLeft -> GridMove.Direction.LEFT
                                    Key.DirectionRight -> GridMove.Direction.RIGHT
                                    Key.DirectionUp -> GridMove.Direction.UP
                                    Key.DirectionDown -> GridMove.Direction.DOWN
                                    else -> return@onPreviewKeyEvent false
                                }
                                val from = list.indexOfFirst { favouriteKey(it) == key }
                                val to = GridMove.target(from, list.size, GridColumns, direction)
                                if (from >= 0 && to != null) {
                                    order = GridMove.move(list, from, to)
                                    scope.launch { gridState.keepRowVisible(to) }
                                }
                                true // at an edge: stay put rather than letting focus leave the grid
                            },
                        // Room around the cards for their focus lift and shadow.
                        contentPadding = PaddingValues(GridPadding),
                        horizontalArrangement = Arrangement.spacedBy(GridSpacing),
                        verticalArrangement = Arrangement.spacedBy(GridSpacing),
                    ) {
                        itemsIndexed(list, key = { _, c -> favouriteKey(c) }) { index, channel ->
                            val previewing = preview.channelId == channel.id
                            FavouriteCard(
                                channel = channel,
                                now = guide[channel.id]?.now,
                                player = if (previewing) preview.player else null,
                                live = previewing && preview.showing,
                                moving = moving == favouriteKey(channel),
                                onTint = { tints[channel.id] = it },
                                onClick = {
                                    preview.handOver()
                                    container.lastChannelId.value = channel.id
                                    onPlay(channel.id)
                                },
                                onLongClick = { if (moving == null) menuFor = channel },
                                modifier = Modifier
                                    .then(if (index == focusIndex) Modifier.focusRequester(cardFocus) else Modifier)
                                    .onFocusChanged {
                                        if (it.isFocused) focusedId = channel.id
                                        else if (focusedId == channel.id) focusedId = null
                                    },
                            )
                        }
                    }
                }
            }
        }
    }

    menuFor?.let { channel ->
        FavouriteMenu(
            channel = channel,
            onMove = {
                menuFor = null
                order = list
                moving = favouriteKey(channel)
            },
            onRemove = {
                menuFor = null
                vm.remove(channel)
            },
            onDismiss = { menuFor = null },
        )
    }

    // First focus: the card we came back from (else the first), or the button if there are none.
    LaunchedEffect(list.isEmpty()) {
        if (initialFocusDone) return@LaunchedEffect
        if (list.isNotEmpty()) {
            gridState.scrollToItem(focusIndex)
            withFrameNanos { } // let the scrolled-to card compose before focusing it
            initialFocusDone = runCatching { cardFocus.requestFocus() }.isSuccess
        }
        if (!initialFocusDone) initialFocusDone = runCatching { buttonFocus.requestFocus() }.isSuccess
    }
}

/** The focused channel, large: its name, quality tag, and what's on now and next. */
@Composable
private fun Hero(channel: FavouriteRow?, nowNext: NowNext?, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.BottomStart) {
        // Crossfade so moving along the row feels like turning pages, not text flicker.
        Crossfade(channel to nowNext, animationSpec = tween(250), label = "hero") { (shown, info) ->
            if (shown == null) return@Crossfade
            val name = remember(shown.name) { ChannelNames.display(shown.name) }
            Column(Modifier.widthIn(max = 720.dp).padding(start = 8.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        name.title,
                        style = MaterialTheme.typography.displaySmall,
                        color = LumenColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    name.tags.forEach { QualityTag(it) }
                }
                val now = info?.now
                if (now != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            now.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = LumenColors.TextPrimary.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(now.timeRange(), style = MaterialTheme.typography.bodyLarge, color = LumenColors.TextSecondary)
                    }
                    Spacer(Modifier.height(10.dp))
                    ProgrammeProgress(
                        now.progress(System.currentTimeMillis()),
                        color = LumenColors.TextPrimary,
                        modifier = Modifier.width(360.dp),
                        height = 4.dp,
                    )
                }
                info?.next?.let { next ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Next   ${formatTime(next.start)}   ${next.title}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = LumenColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private const val GridColumns = 4

private fun favouriteKey(c: FavouriteRow) = "${c.sourceId}/${c.stableKey}"

/** Scrolls just enough that the row holding [index] is on screen (two rows show at a time). */
private suspend fun LazyGridState.keepRowVisible(index: Int) {
    val visible = layoutInfo.visibleItemsInfo
    val first = visible.firstOrNull()?.index ?: return
    val last = visible.last().index
    val rowStart = index - index % GridColumns
    when {
        index < first -> animateScrollToItem(rowStart)
        index > last -> animateScrollToItem((rowStart - GridColumns).coerceAtLeast(0))
    }
}

/** Hold OK on a favourite: move it, or take it off Home. */
@Composable
private fun FavouriteMenu(channel: FavouriteRow, onMove: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    // A Dialog is its own window: it keeps focus inside itself and Back closes it,
    // like a SwiftUI .confirmationDialog.
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(420.dp)
                .background(LumenColors.Surface, RoundedCornerShape(24.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                ChannelNames.display(channel.name).title,
                style = MaterialTheme.typography.headlineSmall,
                color = LumenColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(24.dp))
            LumenButton(text = "Move", onClick = onMove, modifier = Modifier.fillMaxWidth().focusRequester(first))
            Spacer(Modifier.height(12.dp))
            LumenButton(text = "Remove from favourites", onClick = onRemove, modifier = Modifier.fillMaxWidth())
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
private val GridSpacing = 24.dp
private val GridPadding = 16.dp

/**
 * One favourite: its artwork edge to edge (the name and what's on are in the hero above),
 * with a progress line for the current programme along the bottom.
 */
@OptIn(UnstableApi::class)
@Composable
private fun FavouriteCard(
    channel: FavouriteRow,
    now: Programme?,
    player: ExoPlayer?,
    live: Boolean,
    moving: Boolean,
    onTint: (Color) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Logo fades out once the preview's first frame is on screen, revealing the video under it.
    val artAlpha by animateFloatAsState(if (live) 0f else 1f, tween(400), label = "art")
    LumenCard(onClick = onClick, onLongClick = onLongClick, modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
        Box(Modifier.fillMaxSize()) {
            if (player != null) {
                // TextureView, not the default SurfaceView: a SurfaceView ignores the card's
                // focus scale and rounded clip. Fine for one small preview at a time.
                ContentFrame(
                    player = player,
                    modifier = Modifier.fillMaxSize(),
                    surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                    contentScale = ContentScale.Crop,
                )
            }
            if (artAlpha > 0f) {
                ChannelArt(
                    name = channel.name,
                    logoUrl = channel.logoUrl,
                    onTint = onTint,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = artAlpha },
                )
            }
            if (live) LiveBadge(Modifier.align(Alignment.TopStart).padding(8.dp))
            // Picked up: an accent outline, so it's clear the arrows now carry this card.
            if (moving) {
                Box(
                    Modifier
                        .matchParentSize()
                        .border(3.dp, LumenColors.Accent, RoundedCornerShape(16.dp)),
                )
            }
            // How far through the current programme, along the bottom edge like Up Next.
            if (now != null) {
                ProgrammeProgress(
                    now.progress(System.currentTimeMillis()),
                    color = Color.White,
                    height = 3.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    Text(
        "LIVE",
        style = MaterialTheme.typography.labelMedium,
        color = LumenColors.TextPrimary,
        modifier = modifier
            .background(LumenColors.Accent, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** What the focused card's muted preview is doing. Compose re-reads these when they change. */
@Stable
private class LivePreview {
    var channelId by mutableStateOf<Long?>(null)
    var player by mutableStateOf<ExoPlayer?>(null)
    /** True once the first video frame has rendered. */
    var showing by mutableStateOf(false)
    /** Set when OK opens the player, so the stream keeps running for it. */
    var handedOver = false

    fun handOver() {
        handedOver = true
    }

    fun clear() {
        channelId = null
        player = null
        showing = false
    }
}

/**
 * Plays [focusedId] muted, [PREVIEW_DELAY_MS] after focus settles on it. Uses the app's one
 * ExoPlayer, so there is only ever one decoder and one provider connection: moving focus stops
 * the old stream at once, and opening the previewed channel reuses its stream.
 */
@Composable
private fun rememberLivePreview(focusedId: Long?, streamUrl: suspend (Long) -> String?): LivePreview {
    val container = LocalAppContainer.current
    val state = remember { LivePreview() }
    val owner = remember { Any() }
    var started by remember { mutableStateOf(false) }

    fun stop() {
        if (!state.handedOver) container.player.stop(owner)
        state.clear()
    }

    LifecycleStartEffect(Unit) {
        started = true
        state.handedOver = false
        onStopOrDispose {
            started = false
            stop()
        }
    }

    LaunchedEffect(focusedId, started) {
        if (state.player != null) stop()
        if (!started || focusedId == null || state.handedOver) return@LaunchedEffect
        delay(PREVIEW_DELAY_MS)
        val url = streamUrl(focusedId) ?: return@LaunchedEffect
        val p = container.player.acquire(owner)
        p.volume = 0f
        p.setMediaItem(PlayerHolder.mediaItem(url))
        p.prepare()
        p.playWhenReady = true
        state.channelId = focusedId
        state.player = p
    }

    DisposableEffect(state.player) {
        val p = state.player ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                state.showing = true
            }

            override fun onPlayerError(error: PlaybackException) {
                // Leave the logo up; a preview isn't worth retrying.
                state.showing = false
            }
        }
        p.addListener(listener)
        onDispose { p.removeListener(listener) }
    }
    return state
}

private const val PREVIEW_DELAY_MS = 1_200L
