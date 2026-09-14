package com.b1g.player.core.model

/**
 * Everything the player needs to open a stream.
 *
 * Headers matter: a large share of IPTV providers reject requests that do not carry
 * the same `User-Agent` (and sometimes `Referer`) the playlist was fetched with, so
 * these travel with the URL rather than being applied globally.
 */
data class StreamRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
) {
    /** Best-effort container hint for the player, e.g. `m3u8`, `ts`, `mkv`. */
    val extension: String?
        get() = url.substringBefore('?').substringAfterLast('/')
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .takeIf { it.isNotEmpty() && it.length <= 5 }

    fun withHeader(name: String, value: String): StreamRequest =
        copy(headers = headers + (name to value))
}
