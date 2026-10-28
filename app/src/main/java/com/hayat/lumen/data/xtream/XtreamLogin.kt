package com.hayat.lumen.data.xtream

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * An Xtream Codes login: server address plus username and password.
 *
 * Many providers switch off the `get.php` playlist download (it answers 404 or a
 * made-up code like 884) but keep the `player_api.php` JSON API that IPTV apps use,
 * so Lumen always talks to the API when it has an Xtream login.
 */
data class XtreamLogin(val server: HttpUrl, val username: String, val password: String) {

    /** `player_api.php` with the login, plus `action=<action>` when given. */
    fun apiUrl(action: String? = null): HttpUrl = server.newBuilder()
        .addPathSegment("player_api.php")
        .addQueryParameter("username", username)
        .addQueryParameter("password", password)
        .apply { if (action != null) addQueryParameter("action", action) }
        .build()

    /** The panel's full XMLTV guide, `xmltv.php`. Used when the user hasn't entered their own guide URL. */
    fun xmltvUrl(): String = server.newBuilder()
        .addPathSegment("xmltv.php")
        .addQueryParameter("username", username)
        .addQueryParameter("password", password)
        .build()
        .toString()

    /** Xtream's live stream path: `/live/<user>/<pass>/<id>.<ext>`. */
    fun liveStreamUrl(streamId: String, extension: String): String = server.newBuilder()
        .addPathSegment("live")
        .addPathSegment(username)
        .addPathSegment(password)
        .addPathSegment("$streamId.$extension")
        .build()
        .toString()

    /** A film: `/movie/<user>/<pass>/<id>.<ext>`. */
    fun movieUrl(streamId: String, extension: String): String = mediaUrl("movie", streamId, extension)

    /** A series episode: `/series/<user>/<pass>/<id>.<ext>`. */
    fun episodeUrl(episodeId: String, extension: String): String = mediaUrl("series", episodeId, extension)

    private fun mediaUrl(kind: String, id: String, extension: String): String = server.newBuilder()
        .addPathSegment(kind)
        .addPathSegment(username)
        .addPathSegment(password)
        .addPathSegment("$id.$extension")
        .build()
        .toString()

    companion object {
        private val liveStreamId = Regex("/live/[^/]+/[^/]+/(\\d+)(\\.[a-z0-9]+)?$", RegexOption.IGNORE_CASE)

        /** The stream id in a URL built by [liveStreamUrl], e.g. `…/live/u/p/123.m3u8` → `123`. */
        fun streamIdOf(streamUrl: String): String? = liveStreamId.find(streamUrl.substringBefore('?'))?.groupValues?.get(1)

        /** Server typed by hand, e.g. `cf.example.com:8080` or `http://cf.example.com/`. Null if unusable. */
        fun of(server: String, username: String, password: String): XtreamLogin? {
            val raw = server.trim().let { if ("://" in it) it else "http://$it" }
            val base = raw.toHttpUrlOrNull()?.let(::serverRoot) ?: return null
            if (username.isBlank() || password.isBlank()) return null
            return XtreamLogin(base, username.trim(), password.trim())
        }

        /**
         * Recognises an Xtream playlist link (`…/get.php?username=…&password=…`) or API
         * link (`…/player_api.php?…`) and turns it into a login. Null for any other URL.
         */
        fun fromPlaylistUrl(url: String): XtreamLogin? {
            val parsed = url.trim().toHttpUrlOrNull() ?: return null
            val file = parsed.pathSegments.lastOrNull()?.lowercase()
            if (file != "get.php" && file != "player_api.php") return null
            val user = parsed.queryParameter("username")?.takeIf { it.isNotBlank() } ?: return null
            val pass = parsed.queryParameter("password")?.takeIf { it.isNotBlank() } ?: return null
            // Keep any sub-path the panel lives under, minus the php file itself.
            val base = parsed.newBuilder().query(null).fragment(null).removePathSegment(parsed.pathSize - 1).build()
            return XtreamLogin(serverRoot(base), user, pass)
        }

        /** Drops query, fragment and a trailing empty segment so path building is predictable. */
        private fun serverRoot(url: HttpUrl): HttpUrl {
            val b = url.newBuilder().query(null).fragment(null).username("").password("")
            val segments = url.pathSegments
            if (segments.lastOrNull()?.isEmpty() == true && segments.size > 1) b.removePathSegment(segments.size - 1)
            return b.build()
        }
    }
}
