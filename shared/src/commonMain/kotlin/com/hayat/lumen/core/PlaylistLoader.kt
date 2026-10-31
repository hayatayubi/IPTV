package com.hayat.lumen.core

import com.hayat.lumen.data.m3u.EntryKind
import com.hayat.lumen.data.m3u.M3uEntry
import com.hayat.lumen.data.m3u.M3uParser
import com.hayat.lumen.data.m3u.StableKeys
import com.hayat.lumen.util.CategoryNames
import com.hayat.lumen.util.ChannelNames
import com.hayat.lumen.util.UrlRedaction
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException

/**
 * Loads a playlist's live channels: the entry point the iPhone app calls. Each method is a
 * Kotlin `suspend` function, which Swift sees as `async throws`.
 */
class PlaylistLoader internal constructor(private val http: HttpClient) {
    private val xtream = XtreamApi(http)

    /** An Xtream login typed in the form. */
    @Throws(Exception::class)
    suspend fun loadXtream(server: String, username: String, password: String): LiveLibrary {
        val login = XtreamServer.of(server, username, password)
            ?: throw LoadException("Enter a server address, username and password.")
        return loadXtream(login)
    }

    /** An M3U link. An Xtream `get.php` link is loaded through the Xtream API instead (see [isXtreamLink]). */
    @Throws(Exception::class)
    suspend fun loadM3u(link: String): LiveLibrary {
        XtreamServer.fromPlaylistUrl(link)?.let { return loadXtream(it) }
        val url = link.trim()
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            throw LoadException("That doesn't look like a web address.")
        }
        val host = UrlRedaction.hostOnly(url)
        val text = try {
            val response = http.get(url)
            if (response.status.value !in 200..299) {
                throw LoadException("$host answered with error ${response.status.value}. Check the link.")
            }
            response.bodyAsText()
        } catch (e: LoadException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LoadException("Couldn't reach $host. Check the link and your connection.", e)
        }
        val parser = M3uParser()
        val entries = parser.entries(text.lineSequence()).filter { it.kind == EntryKind.LIVE }.toList()
        if (entries.isEmpty()) throw LoadException("No channels found in that playlist.")
        return build(entries, categoryOrder = emptyList(), epgUrl = parser.header.epgUrl, skipped = parser.skipped)
    }

    /** True for `get.php` / `player_api.php` links, which carry an Xtream login. */
    fun isXtreamLink(link: String): Boolean = XtreamServer.fromPlaylistUrl(link) != null

    /** The login inside an Xtream link, to store it like a typed one. Null for other links. */
    fun xtreamLogin(link: String): XtreamLoginParts? =
        XtreamServer.fromPlaylistUrl(link)?.let { XtreamLoginParts(it.base.toString(), it.username, it.password) }

    private suspend fun loadXtream(login: XtreamServer): LiveLibrary {
        val account = xtream.account(login)
        val categories = xtream.liveCategories(login)
        val names = categories.associate { it.id to it.name }
        val entries = xtream.liveStreams(login).map { s ->
            M3uEntry(
                name = s.name,
                url = login.liveStreamUrl(s.streamId, account.outputExtension),
                kind = EntryKind.LIVE,
                tvgId = s.epgChannelId,
                tvgName = null,
                tvgLogo = s.icon,
                groupTitle = s.categoryId?.let(names::get),
                tvgChno = s.number,
            )
        }
        if (entries.isEmpty()) throw LoadException("${login.host} has no live channels for this login.")
        return build(entries, categories.map { it.name }, epgUrl = null, skipped = 0)
    }

    /** Same keys and grouping as the Android import (`LiveRepository.importEntries`). */
    private fun build(entries: List<M3uEntry>, categoryOrder: List<String>, epgUrl: String?, skipped: Int): LiveLibrary {
        val keys = StableKeys()
        val channels = entries.map { e ->
            val display = ChannelNames.display(e.name)
            LiveChannel(
                key = keys.keyFor(e),
                name = e.name,
                title = display.title,
                quality = display.tags.firstOrNull(),
                categoryName = e.groupTitle,
                logoUrl = e.tvgLogo,
                streamUrl = e.url,
                tvgId = e.tvgId,
                number = e.tvgChno,
            )
        }
        val counts = channels.groupingBy { it.categoryName }.eachCount()
        val order = (categoryOrder + channels.mapNotNull { it.categoryName }).distinct()
        val categories = order.filter { (counts[it] ?: 0) > 0 }.map { LiveCategory(it, CategoryNames.title(it), counts.getValue(it)) }
        return LiveLibrary(categories, channels, epgUrl, skipped)
    }
}

class XtreamLoginParts(val server: String, val username: String, val password: String)
