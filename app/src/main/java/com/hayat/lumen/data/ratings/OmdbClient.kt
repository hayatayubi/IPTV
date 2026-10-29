package com.hayat.lumen.data.ratings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

enum class KeyCheck { WORKS, REJECTED, UNREACHABLE }

/** OMDb (omdbapi.com): IMDb, Rotten Tomatoes and age ratings, with the user's own free key. */
class OmdbClient(private val http: OkHttpClient) {
    /** Looks up one well-known film to see whether OMDb accepts [key]. */
    suspend fun check(key: String): KeyCheck = withContext(Dispatchers.IO) {
        val url = BASE.toHttpUrl().newBuilder()
            .addQueryParameter("apikey", key.trim())
            .addQueryParameter("i", "tt0111161")
            .build()
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body.string()
                when {
                    response.code == 401 || "Invalid API key" in body -> KeyCheck.REJECTED
                    response.isSuccessful && "\"Response\":\"True\"" in body -> KeyCheck.WORKS
                    else -> KeyCheck.UNREACHABLE
                }
            }
        } catch (e: IOException) {
            KeyCheck.UNREACHABLE
        }
    }

    private companion object {
        const val BASE = "https://www.omdbapi.com/"
    }
}
