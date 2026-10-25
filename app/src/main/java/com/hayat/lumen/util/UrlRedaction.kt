package com.hayat.lumen.util

import java.net.URI

/**
 * Playlist and stream URLs routinely carry credentials (query params, user:pass@,
 * or Xtream-style /live/<user>/<pass>/ paths). Nothing that might be logged or
 * shown in an error message should contain them.
 */
object UrlRedaction {

    private val xtreamPath = Regex("/(live|movie|series|timeshift)/[^/]+/[^/]+/", RegexOption.IGNORE_CASE)

    /** Safe for logs and error messages: just the host, e.g. "cf.example.com". */
    fun hostOnly(url: String): String =
        runCatching { URI(url.trim()).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "the server"

    /**
     * The URL with credentials removed but the stream identity kept, so it is stable
     * when a password changes: drops user-info, query and fragment, and collapses
     * Xtream `/live/<user>/<pass>/123.ts` to `/live/123.ts`.
     */
    fun withoutCredentials(url: String): String {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return url.substringBefore('?')
        val host = uri.host ?: return url.substringBefore('?')
        val port = if (uri.port != -1) ":${uri.port}" else ""
        val path = (uri.rawPath ?: "").replace(xtreamPath, "/$1/")
        return "${uri.scheme}://$host$port$path"
    }
}
