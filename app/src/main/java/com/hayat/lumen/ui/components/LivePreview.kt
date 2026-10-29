package com.hayat.lumen.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.hayat.lumen.LocalAppContainer
import com.hayat.lumen.playback.PlayerHolder
import kotlinx.coroutines.delay

/** What the muted preview is doing. Compose re-reads these when they change. */
@Stable
class LivePreview {
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
fun rememberLivePreview(focusedId: Long?, streamUrl: suspend (Long) -> String?): LivePreview {
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
