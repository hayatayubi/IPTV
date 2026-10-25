package com.hayat.lumen

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.hayat.lumen.data.LiveRepository
import com.hayat.lumen.data.db.LumenDatabase
import com.hayat.lumen.playback.PlayerHolder
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Hand-rolled dependency injection: every long-lived object is created once here
 * and passed to whoever needs it. One instance lives on [LumenApp].
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followSslRedirects(true)
        // Some IPTV servers reject OkHttp's default user agent.
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    val db: LumenDatabase = LumenDatabase.create(appContext)
    val live = LiveRepository(db, http)
    val player = PlayerHolder(appContext, http)

    /** The channel currently/last playing, so Live can put focus back on it. In-memory for now. */
    val lastChannelId = MutableStateFlow<Long?>(null)

    private companion object {
        const val USER_AGENT = "Lumen/0.1 (Android TV)"
    }
}

/**
 * Makes the container reachable from any composable, like a SwiftUI
 * `.environment(...)` value set once at the root.
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
