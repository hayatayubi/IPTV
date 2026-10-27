package com.hayat.lumen.data.epg

import android.util.Xml
import androidx.room.withTransaction
import com.hayat.lumen.data.ImportException
import com.hayat.lumen.data.db.ChannelProgramme
import com.hayat.lumen.data.db.EpgStateEntity
import com.hayat.lumen.data.db.LumenDatabase
import com.hayat.lumen.data.db.ProgrammeEntity
import com.hayat.lumen.data.db.SourceEntity
import com.hayat.lumen.data.db.SourceType
import com.hayat.lumen.data.xtream.XtreamClient
import com.hayat.lumen.data.xtream.XtreamLogin
import com.hayat.lumen.util.UrlRedaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import kotlin.coroutines.coroutineContext

/** A guide entry as the UI sees it. Times are epoch millis. */
data class Programme(val title: String, val description: String?, val start: Long, val stop: Long) {
    /** How far through the programme [now] is, 0..1. */
    fun progress(now: Long): Float = ((now - start).toFloat() / (stop - start)).coerceIn(0f, 1f)
}

/** What's on a channel now, and what's on after it. Either can be missing. */
data class NowNext(val now: Programme?, val next: Programme?) {
    companion object {
        /** Picks now/next from one channel's programmes, sorted by start. Null if there's nothing current or upcoming. */
        fun from(programmes: List<Programme>, at: Long): NowNext? {
            val current = programmes.firstOrNull { it.start <= at && at < it.stop }
            val next = programmes.firstOrNull { it.start >= (current?.stop ?: at) }
            return if (current == null && next == null) null else NowNext(current, next)
        }
    }
}

