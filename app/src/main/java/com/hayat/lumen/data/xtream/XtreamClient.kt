package com.hayat.lumen.data.xtream

import android.util.JsonReader
import android.util.JsonToken
import com.hayat.lumen.data.ImportException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.Closeable
import java.io.IOException

data class XtreamAccount(val outputExtension: String)

data class XtreamCategory(val id: String, val name: String)

/** One `get_short_epg` listing. Times are epoch millis. */
data class XtreamListing(val title: String, val description: String?, val start: Long, val stop: Long)

data class XtreamStream(
    val streamId: String,
    val name: String,
    val categoryId: String?,
    val icon: String?,
    val epgChannelId: String?,
    val number: Int?,
)

/**
 * The few `player_api.php` calls Phase 1 needs. Responses are read with Android's
 * streaming [JsonReader], so a 50k-channel `get_live_streams` never sits in memory as one tree.
 *
 * Xtream panels are loose with types (ids come as numbers or strings, fields can be
 * null or missing), so every field is read leniently.
 */
class XtreamClient(private val http: OkHttpClient) {

    /** Checks the login. Throws [ImportException] with a user-facing reason if it's refused. */
    fun account(login: XtreamLogin, host: String): XtreamAccount = get(login, null, host) { reader ->
        var auth: String? = null
        var status: String? = null
        val formats = ArrayList<String>()
        // Wrong logins often get `[]` or `{"user_info":{"auth":0}}` rather than an HTTP error.
        if (reader.peek() != JsonToken.BEGIN_OBJECT) throw ImportException("$host didn't accept that username and password.")
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() != "user_info" || reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue()
                continue
            }
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "auth" -> auth = reader.lenientString()
                    "status" -> status = reader.lenientString()
                    "allowed_output_formats" -> if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        while (reader.hasNext()) reader.lenientString()?.let { formats += it.lowercase() }
                        reader.endArray()
                    } else reader.skipValue()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        reader.endObject()

        if (auth != "1" && auth != "true") throw ImportException("$host didn't accept that username and password.")
        when (status?.lowercase()) {
            null, "active" -> Unit
            "expired" -> throw ImportException("This subscription on $host has expired.")
            else -> throw ImportException("$host says this account is ${status.lowercase()}.")
        }
        // HLS starts faster and survives flaky Wi-Fi better; fall back to MPEG-TS.
        XtreamAccount(outputExtension = if (formats.isEmpty() || "m3u8" in formats) "m3u8" else formats.first())
    }

    fun liveCategories(login: XtreamLogin, host: String): List<XtreamCategory> =
        get(login, "get_live_categories", host) { reader ->
            val out = ArrayList<XtreamCategory>()
            reader.forEachObjectInArray {
                var id: String? = null
                var name: String? = null
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "category_id" -> id = reader.lenientString()
                        "category_name" -> name = reader.lenientString()
                        else -> reader.skipValue()
                    }
                }
                if (id != null) out += XtreamCategory(id, name?.trim().orEmpty().ifEmpty { id })
            }
            out
        }

    /**
     * Opens `get_live_streams`. Iterate [LiveStreams.entries] (lazily, so the caller can
     * suspend between rows) and close it when done.
     */
    fun openLiveStreams(login: XtreamLogin, host: String): LiveStreams {
        val response = open(login, "get_live_streams", host)
        return LiveStreams(response, host)
    }

    class LiveStreams internal constructor(private val response: Response, private val host: String) : Closeable {
        private val reader = JsonReader(response.body.charStream()).apply { isLenient = true }

        val entries: Sequence<XtreamStream> = sequence {
            try {
                yieldAll(readStreams())
            } catch (e: IOException) {
                throw e
            } catch (e: IllegalStateException) {
                throw unreadable(host, e)
            } catch (e: NumberFormatException) {
                throw unreadable(host, e)
            }
        }

        private fun readStreams(): Sequence<XtreamStream> = sequence {
            if (reader.peek() != JsonToken.BEGIN_ARRAY) {
                reader.skipValue()
                return@sequence
            }
            reader.beginArray()
            while (reader.hasNext()) {
                if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                    reader.skipValue()
                    continue
                }
                reader.beginObject()
                var id: String? = null
                var name: String? = null
                var category: String? = null
                var icon: String? = null
                var epg: String? = null
                var num: Int? = null
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "stream_id" -> id = reader.lenientString()
                        "name" -> name = reader.lenientString()
                        "category_id" -> category = reader.lenientString()
                        "stream_icon" -> icon = reader.lenientString()
                        "epg_channel_id" -> epg = reader.lenientString()
                        "num" -> num = reader.lenientString()?.toIntOrNull()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (id != null && !name.isNullOrBlank()) {
                    yield(XtreamStream(id, name.trim(), category, icon?.takeIf { it.isNotBlank() }, epg?.takeIf { it.isNotBlank() }, num))
                }
            }
            reader.endArray()
        }

        override fun close() = response.close()
    }

    /**
     * The next few programmes on one channel (`get_short_epg`). A fallback for panels whose
     * `xmltv.php` is blocked or empty; one request per channel, so only for a handful at a time.
     */
    fun shortEpg(login: XtreamLogin, streamId: String, host: String, limit: Int = 4): List<XtreamListing> {
        val url = login.apiUrl("get_short_epg").newBuilder()
            .addQueryParameter("stream_id", streamId)
            .addQueryParameter("limit", limit.toString())
            .build()
        return http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw ImportException("$host answered with error ${response.code}.")
            try {
                JsonReader(response.body.charStream()).apply { isLenient = true }.use(::readListings)
            } catch (e: IllegalStateException) {
                throw unreadable(host, e)
            } catch (e: NumberFormatException) {
                throw unreadable(host, e)
            }
        }
    }

    private fun readListings(reader: JsonReader): List<XtreamListing> {
        val out = ArrayList<XtreamListing>()
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return out
        }
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() != "epg_listings") {
                reader.skipValue()
                continue
            }
            reader.forEachObjectInArray {
                var title: String? = null
                var description: String? = null
                var start: Long? = null
                var stop: Long? = null
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "title" -> title = reader.lenientString()
                        "description" -> description = reader.lenientString()
                        "start_timestamp" -> start = reader.lenientString()?.toLongOrNull()
                        "stop_timestamp" -> stop = reader.lenientString()?.toLongOrNull()
                        else -> reader.skipValue()
                    }
                }
                val name = title?.let(::decodeText)?.trim()
                if (!name.isNullOrEmpty() && start != null && stop != null && stop > start) {
                    out += XtreamListing(name, description?.let(::decodeText)?.trim()?.ifEmpty { null }, start * 1000, stop * 1000)
                }
            }
        }
        reader.endObject()
        return out
    }

    private fun open(login: XtreamLogin, action: String?, host: String): Response {
        val request = Request.Builder().url(login.apiUrl(action)).build()
        val response = http.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw ImportException("$host answered with error ${response.code}. Check the server and your login.")
        }
        return response
    }

    private fun <T> get(login: XtreamLogin, action: String?, host: String, read: (JsonReader) -> T): T =
        open(login, action, host).use { response ->
            try {
                JsonReader(response.body.charStream()).apply { isLenient = true }.use(read)
            } catch (e: IllegalStateException) {
                throw unreadable(host, e)
            } catch (e: NumberFormatException) {
                throw unreadable(host, e)
            }
            // IOException (network drop mid-download) passes through: the caller reports "couldn't reach".
        }

    private companion object {
        /** Xtream sends EPG text base64-encoded; a few panels send it plain. */
        fun decodeText(raw: String): String {
            val bytes = runCatching { java.util.Base64.getDecoder().decode(raw.trim()) }.getOrNull() ?: return raw
            val decoder = Charsets.UTF_8.newDecoder()
            return runCatching { decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString() }.getOrNull() ?: raw
        }

        /** The server sent something that isn't Xtream JSON (an HTML error page, say). */
        fun unreadable(host: String, cause: Throwable) =
            ImportException("$host sent something Lumen couldn't read. Is this an Xtream server?", cause)

        fun JsonReader.lenientString(): String? = when (peek()) {
            JsonToken.STRING, JsonToken.NUMBER -> nextString()
            JsonToken.BOOLEAN -> nextBoolean().toString()
            JsonToken.NULL -> { nextNull(); null }
            else -> { skipValue(); null }
        }

        /** Some panels answer an empty list as `{}` or `null`, so anything but an array means "none". */
        inline fun JsonReader.forEachObjectInArray(body: () -> Unit) {
            if (peek() != JsonToken.BEGIN_ARRAY) {
                skipValue()
                return
            }
            beginArray()
            while (hasNext()) {
                if (peek() != JsonToken.BEGIN_OBJECT) {
                    skipValue()
                    continue
                }
                beginObject()
                body()
                endObject()
            }
            endArray()
        }
    }
}
