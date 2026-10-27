package com.hayat.lumen.ui.home

import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import coil3.compose.AsyncImage
import com.hayat.lumen.LocalAppContainer
import com.hayat.lumen.data.db.FavouriteRow
import com.hayat.lumen.data.epg.Programme
import com.hayat.lumen.playback.PlayerHolder
import com.hayat.lumen.ui.components.LumenButton
import com.hayat.lumen.ui.components.LumenCard
import com.hayat.lumen.ui.components.Monogram
import com.hayat.lumen.ui.components.ProgrammeProgress
import com.hayat.lumen.ui.theme.LumenColors
import com.hayat.lumen.ui.theme.SafeArea
import kotlinx.coroutines.delay

/**
 * The launch screen: favourite channels as large cards. Resting on a card for a moment
 * plays it muted inside the card; OK opens it full screen (zapping through favourites),
 * holding OK removes it. "All channels" opens Live, "TV guide" the guide grid.
 */
@Composable
fun HomeScreen(onPlay: (channelId: Long) -> Unit, onBrowse: () -> Unit, onOpenGuide: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { HomeViewModel(container.live, container.epg) }
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val guide by vm.nowNext.collectAsStateWithLifecycle()
    val list = favourites
    if (list == null) {
        Box(Modifier.fillMaxSize().background(LumenColors.Background))
        return
    }

    var focusedId by remember { mutableStateOf<Long?>(null) }
    val preview = rememberLivePreview(focusedId, vm::streamUrl)

    val rowState = rememberLazyListState()
    val cardFocus = remember { FocusRequester() }
    val buttonFocus = remember { FocusRequester() }
    // Coming back from the player: focus the card of the channel that was playing.
    val returnTo = remember { container.lastChannelId.value }
    var initialFocusDone by remember { mutableStateOf(false) }
    val focusIndex = list.indexOfFirst { it.id == returnTo }.coerceAtLeast(0)

    Column(
        Modifier
            .fillMaxSize()
            .background(LumenColors.Background)
            .padding(horizontal = SafeArea.Horizontal, vertical = SafeArea.Vertical),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Favourites",
                style = MaterialTheme.typography.headlineLarge,
                color = LumenColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            LumenButton(text = "All channels", onClick = onBrowse, modifier = Modifier.focusRequester(buttonFocus))
            Spacer(Modifier.width(12.dp))
            LumenButton(text = "TV guide", onClick = onOpenGuide)
        }

        if (list.isEmpty()) {
            Spacer(Modifier.height(96.dp))
            Text("No favourites yet", style = MaterialTheme.typography.headlineSmall, color = LumenColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Open All channels, then hold OK on a channel to add it here.",
                style = MaterialTheme.typography.bodyLarge,
                color = LumenColors.TextSecondary,
            )
        } else {
            Spacer(Modifier.height(8.dp))
            LazyRow(
                state = rowState,
                // focusRestorer: coming back up from the button lands on the last focused card.
                modifier = Modifier.focusRestorer(),
                // Room around the cards for their focus scale and glow.
                contentPadding = PaddingValues(vertical = 24.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                itemsIndexed(list, key = { _, c -> "${c.sourceId}/${c.stableKey}" }) { index, channel ->
                    val previewing = preview.channelId == channel.id
                    FavouriteCard(
                        channel = channel,
                        now = guide[channel.id]?.now,
                        player = if (previewing) preview.player else null,
                        live = previewing && preview.showing,
                        focused = focusedId == channel.id,
                        onClick = {
                            preview.handOver()
                            container.lastChannelId.value = channel.id
                            onPlay(channel.id)
                        },
                        onLongClick = { vm.remove(channel) },
                        modifier = Modifier
                            .then(if (index == focusIndex) Modifier.focusRequester(cardFocus) else Modifier)
                            .onFocusChanged {
                                if (it.isFocused) focusedId = channel.id
                                else if (focusedId == channel.id) focusedId = null
                            },
                    )
                }
            }
            Text(
                "Hold OK to remove a favourite",
                style = MaterialTheme.typography.bodyMedium,
                color = LumenColors.TextTertiary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }

    // First focus: the card we came back from (else the first), or the button if there are none.
    LaunchedEffect(list.isEmpty()) {
        if (initialFocusDone) return@LaunchedEffect
        if (list.isNotEmpty()) {
            rowState.scrollToItem(focusIndex)
            withFrameNanos { } // let the scrolled-to card compose before focusing it
            initialFocusDone = runCatching { cardFocus.requestFocus() }.isSuccess
        }
        if (!initialFocusDone) initialFocusDone = runCatching { buttonFocus.requestFocus() }.isSuccess
    }
}

private val CardWidth = 320.dp
private val CardHeight = 180.dp

@OptIn(UnstableApi::class)
@Composable
private fun FavouriteCard(
    channel: FavouriteRow,
    now: Programme?,
    player: ExoPlayer?,
    live: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Logo fades out once the preview's first frame is on screen, revealing the video under it.
    val artAlpha by animateFloatAsState(if (live) 0f else 1f, tween(400), label = "art")
    Column(Modifier.width(CardWidth)) {
        LumenCard(onClick = onClick, onLongClick = onLongClick, modifier = modifier.size(CardWidth, CardHeight)) {
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
                    ChannelArt(channel, Modifier.fillMaxSize().graphicsLayer { alpha = artAlpha })
                }
                if (live) LiveBadge(Modifier.align(Alignment.TopStart).padding(12.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            listOfNotNull(channel.number?.toString(), channel.name).joinToString("  "),
            style = MaterialTheme.typography.titleMedium,
            color = if (focused) LumenColors.TextPrimary else LumenColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // What's on now, from the guide (when there is one for this channel).
        if (now != null) {
            val dim = if (focused) LumenColors.TextSecondary else LumenColors.TextTertiary
            Spacer(Modifier.height(4.dp))
            Text(
                now.title,
                style = MaterialTheme.typography.bodyMedium,
                color = dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            ProgrammeProgress(now.progress(System.currentTimeMillis()), color = dim, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The channel logo, large and centred; a monogram while it loads or if there isn't one. */
@Composable
private fun ChannelArt(channel: FavouriteRow, modifier: Modifier = Modifier) {
    var loaded by remember(channel.logoUrl) { mutableStateOf(false) }
    Box(modifier.background(LumenColors.Surface), contentAlignment = Alignment.Center) {
        if (!loaded) Monogram(channel.name, size = 96.dp)
        if (!channel.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp),
            )
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
