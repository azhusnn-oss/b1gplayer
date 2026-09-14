package com.b1g.player.core

import com.b1g.player.core.http.HttpClient
import com.b1g.player.core.http.HttpResponse

/** Serves canned bodies by URL substring, and records what was requested. */
class FakeHttpClient(
    private val routes: List<Pair<String, String>> = emptyList(),
    private val statusCode: Int = 200,
) : HttpClient {

    val requestedUrls = mutableListOf<String>()
    val requestedHeaders = mutableListOf<Map<String, String>>()

    constructor(body: String) : this(listOf("" to body))

    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
        requestedUrls += url
        requestedHeaders += headers
        val body = routes.firstOrNull { (match, _) -> match.isEmpty() || url.contains(match) }?.second
            ?: error("No fake route matches $url")
        return HttpResponse(statusCode, body.byteInputStream())
    }
}
