package com.b1g.player.core.xtream

import com.b1g.player.core.model.SourceConfig
import com.b1g.player.core.model.XtreamServer
import java.net.URLEncoder

/**
 * Builds every URL an Xtream account needs.
 *
 * Playback URLs are *constructed*, not returned by the API — the panel only gives
 * out ids — so this is the single place that knows the path layout.
 */
object XtreamUrls {

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    fun playerApi(
        server: XtreamServer,
        username: String,
        password: String,
        action: String? = null,
        params: Map<String, String> = emptyMap(),
    ): String = buildString {
        append(server.baseUrl).append("/player_api.php")
        append("?username=").append(encode(username))
        append("&password=").append(encode(password))
        if (action != null) append("&action=").append(encode(action))
        params.forEach { (key, value) ->
            append('&').append(encode(key)).append('=').append(encode(value))
        }
    }

    fun playerApi(config: SourceConfig.Xtream, action: String? = null, params: Map<String, String> = emptyMap()): String =
        playerApi(config.server, config.username, config.password, action, params)

    /**
     * [extension] is the container to request. `m3u8` gives an HLS rendition that
     * recovers better from network stalls; `ts` is the raw MPEG-TS feed and is the
     * only format some panels serve, so it is the fallback.
     */
    fun live(config: SourceConfig.Xtream, streamId: String, extension: String = "m3u8"): String =
        "${config.server.baseUrl}/live/${encode(config.username)}/${encode(config.password)}/$streamId.$extension"

    fun vod(config: SourceConfig.Xtream, streamId: String, container: String = "mp4"): String =
        "${config.server.baseUrl}/movie/${encode(config.username)}/${encode(config.password)}/$streamId.$container"

    fun episode(config: SourceConfig.Xtream, episodeId: String, container: String = "mp4"): String =
        "${config.server.baseUrl}/series/${encode(config.username)}/${encode(config.password)}/$episodeId.$container"

    /** Catch-up / archive playback for channels whose `tv_archive` flag is set. */
    fun timeshift(
        config: SourceConfig.Xtream,
        streamId: String,
        durationMinutes: Int,
        start: String,
    ): String = "${config.server.baseUrl}/streaming/timeshift.php" +
        "?username=${encode(config.username)}&password=${encode(config.password)}" +
        "&stream=$streamId&start=${encode(start)}&duration=$durationMinutes"

    /** Full XMLTV guide. Large — fetch on a schedule, not per screen. */
    fun xmltv(config: SourceConfig.Xtream): String =
        "${config.server.baseUrl}/xmltv.php?username=${encode(config.username)}&password=${encode(config.password)}"

    /** The same account exported as an M3U playlist, useful for debugging. */
    fun m3uExport(config: SourceConfig.Xtream, output: String = "ts"): String =
        "${config.server.baseUrl}/get.php?username=${encode(config.username)}" +
            "&password=${encode(config.password)}&type=m3u_plus&output=$output"
}
