package com.b1g.player.core.login

import com.b1g.player.core.model.SourceConfig
import com.b1g.player.core.model.XtreamServer

/** Fields a login form can complain about. */
enum class SourceField { DISPLAY_NAME, SERVER, USERNAME, PASSWORD, PLAYLIST_URL }

/**
 * Why a field was rejected. Codes rather than messages, so the strings stay in the
 * Android resource files and can be translated.
 */
enum class SourceError { REQUIRED, INVALID_SERVER, INVALID_URL }

data class FieldError(val field: SourceField, val error: SourceError)

sealed interface SourceBuildResult {
    data class Valid(val config: SourceConfig) : SourceBuildResult
    data class Invalid(val errors: List<FieldError>) : SourceBuildResult
}

/**
 * Raw login form values, validated here rather than in the UI so the rules are
 * unit-testable and identical on every screen that can create a source.
 */
data class XtreamInput(
    val displayName: String = "",
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val userAgent: String = "",
) {
    fun build(id: String): SourceBuildResult {
        val errors = buildList {
            if (server.isBlank()) add(FieldError(SourceField.SERVER, SourceError.REQUIRED))
            else if (XtreamServer.parse(server) == null) {
                add(FieldError(SourceField.SERVER, SourceError.INVALID_SERVER))
            }
            if (username.isBlank()) add(FieldError(SourceField.USERNAME, SourceError.REQUIRED))
            if (password.isBlank()) add(FieldError(SourceField.PASSWORD, SourceError.REQUIRED))
        }
        if (errors.isNotEmpty()) return SourceBuildResult.Invalid(errors)

        val parsed = XtreamServer.parse(server)!!
        return SourceBuildResult.Valid(
            SourceConfig.Xtream(
                id = id,
                // An unnamed source is labelled by its host, which is what the user
                // recognises it by anyway.
                displayName = displayName.ifBlank { parsed.host },
                server = parsed,
                username = username.trim(),
                password = password,
                userAgent = userAgent.ifBlank { null },
            )
        )
    }
}

data class M3uInput(
    val displayName: String = "",
    val playlistUrl: String = "",
    val epgUrl: String = "",
    val userAgent: String = "",
) {
    fun build(id: String): SourceBuildResult {
        val url = playlistUrl.trim()
        val errors = buildList {
            if (url.isBlank()) add(FieldError(SourceField.PLAYLIST_URL, SourceError.REQUIRED))
            else if (!isHttpUrl(url)) add(FieldError(SourceField.PLAYLIST_URL, SourceError.INVALID_URL))
        }
        if (errors.isNotEmpty()) return SourceBuildResult.Invalid(errors)

        return SourceBuildResult.Valid(
            SourceConfig.M3u(
                id = id,
                displayName = displayName.ifBlank { defaultNameFor(url) },
                playlistUrl = url,
                epgUrl = epgUrl.trim().ifBlank { null },
                userAgent = userAgent.ifBlank { null },
            )
        )
    }

    private fun isHttpUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    private fun defaultNameFor(url: String): String =
        url.substringAfter("//").substringBefore('/').substringBefore(':').ifBlank { "Playlist" }
}
