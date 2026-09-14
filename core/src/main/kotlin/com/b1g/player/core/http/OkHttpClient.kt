package com.b1g.player.core.http

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

/**
 * Default [HttpClient]. Uses generous timeouts because IPTV panels are frequently
 * slow to build a several-megabyte playlist response.
 */
class OkHttpEngine(
    private val client: OkHttpClient = defaultClient(),
    private val defaultUserAgent: String = DEFAULT_USER_AGENT,
) : HttpClient {

    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", headers["User-Agent"] ?: defaultUserAgent)
                .apply {
                    headers.forEach { (name, value) ->
                        if (!name.equals("User-Agent", ignoreCase = true)) header(name, value)
                    }
                }
                .build()

            val response = client.newCall(request).execute()
            // Hand the raw stream out so large playlists are parsed incrementally
            // instead of being materialised as one huge String.
            val stream = response.body?.byteStream() ?: ByteArrayInputStream(ByteArray(0))
            HttpResponse(response.code, stream)
        }

    companion object {
        /**
         * Many providers block unknown agents outright; VLC's agent is the one value
         * that is accepted essentially everywhere.
         */
        const val DEFAULT_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }
}
