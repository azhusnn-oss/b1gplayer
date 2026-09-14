package com.b1g.player.core.http

import java.io.InputStream

/** A response body that is consumed exactly once. */
class HttpResponse(
    val statusCode: Int,
    val body: InputStream,
) : AutoCloseable {
    val isSuccessful: Boolean get() = statusCode in 200..299
    override fun close() = body.close()
}

/**
 * Minimal HTTP surface the core needs. Kept as an interface so parsing and the
 * Xtream client can be tested without a network, and so the Android app can supply
 * its own OkHttp instance (cache, cookie jar, TLS config).
 */
interface HttpClient {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse
}

class HttpException(val statusCode: Int, url: String) :
    RuntimeException("HTTP $statusCode for $url")
