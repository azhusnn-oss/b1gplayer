package com.b1g.player.core.store

import com.b1g.player.core.model.Category
import com.b1g.player.core.model.ContentKind
import com.b1g.player.core.model.Episode
import com.b1g.player.core.model.LiveChannel
import com.b1g.player.core.model.Series
import com.b1g.player.core.model.VodItem

/**
 * One `/series/` playlist row together with the show it belongs to.
 *
 * The show is repeated on every episode because a playlist states it that way —
 * there is no separate series record to read — and the store collapses the
 * repetition when it reports the series list.
 */
data class EpisodeRow(
    val series: Series,
    val episode: Episode,
)

/**
 * Receives parsed entries during [ContentStore.replace].
 *
 * Deliberately not suspending: it is called from inside the playlist parse loop,
 * which already runs on an IO thread, and a blocking batch insert is both simpler
 * and faster there than suspending per batch.
 */
interface ContentSink {
    /** Playlist-level data from the `#EXTM3U` line, if the playlist carries any. */
    fun header(epgUrl: String?)

    fun live(channels: List<LiveChannel>)
    fun vod(items: List<VodItem>)
    fun episodes(rows: List<EpisodeRow>)
}

data class ContentCounts(val live: Int, val vod: Int, val episodes: Int = 0) {
    val isEmpty: Boolean get() = live == 0 && vod == 0 && episodes == 0
}

/**
 * Where a parsed playlist lives between launches.
 *
 * Holding a playlist in memory does not scale — providers ship catalogues of a
 * hundred thousand entries and more — so entries are written in batches as they
 * are parsed and read back a page at a time, with filtering pushed down rather
 * than done on a loaded list.
 */
interface ContentStore {

    /**
     * Replaces everything held for [sourceId] with whatever [fill] writes.
     *
     * Atomic: if [fill] throws, the previous contents survive, so a refresh that
     * fails halfway cannot leave a user with half a playlist.
     */
    suspend fun replace(sourceId: String, fill: (ContentSink) -> Unit)

    suspend fun categories(sourceId: String, kind: ContentKind): List<Category>

    suspend fun liveChannels(
        sourceId: String,
        categoryId: String? = null,
        query: String? = null,
        limit: Int = DEFAULT_PAGE,
        offset: Int = 0,
    ): List<LiveChannel>

    suspend fun vod(
        sourceId: String,
        categoryId: String? = null,
        query: String? = null,
        limit: Int = DEFAULT_PAGE,
        offset: Int = 0,
    ): List<VodItem>

    /** Distinct shows, collapsed from the stored episodes. */
    suspend fun series(
        sourceId: String,
        categoryId: String? = null,
        query: String? = null,
        limit: Int = DEFAULT_PAGE,
        offset: Int = 0,
    ): List<Series>

    /** Every episode of one show, in season and episode order. */
    suspend fun episodes(sourceId: String, seriesId: String): List<Episode>

    suspend fun counts(sourceId: String): ContentCounts

    /** Epoch millis of the last successful [replace], or null if never stored. */
    suspend fun lastRefreshedAt(sourceId: String): Long?

    /** The guide URL the stored playlist advertised, if it declared one. */
    suspend fun epgUrl(sourceId: String): String?

    suspend fun clear(sourceId: String)

    companion object {
        const val DEFAULT_PAGE = 100
    }
}

/** Shared matching rule, so every source and store filters identically. */
fun matches(name: String, query: String?): Boolean =
    query.isNullOrBlank() || name.contains(query.trim(), ignoreCase = true)
