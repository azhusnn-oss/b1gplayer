package com.b1g.player.core.model

/** The three kinds of content an IPTV provider exposes. */
enum class ContentKind { LIVE, VOD, SERIES }

/**
 * A group of content. For M3U playlists this is the `group-title` attribute; for
 * Xtream it is a real category returned by `get_*_categories`.
 */
data class Category(
    val id: String,
    val name: String,
    val kind: ContentKind,
)

/**
 * A playable live channel, normalised across both source types.
 *
 * [id] is stable within a source: the Xtream `stream_id`, or the `tvg-id` (falling
 * back to a hash of the URL) for M3U playlists.
 */
data class LiveChannel(
    val id: String,
    val name: String,
    val logoUrl: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val epgChannelId: String? = null,
    val number: Int? = null,
    val stream: StreamRequest,
)

/** A movie / on-demand title. */
data class VodItem(
    val id: String,
    val name: String,
    val logoUrl: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val rating: Double? = null,
    val addedEpochSeconds: Long? = null,
    val stream: StreamRequest,
)

/**
 * A series. Xtream only: M3U playlists flatten series into individual entries, so
 * a playlist source reports them as [VodItem]s instead.
 */
data class Series(
    val id: String,
    val name: String,
    val coverUrl: String? = null,
    val categoryId: String? = null,
    val plot: String? = null,
    val rating: Double? = null,
)

/** One episode of a [Series], grouped by [seasonNumber]. */
data class Episode(
    val id: String,
    val seriesId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val plot: String? = null,
    val durationSeconds: Int? = null,
    val stillUrl: String? = null,
    val stream: StreamRequest,
)

/** A single programme in the electronic programme guide. */
data class EpgEntry(
    val channelId: String,
    val title: String,
    val description: String? = null,
    val startEpochSeconds: Long,
    val endEpochSeconds: Long,
) {
    fun isLiveAt(epochSeconds: Long): Boolean =
        epochSeconds in startEpochSeconds until endEpochSeconds
}
