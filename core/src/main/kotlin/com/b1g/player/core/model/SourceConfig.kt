package com.b1g.player.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A saved account. The app supports any number of these, and the user switches
 * between them; both variants are normalised behind
 * [com.b1g.player.core.source.ContentSource].
 */
@Serializable
sealed interface SourceConfig {
    val id: String
    val displayName: String

    /** A plain M3U/M3U8 playlist, either remote (`http(s)://`) or a local file URI. */
    @Serializable
    @SerialName("m3u")
    data class M3u(
        override val id: String,
        override val displayName: String,
        val playlistUrl: String,
        val epgUrl: String? = null,
        val userAgent: String? = null,
    ) : SourceConfig

    /** An Xtream Codes account. */
    @Serializable
    @SerialName("xtream")
    data class Xtream(
        override val id: String,
        override val displayName: String,
        val server: XtreamServer,
        val username: String,
        val password: String,
        val userAgent: String? = null,
    ) : SourceConfig
}

/**
 * A normalised Xtream base URL.
 *
 * Users paste hosts in every imaginable shape — `http://host:8080`, `host:8080`,
 * `https://host/`, or a full `player_api.php` URL copied out of a browser — so
 * parsing is deliberately forgiving.
 */
@Serializable
data class XtreamServer(
    val scheme: String,
    val host: String,
    val port: Int?,
) {
    val baseUrl: String
        get() = buildString {
            append(scheme).append("://").append(host)
            if (port != null && port != defaultPortFor(scheme)) append(':').append(port)
        }

    companion object {
        private fun defaultPortFor(scheme: String) = if (scheme == "https") 443 else 80

        /** Returns null when [input] cannot be read as a host. */
        fun parse(input: String): XtreamServer? {
            var text = input.trim()
            if (text.isEmpty()) return null

            val scheme = when {
                text.startsWith("https://", ignoreCase = true) -> "https"
                text.startsWith("http://", ignoreCase = true) -> "http"
                else -> null
            }
            if (scheme != null) text = text.substringAfter("//")

            // Drop any path, query or fragment the user pasted along with the host.
            text = text.substringBefore('/').substringBefore('?').substringBefore('#')
            if (text.isEmpty()) return null

            // Strip credentials of the user:pass@host form.
            text = text.substringAfterLast('@')

            val (host, port) = splitHostAndPort(text) ?: return null
            if (host.isEmpty() || host.contains(' ')) return null

            return XtreamServer(
                scheme = scheme ?: if (port == 443) "https" else "http",
                host = host.lowercase(),
                port = port,
            )
        }

        private fun splitHostAndPort(text: String): Pair<String, Int?>? {
            if (text.startsWith('[')) { // IPv6 literal
                val close = text.indexOf(']')
                if (close < 0) return null
                val host = text.substring(0, close + 1)
                val rest = text.substring(close + 1)
                if (rest.isEmpty()) return host to null
                if (!rest.startsWith(':')) return null
                return host to (rest.drop(1).toIntOrNull() ?: return null)
            }
            val colon = text.lastIndexOf(':')
            if (colon < 0) return text to null
            val port = text.substring(colon + 1).toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            return text.substring(0, colon) to port
        }
    }
}
