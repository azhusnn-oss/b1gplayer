package com.b1g.player.core.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Wire types for `player_api.php`. Kept internal — callers work with domain models. */

@Serializable
internal data class AuthResponseDto(
    @SerialName("user_info") val userInfo: UserInfoDto? = null,
    @SerialName("server_info") val serverInfo: ServerInfoDto? = null,
)

@Serializable
internal data class UserInfoDto(
    @Serializable(FlexibleString::class) val username: String? = null,
    @Serializable(FlexibleString::class) val message: String? = null,
    @Serializable(FlexibleBoolean::class) val auth: Boolean? = null,
    @Serializable(FlexibleString::class) val status: String? = null,
    @SerialName("exp_date") @Serializable(FlexibleLong::class) val expiresAt: Long? = null,
    @SerialName("is_trial") @Serializable(FlexibleBoolean::class) val isTrial: Boolean? = null,
    @SerialName("active_cons") @Serializable(FlexibleInt::class) val activeConnections: Int? = null,
    @SerialName("max_connections") @Serializable(FlexibleInt::class) val maxConnections: Int? = null,
    @SerialName("allowed_output_formats") val allowedOutputFormats: List<String> = emptyList(),
)

@Serializable
internal data class ServerInfoDto(
    @Serializable(FlexibleString::class) val url: String? = null,
    @Serializable(FlexibleInt::class) val port: Int? = null,
    @SerialName("https_port") @Serializable(FlexibleInt::class) val httpsPort: Int? = null,
    @SerialName("server_protocol") @Serializable(FlexibleString::class) val protocol: String? = null,
    @Serializable(FlexibleString::class) val timezone: String? = null,
)

@Serializable
internal data class CategoryDto(
    @SerialName("category_id") @Serializable(FlexibleString::class) val id: String? = null,
    @SerialName("category_name") @Serializable(FlexibleString::class) val name: String? = null,
)

@Serializable
internal data class LiveStreamDto(
    @Serializable(FlexibleInt::class) val num: Int? = null,
    @Serializable(FlexibleString::class) val name: String? = null,
    @SerialName("stream_id") @Serializable(FlexibleString::class) val streamId: String? = null,
    @SerialName("stream_icon") @Serializable(FlexibleString::class) val icon: String? = null,
    @SerialName("epg_channel_id") @Serializable(FlexibleString::class) val epgChannelId: String? = null,
    @SerialName("category_id") @Serializable(FlexibleString::class) val categoryId: String? = null,
    @SerialName("tv_archive") @Serializable(FlexibleBoolean::class) val hasArchive: Boolean? = null,
    @SerialName("direct_source") @Serializable(FlexibleString::class) val directSource: String? = null,
)

@Serializable
internal data class VodStreamDto(
    @Serializable(FlexibleInt::class) val num: Int? = null,
    @Serializable(FlexibleString::class) val name: String? = null,
    @SerialName("stream_id") @Serializable(FlexibleString::class) val streamId: String? = null,
    @SerialName("stream_icon") @Serializable(FlexibleString::class) val icon: String? = null,
    @Serializable(FlexibleDouble::class) val rating: Double? = null,
    @Serializable(FlexibleLong::class) val added: Long? = null,
    @SerialName("category_id") @Serializable(FlexibleString::class) val categoryId: String? = null,
    @SerialName("container_extension") @Serializable(FlexibleString::class) val container: String? = null,
    @SerialName("direct_source") @Serializable(FlexibleString::class) val directSource: String? = null,
)

@Serializable
internal data class SeriesDto(
    @SerialName("series_id") @Serializable(FlexibleString::class) val seriesId: String? = null,
    @Serializable(FlexibleString::class) val name: String? = null,
    @Serializable(FlexibleString::class) val cover: String? = null,
    @Serializable(FlexibleString::class) val plot: String? = null,
    @Serializable(FlexibleDouble::class) val rating: Double? = null,
    @SerialName("category_id") @Serializable(FlexibleString::class) val categoryId: String? = null,
)

@Serializable
internal data class SeriesInfoDto(
    val info: SeriesDto? = null,
    /**
     * Normally an object keyed by season number, but panels with no episodes return
     * an empty array instead — so it stays raw until [XtreamClient] can inspect it.
     */
    val episodes: JsonElement? = null,
)

@Serializable
internal data class EpisodeDto(
    @Serializable(FlexibleString::class) val id: String? = null,
    @SerialName("episode_num") @Serializable(FlexibleInt::class) val episodeNumber: Int? = null,
    @Serializable(FlexibleString::class) val title: String? = null,
    @Serializable(FlexibleInt::class) val season: Int? = null,
    @SerialName("container_extension") @Serializable(FlexibleString::class) val container: String? = null,
    val info: EpisodeInfoDto? = null,
)

@Serializable
internal data class EpisodeInfoDto(
    @Serializable(FlexibleString::class) val plot: String? = null,
    @SerialName("duration_secs") @Serializable(FlexibleInt::class) val durationSeconds: Int? = null,
    @SerialName("movie_image") @Serializable(FlexibleString::class) val image: String? = null,
)

@Serializable
internal data class VodInfoDto(
    val info: VodDetailDto? = null,
    @SerialName("movie_data") val movieData: VodStreamDto? = null,
)

@Serializable
internal data class VodDetailDto(
    @Serializable(FlexibleString::class) val plot: String? = null,
    @SerialName("movie_image") @Serializable(FlexibleString::class) val image: String? = null,
    @Serializable(FlexibleString::class) val genre: String? = null,
    @Serializable(FlexibleString::class) val director: String? = null,
    @Serializable(FlexibleString::class) val cast: String? = null,
    @Serializable(FlexibleInt::class) val duration_secs: Int? = null,
    @Serializable(FlexibleDouble::class) val rating: Double? = null,
)

@Serializable
internal data class ShortEpgDto(
    @SerialName("epg_listings") val listings: List<EpgListingDto> = emptyList(),
)

@Serializable
internal data class EpgListingDto(
    @SerialName("epg_id") @Serializable(FlexibleString::class) val epgId: String? = null,
    /** Base64-encoded by the panel. */
    @Serializable(FlexibleString::class) val title: String? = null,
    /** Base64-encoded by the panel. */
    @Serializable(FlexibleString::class) val description: String? = null,
    @SerialName("channel_id") @Serializable(FlexibleString::class) val channelId: String? = null,
    @SerialName("start_timestamp") @Serializable(FlexibleLong::class) val startTimestamp: Long? = null,
    @SerialName("stop_timestamp") @Serializable(FlexibleLong::class) val stopTimestamp: Long? = null,
)
