package com.b1g.player.core.xtream

import com.b1g.player.core.http.HttpClient
import com.b1g.player.core.http.HttpException
import com.b1g.player.core.model.Category
import com.b1g.player.core.model.ContentKind
import com.b1g.player.core.model.EpgEntry
import com.b1g.player.core.model.Episode
import com.b1g.player.core.model.LiveChannel
import com.b1g.player.core.model.Series
import com.b1g.player.core.model.SourceConfig
import com.b1g.player.core.model.StreamRequest
import com.b1g.player.core.model.VodItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.util.Base64

/** Outcome of an Xtream login attempt. */
sealed interface XtreamAuthResult {
    data class Success(val account: XtreamAccount) : XtreamAuthResult
    /** The server answered, but rejected the credentials or the account is expired. */
    data class Rejected(val reason: String) : XtreamAuthResult
}

data class XtreamAccount(
    val username: String,
    val status: String?,
    val expiresAtEpochSeconds: Long?,
    val isTrial: Boolean,
    val activeConnections: Int?,
    val maxConnections: Int?,
    val allowedOutputFormats: List<String>,
) {
    /** The container to request for live playback, preferring HLS when offered. */
    val preferredLiveExtension: String
        get() = when {
            allowedOutputFormats.isEmpty() -> "m3u8"
            allowedOutputFormats.any { it.equals("m3u8", true) } -> "m3u8"
            else -> allowedOutputFormats.first()
        }
}

/**
 * Client for the Xtream Codes `player_api.php` interface.
 *
 * Every call is a plain GET; the panel returns either a JSON array of rows or an
 * object. Failures are surfaced as exceptions except for login, which returns
 * [XtreamAuthResult] because a rejected login is an expected outcome, not an error.
 */