/**
 * The TV guide (EPG). Each source has one guide URL: the one the user entered, else the
 * playlist's `url-tvg`, else (Xtream) the panel's `xmltv.php`.
 *
 * Updating streams the XMLTV file and keeps only programmes for this playlist's channels,
 * from 2 hours ago to 36 hours ahead, so even a huge guide is a few MB in the database.
 *
 * Other screens read now/next through [nowNextForGroup], [nowNextForChannels] and [nowNext].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EpgRepository(
    private val db: LumenDatabase,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
) {
    private val xtream = XtreamClient(http)
    private val sourceDao = db.sources()
    private val channelDao = db.channels()
    private val epgDao = db.epg()

    val states: Flow<List<EpgStateEntity>> = epgDao.observeStates()

    private val _updating = MutableStateFlow<Map<Long, Int>>(emptyMap())

    /** Sources whose guide is downloading right now → programmes stored so far. */
    val updating: StateFlow<Map<Long, Int>> = _updating

    /** Fallback listings from Xtream's `get_short_epg`, per channel id. Memory only. */
    private val shortEpgCache = ConcurrentHashMap<Long, CachedListings>()

    private class CachedListings(val fetchedAt: Long, val programmes: List<Programme>)

    /** The URL the guide is loaded from, or null if this source has none. */
    fun guideUrl(source: SourceEntity): String? =
        source.epgUrl?.takeIf { it.isNotBlank() }
            ?: if (source.type == SourceType.XTREAM) xtreamLogin(source)?.xmltvUrl() else null

    /** Saves the user's guide URL (blank = automatic) and reloads the guide. */
    suspend fun setGuideUrl(sourceId: Long, url: String) {
        sourceDao.setEpgUrl(sourceId, url.trim().ifEmpty { null })
        updateInBackground(sourceId)
    }

    fun updateInBackground(sourceId: Long) {
        scope.launch { runCatching { update(sourceId) } }
    }

    /** Updates every guide that is missing or older than [STALE_AFTER_MS]. Called at launch and after adding a playlist. */
    fun updateStaleInBackground() {
        scope.launch {
            val states = epgDao.observeStates().first().associateBy { it.sourceId }
            val now = System.currentTimeMillis()
            for (source in sourceDao.observeAll().first()) {
                if (source.hidden) continue
                val updatedAt = states[source.id]?.updatedAt
                if (updatedAt == null || now - updatedAt > STALE_AFTER_MS) runCatching { update(source.id) }
            }
        }
    }

    /**
     * Downloads the source's guide and swaps it in. On failure the old guide stays and the
     * reason is saved on [EpgStateEntity.error] (never with the URL in it).
     */
    suspend fun update(sourceId: Long): Unit = withContext(Dispatchers.IO) {
        val source = sourceDao.get(sourceId)?.takeUnless { it.hidden } ?: return@withContext
        val url = guideUrl(source) ?: return@withContext
        val host = UrlRedaction.hostOnly(url)
        val batch = System.currentTimeMillis()
        var claimed = false
        _updating.update { if (sourceId in it) it.also { claimed = false } else (it + (sourceId to 0)).also { claimed = true } }
        if (!claimed) return@withContext // already updating
        try {
            val keys = epgDao.guideKeys(sourceId).toHashSet()
            if (keys.isEmpty()) {
                throw ImportException("These channels have no guide ids (tvg-id), so Lumen can't match a guide to them.")
            }
            val request = runCatching { Request.Builder().url(url).build() }
                .getOrElse { throw ImportException("The guide link doesn't look like a valid web address.") }
            val (stored, channels) = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ImportException("$host answered with error ${response.code} for the guide.")
                importProgrammes(sourceId, batch, keys, ungzipped(response.body.byteStream()), host)
            }
            db.withTransaction {
                epgDao.upsertState(EpgStateEntity(sourceId, batch, System.currentTimeMillis(), stored, channels, error = null))
                epgDao.deleteProgrammesExcept(sourceId, keep = batch)
            }
        } catch (e: Throwable) {
            withContext(NonCancellable) {
                epgDao.deleteBatch(sourceId, batch)
                if (e !is CancellationException) {
                    val message = when (e) {
                        is ImportException -> e.message
                        is IOException -> "Couldn't reach $host for the guide."
                        else -> "Updating the guide failed."
                    }
                    val old = epgDao.state(sourceId)
                    epgDao.upsertState(old?.copy(error = message) ?: EpgStateEntity(sourceId, 0, null, 0, 0, message))
                }
            }
            throw e
        } finally {
            _updating.update { it - sourceId }
        }
    }

    /** Streams programmes into the database under [batch]. Returns (programmes stored, channels matched). */
    private suspend fun importProgrammes(sourceId: Long, batch: Long, keys: Set<String>, input: InputStream, host: String): Pair<Int, Int> {
        val from = batch - KEEP_PAST_MS
        val until = batch + KEEP_AHEAD_MS
        val pull = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(input, null) // null: take the encoding from the XML declaration
        }
        val parser = XmltvParser(pull)
        val pending = ArrayList<ProgrammeEntity>(BATCH)
        val matched = HashSet<String>()
        var seen = 0
        var stored = 0
        try {
            for (p in parser.programmes()) {
                seen++
                if (p.channel !in keys || p.stop < from || p.start > until) continue
                coroutineContext.ensureActive()
                matched += p.channel
                pending += ProgrammeEntity(sourceId = sourceId, batch = batch, channelKey = p.channel, start = p.start, stop = p.stop, title = p.title, description = p.description)
                if (pending.size == BATCH) {
                    epgDao.insertProgrammes(pending.toList())
                    stored += pending.size
                    pending.clear()
                    _updating.update { it + (sourceId to stored) }
                }
            }
        } catch (e: XmlPullParserException) {
            throw ImportException("$host sent a guide Lumen couldn't read. Is it an XMLTV link?", e)
        }
        if (pending.isNotEmpty()) epgDao.insertProgrammes(pending.toList())
        stored += pending.size
        if (stored == 0) {
            throw ImportException(
                if (seen == 0) "The guide from $host is empty."
                else "The guide from $host has no programmes for these channels right now.",
            )
        }
        return stored to matched.size
    }

    // --- Reading ---

    /** Now/next for every channel in a group, from the stored guide. Updates every minute. */
    fun nowNextForGroup(sourceId: Long, groupName: String): Flow<Map<Long, NowNext>> =
        minuteTicker().flatMapLatest { now ->
            epgDao.observeForGroup(sourceId, groupName, now, now + LOOK_AHEAD_MS).map { group(it, now) }
        }

    /**
     * Now/next for a few channels (the player, the focused row, favourites). Channels the stored
     * guide doesn't cover fall back to Xtream's per-channel `get_short_epg`, for up to
     * [FALLBACK_MAX] of them. Channels with nothing known are left out of the map.
     */
    fun nowNextForChannels(channelIds: List<Long>): Flow<Map<Long, NowNext>> {
        if (channelIds.isEmpty()) return flowOf(emptyMap())
        return minuteTicker()
            .flatMapLatest { now ->
                epgDao.observeForChannels(channelIds, now, now + LOOK_AHEAD_MS).map { now to group(it, now) }
            }
            .mapLatest { (now, fromGuide) ->
                val missing = channelIds.filter { it !in fromGuide }.take(FALLBACK_MAX)
                if (missing.isEmpty()) fromGuide else fromGuide + shortEpg(missing, now)
            }
    }

    fun nowNext(channelId: Long): Flow<NowNext?> = nowNextForChannels(listOf(channelId)).map { it[channelId] }

    private suspend fun shortEpg(channelIds: List<Long>, now: Long): Map<Long, NowNext> = withContext(Dispatchers.IO) {
        val out = HashMap<Long, NowNext>()
        val logins = HashMap<Long, XtreamLogin?>()
        for (id in channelIds) {
            val cached = shortEpgCache[id]?.takeIf { it.isFresh(now) }
            val programmes = cached?.programmes ?: run {
                val channel = channelDao.get(id) ?: return@run emptyList()
                val login = logins.getOrPut(channel.sourceId) {
                    sourceDao.get(channel.sourceId)?.takeIf { it.type == SourceType.XTREAM }?.let(::xtreamLogin)
                } ?: return@run emptyList()
                val streamId = XtreamLogin.streamIdOf(channel.streamUrl) ?: return@run emptyList()
                val listings = runCatching {
                    xtream.shortEpg(login, streamId, UrlRedaction.hostOnly(login.server.toString()))
                        .map { Programme(it.title, it.description, it.start, it.stop) }
                        .sortedBy { it.start }
                }.getOrElse { if (it is CancellationException) throw it else emptyList() }
                shortEpgCache[id] = CachedListings(now, listings)
                listings
            }
            NowNext.from(programmes, now)?.let { out[id] = it }
        }
        out
    }

    /** Cached listings are reused until the current programme ends, for at most 30 min (15 if empty). */
    private fun CachedListings.isFresh(now: Long): Boolean {
        val age = now - fetchedAt
        return if (programmes.isEmpty()) age < 15 * MINUTE else age < 30 * MINUTE && programmes.any { it.stop > now }
    }

    private fun xtreamLogin(source: SourceEntity) = XtreamLogin.of(source.url, source.username.orEmpty(), source.password.orEmpty())

    private companion object {
        const val MINUTE = 60_000L
        const val BATCH = 1_000
        const val FALLBACK_MAX = 12
        const val KEEP_PAST_MS = 2 * 60 * MINUTE
        const val KEEP_AHEAD_MS = 36 * 60 * MINUTE
        const val LOOK_AHEAD_MS = 6 * 60 * MINUTE
        const val STALE_AFTER_MS = 12 * 60 * MINUTE

        /** Emits the time now, then again at the start of every minute. */
        fun minuteTicker(): Flow<Long> = flow {
            while (true) {
                val now = System.currentTimeMillis()
                emit(now)
                delay(MINUTE - now % MINUTE)
            }
        }

        fun group(rows: List<ChannelProgramme>, now: Long): Map<Long, NowNext> =
            rows.groupBy({ it.channelId }) { Programme(it.title, it.description, it.start, it.stop) }
                .mapNotNull { (id, programmes) -> NowNext.from(programmes, now)?.let { id to it } }
                .toMap()

        /** `.xml.gz` guides are common; OkHttp only unzips `Content-Encoding: gzip`, not gzip files. */
        fun ungzipped(raw: InputStream): InputStream {
            val input = BufferedInputStream(raw)
            input.mark(2)
            val gzip = input.read() == 0x1f && input.read() == 0x8b
            input.reset()
            return if (gzip) BufferedInputStream(GZIPInputStream(input)) else input
        }
    }
}
