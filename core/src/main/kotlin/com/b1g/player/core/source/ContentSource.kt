package com.b1g.player.core.source

import com.b1g.player.core.model.Category
import com.b1g.player.core.model.ContentKind
import com.b1g.player.core.model.EpgEntry
import com.b1g.player.core.model.Episode
import com.b1g.player.core.model.LiveChannel
import com.b1g.player.core.model.Series
import com.b1g.player.core.model.SourceConfig
import com.b1g.player.core.model.VodItem
import com.b1g.player.core.store.ContentStore

/** Result of validating a saved account before the app shows any content. */
sealed interface ConnectResult {
    data class Success(val summary: String? = null) : ConnectResult
    data class Failure(val message: String, val cause: Throwable? = null) : ConnectResult
}

/**
 * The single abstraction the rest of the app is written against.
 *
 * Both login modes are reduced to this interface, so UI, storage, favourites and
 * playback never branch on whether the account is an M3U playlist or an Xtream
 * panel. Adding another provider later means adding an implementation here and
 * nothing else.
 */
interface ContentSource {
    val config: SourceConfig

    suspend fun connect(): ConnectResult

    /**
     * Re-fetches whatever the source caches.
     *
     * A no-op by default: a panel is queried live every time, so only a stored
     * playlist has anything to refresh.
     */
    suspend fun refresh() = Unit

    suspend fun categories(kind: ContentKind): List<Category>

    /**
     * One page of live channels.
     *
     * Filtering and paging are parameters rather than something the caller does to a
     * returned list, because a playlist catalogue is far too large to hand over whole.
     */
    suspend fun liveChannels(
        categoryId: String? = null,
        query: String? = null,
        limit: Int = ContentStore.DEFAULT_PAGE,
        offset: Int = 0,
    ): List<LiveChannel>

    suspend fun vod(
        categoryId: String? = null,
        query: String? = null,
        limit: Int = ContentStore.DEFAULT_PAGE,
        offset: Int = 0,
    ): List<VodItem>

    /**
     * One page of series.
     *
     * Xtream reports these directly; a playlist source reconstructs them by grouping
     * its `/series/` entries on the show name in their titles.
     */
    suspend fun series(
        categoryId: String? = null,
        query: String? = null,
        limit: Int = ContentStore.DEFAULT_PAGE,
        offset: Int = 0,
    ): List<Series>

    suspend fun episodes(seriesId: String): List<Episode>

    /** Null when the source advertises no guide. */
    suspend fun epgUrl(): String?

    /** Empty when the source cannot answer without the full XMLTV guide. */
    suspend fun shortEpg(channel: LiveChannel, limit: Int = 8): List<EpgEntry>
}
