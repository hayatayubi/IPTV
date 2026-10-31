package com.hayat.lumen.core

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import kotlinx.io.Buffer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.io.decodeSourceToSequence

internal class XtreamAccount(val outputExtension: String)
internal class XtreamCategory(val id: String, val name: String)
internal class XtreamStream(
    val streamId: String,
    val name: String,
    val categoryId: String?,
    val icon: String?,
    val epgChannelId: String?,
    val number: Int?,
)

/**
 * The `player_api.php` calls the iPhone app uses (live channels for now). Xtream panels are
 * loose with types (ids come as numbers or strings, fields can be null or missing), so every
 * field is read as raw JSON and converted leniently.
 */
internal class XtreamApi(private val http: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Checks the login. Throws [LoadException] with a user-facing reason if it's refused. */
    suspend fun account(server: XtreamServer): XtreamAccount {
        val root = json.parseToJsonElement(fetch(server, null).decodeToString())
        val refused = LoadException("${server.host} didn't accept that username and password.")
        // Wrong logins often get `[]` or `{"user_info":{"auth":0}}` rather than an HTTP error.
        val user = ((root as? JsonObject)?.get("user_info") as? JsonObject) ?: throw refused
        val auth = user["auth"].lenient()
        if (auth != "1" && auth != "true") throw refused
        val status = user["status"].lenient()?.lowercase()
        when (status) {
            null, "active" -> Unit
            "expired" -> throw LoadException("This subscription on ${server.host} has expired.")
            else -> throw LoadException("${server.host} says this account is $status.")
        }
        val formats = (user["allowed_output_formats"] as? JsonArray).orEmpty().mapNotNull { it.lenient()?.lowercase() }
        // HLS starts faster and is what Apple's player plays best; fall back to MPEG-TS.
        return XtreamAccount(if (formats.isEmpty() || "m3u8" in formats) "m3u8" else formats.first())
    }

    suspend fun liveCategories(server: XtreamServer): List<XtreamCategory> =
        decodeArray(server, "get_live_categories", RawCategory.serializer()).mapNotNull { c ->
            val id = c.id.lenient() ?: return@mapNotNull null
            val name = c.name.lenient()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            XtreamCategory(id, name)
        }.toList()

    suspend fun liveStreams(server: XtreamServer): List<XtreamStream> =
        decodeArray(server, "get_live_streams", RawStream.serializer()).mapNotNull { s ->
            val id = s.streamId.lenient() ?: return@mapNotNull null
            val name = s.name.lenient()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            XtreamStream(
                id, name, s.categoryId.lenient(), s.icon.lenient().nonBlank(), s.epg.lenient().nonBlank(),
                s.num.lenient()?.toIntOrNull(),
            )
        }.toList()

    /**
     * Reads a JSON array one object at a time, so a 50k-channel list never exists as one big
     * JSON tree (the download itself is held as bytes). Anything that isn't an array is empty.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun <T> decodeArray(
        server: XtreamServer,
        action: String,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): Sequence<T> {
        var bytes = fetch(server, action)
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            bytes = bytes.copyOfRange(3, bytes.size) // UTF-8 byte-order mark
        }
        val start = bytes.indexOfFirst { it.toInt().toChar() !in " \t\r\n" }
        if (start == -1 || bytes[start] != '['.code.toByte()) return emptySequence()
        val source = Buffer().apply { write(bytes) }
        return try {
            json.decodeSourceToSequence(source, serializer, DecodeSequenceMode.ARRAY_WRAPPED).toList().asSequence()
        } catch (e: Exception) {
            throw LoadException("${server.host} sent a list Lumen couldn't read.", e)
        }
    }

    private suspend fun fetch(server: XtreamServer, action: String?): ByteArray {
        val response: HttpResponse = try {
            http.get(server.apiUrl(action))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw LoadException("Couldn't reach ${server.host}. Check the address and your connection.", e)
        }
        if (response.status.value !in 200..299) {
            throw LoadException("${server.host} answered with error ${response.status.value}. Check the address and your login.")
        }
        return response.bodyAsBytes()
    }

    @Serializable
    private class RawCategory(
        @SerialName("category_id") val id: JsonElement? = null,
        @SerialName("category_name") val name: JsonElement? = null,
    )

    @Serializable
    private class RawStream(
        @SerialName("stream_id") val streamId: JsonElement? = null,
        val name: JsonElement? = null,
        @SerialName("category_id") val categoryId: JsonElement? = null,
        @SerialName("stream_icon") val icon: JsonElement? = null,
        @SerialName("epg_channel_id") val epg: JsonElement? = null,
        val num: JsonElement? = null,
    )
}

/** A JSON string, number or boolean as text; null for null, objects and arrays. */
internal fun JsonElement?.lenient(): String? =
    (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content

internal fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
