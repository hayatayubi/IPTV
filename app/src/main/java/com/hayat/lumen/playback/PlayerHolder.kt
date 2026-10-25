package com.hayat.lumen.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import okhttp3.OkHttpClient

/**
 * Owns the app's single ExoPlayer. Screens [acquire] it; MainActivity calls
 * [release] when the app goes to the background, freeing decoders and memory.
 */
@OptIn(UnstableApi::class)
class PlayerHolder(
    private val context: Context,
    private val http: OkHttpClient,
) {
    private var player: ExoPlayer? = null

    fun acquire(): ExoPlayer = player ?: build().also { player = it }

    fun release() {
        player?.release()
        player = null
    }

    private fun build(): ExoPlayer {
        // Live TV wants a fast start over a deep buffer: begin playing after 1 s
        // of media instead of ExoPlayer's default 2.5 s.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 40_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_500,
            )
            .build()

        // OkHttp follows http -> https redirects, which IPTV servers love and
        // Media3's default HTTP stack refuses.
        val dataSource = OkHttpDataSource.Factory(http)

        return ExoPlayer.Builder(context)
            .setRenderersFactory(DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSource))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .build()
    }

    companion object {
        /** HLS URLs without a .m3u8 extension aren't detected automatically, so hint when we can. */
        fun mediaItem(url: String): MediaItem {
            val path = url.substringBefore('?').lowercase()
            val mime = when {
                path.endsWith(".m3u8") || "output=hls" in url.lowercase() -> MimeTypes.APPLICATION_M3U8
                path.endsWith(".ts") -> MimeTypes.VIDEO_MP2T
                else -> null
            }
            return MediaItem.Builder().setUri(url).setMimeType(mime).build()
        }
    }
}