class XtreamClient(
    private val http: HttpClient,
    private val json: Json = defaultJson,
) {

    suspend fun authenticate(config: SourceConfig.Xtream): XtreamAuthResult {
        val response = decode<AuthResponseDto>(XtreamUrls.playerApi(config), config)
        val info = response.userInfo
            ?: return XtreamAuthResult.Rejected("Server did not return account information")

        if (info.auth != true) {
            return XtreamAuthResult.Rejected(info.message ?: "Invalid username or password")
        }
        if (info.status != null && !info.status.equals("Active", ignoreCase = true)) {
            return XtreamAuthResult.Rejected("Account status: ${info.status}")
        }

        return XtreamAuthResult.Success(
            XtreamAccount(
                username = info.username ?: config.username,
                status = info.status,
                expiresAtEpochSeconds = info.expiresAt,
                isTrial = info.isTrial == true,
                activeConnections = info.activeConnections,
                maxConnections = info.maxConnections,
                allowedOutputFormats = info.allowedOutputFormats,
            )
        )
    }

    suspend fun categories(config: SourceConfig.Xtream, kind: ContentKind): List<Category> {
        val action = when (kind) {
            ContentKind.LIVE -> "get_live_categories"
            ContentKind.VOD -> "get_vod_categories"
            ContentKind.SERIES -> "get_series_categories"
        }
        return decode<List<CategoryDto>>(XtreamUrls.playerApi(config, action), config)
            .mapNotNull { dto ->
                val id = dto.id ?: return@mapNotNull null
                Category(id = id, name = dto.name ?: id, kind = kind)
            }
    }

    suspend fun liveStreams(
        config: SourceConfig.Xtream,
        categoryId: String? = null,
        extension: String = "m3u8",
    ): List<LiveChannel> =
        decode<List<LiveStreamDto>>(
            XtreamUrls.playerApi(config, "get_live_streams", categoryParams(categoryId)),
            config,
        ).mapNotNull { dto ->
            val id = dto.streamId ?: return@mapNotNull null
            LiveChannel(
                id = id,
                name = dto.name ?: "Channel $id",
                logoUrl = dto.icon,
                categoryId = dto.categoryId,
                epgChannelId = dto.epgChannelId,
                number = dto.num,
                // `direct_source` wins when the panel supplies it: it points at the
                // real origin and skips a redirect hop.
                stream = streamOf(config, dto.directSource ?: XtreamUrls.live(config, id, extension)),
            )
        }

    suspend fun vodStreams(config: SourceConfig.Xtream, categoryId: String? = null): List<VodItem> =
        decode<List<VodStreamDto>>(
            XtreamUrls.playerApi(config, "get_vod_streams", categoryParams(categoryId)),
            config,
        ).mapNotNull { dto ->
            val id = dto.streamId ?: return@mapNotNull null
            VodItem(
                id = id,
                name = dto.name ?: "Movie $id",
                logoUrl = dto.icon,
                categoryId = dto.categoryId,
                rating = dto.rating,
                addedEpochSeconds = dto.added,
                stream = streamOf(
                    config,
                    dto.directSource ?: XtreamUrls.vod(config, id, dto.container ?: "mp4"),
                ),
            )
        }

    suspend fun series(config: SourceConfig.Xtream, categoryId: String? = null): List<Series> =
        decode<List<SeriesDto>>(
            XtreamUrls.playerApi(config, "get_series", categoryParams(categoryId)),
            config,
        ).mapNotNull { dto ->
            val id = dto.seriesId ?: return@mapNotNull null
            Series(
                id = id,
                name = dto.name ?: "Series $id",
                coverUrl = dto.cover,
                categoryId = dto.categoryId,
                plot = dto.plot,
                rating = dto.rating,
            )
        }

    /**
     * Episodes for one series, flattened across seasons.
     *
     * `get_series_info` returns `episodes` as an object keyed by season number, but
     * panels with nothing to report send an empty array instead, so both are handled.
     */
    suspend fun episodes(config: SourceConfig.Xtream, seriesId: String): List<Episode> {
        val info = decode<SeriesInfoDto>(
            XtreamUrls.playerApi(config, "get_series_info", mapOf("series_id" to seriesId)),
            config,
        )
        val episodes = info.episodes
        val seasons: Map<String, JsonArray> = when (episodes) {
            is JsonObject -> episodes.mapNotNull { (key, value) ->
                (value as? JsonArray)?.let { key to it }
            }.toMap()
            is JsonArray -> mapOf("0" to episodes)
            else -> emptyMap()
        }

        return seasons.flatMap { (seasonKey, array) ->
            val seasonFromKey = seasonKey.toIntOrNull() ?: 0
            array.mapNotNull { element ->
                val dto = runCatching { json.decodeFromJsonElement(EpisodeDto.serializer(), element) }
                    .getOrNull() ?: return@mapNotNull null
                val id = dto.id ?: return@mapNotNull null
                val season = dto.season ?: seasonFromKey
                Episode(
                    id = id,
                    seriesId = seriesId,
                    seasonNumber = season,
                    episodeNumber = dto.episodeNumber ?: 0,
                    title = dto.title ?: "Episode ${dto.episodeNumber ?: id}",
                    plot = dto.info?.plot,
                    durationSeconds = dto.info?.durationSeconds,
                    stillUrl = dto.info?.image,
                    stream = streamOf(config, XtreamUrls.episode(config, id, dto.container ?: "mp4")),
                )
            }
        }.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
    }

    /** The next few programmes for a channel. Titles arrive base64-encoded. */
    suspend fun shortEpg(config: SourceConfig.Xtream, streamId: String, limit: Int = 8): List<EpgEntry> =
        decode<ShortEpgDto>(
            XtreamUrls.playerApi(
                config,
                "get_short_epg",
                mapOf("stream_id" to streamId, "limit" to limit.toString()),
            ),
            config,
        ).listings.mapNotNull { listing ->
            val start = listing.startTimestamp ?: return@mapNotNull null
            val end = listing.stopTimestamp ?: return@mapNotNull null
            EpgEntry(
                channelId = listing.channelId ?: streamId,
                title = decodeBase64(listing.title) ?: "No information",
                description = decodeBase64(listing.description),
                startEpochSeconds = start,
                endEpochSeconds = end,
            )
        }

    private fun categoryParams(categoryId: String?): Map<String, String> =
        if (categoryId.isNullOrBlank()) emptyMap() else mapOf("category_id" to categoryId)

    private fun streamOf(config: SourceConfig.Xtream, url: String) =
        StreamRequest(url, headersFor(config))

    private fun headersFor(config: SourceConfig.Xtream): Map<String, String> =
        config.userAgent?.let { mapOf("User-Agent" to it) } ?: emptyMap()

    private suspend inline fun <reified T> decode(url: String, config: SourceConfig.Xtream): T {
        val body = http.get(url, headersFor(config)).use { response ->
            if (!response.isSuccessful) throw HttpException(response.statusCode, url)
            response.body.readBytes().toString(Charsets.UTF_8)
        }
        return json.decodeFromString(body)
    }

    companion object {
        val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }

        /** Panels base64 their EPG text; a few send it in the clear, so both work. */
        internal fun decodeBase64(value: String?): String? {
            if (value.isNullOrBlank()) return null
            return try {
                String(Base64.getMimeDecoder().decode(value), Charsets.UTF_8)
                    .takeIf { it.isNotBlank() }
                    ?: value
            } catch (_: IllegalArgumentException) {
                value
            }
        }
    }
}
