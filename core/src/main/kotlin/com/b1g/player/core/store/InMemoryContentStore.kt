package com.b1g.player.core.store

import com.b1g.player.core.model.Category
import com.b1g.player.core.model.ContentKind
import com.b1g.player.core.model.Episode
import com.b1g.player.core.model.LiveChannel
import com.b1g.player.core.model.Series
import com.b1g.player.core.model.VodItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reference [ContentStore] with no platform dependencies.
 *
 * Used by tests and by any caller without a database; the Room implementation in
 * the app module is expected to behave identically, so this doubles as the
 * specification for it.
 */
class InMemoryContentStore(
    private val now: () -> Long = System::currentTimeMillis,
) : ContentStore {

    private class Bucket {
        val live = ArrayList<LiveChannel>()
        val vod = ArrayList<VodItem>()
        val episodes = ArrayList<EpisodeRow>()
        var refreshedAt: Long? = null
        var epgUrl: String? = null
    }

    private val lock = Mutex()
    private val buckets = HashMap<String, Bucket>()

    override suspend fun replace(sourceId: String, fill: (ContentSink) -> Unit) {
        val staged = Bucket()
        val sink = object : ContentSink {
            override fun header(epgUrl: String?) { staged.epgUrl = epgUrl }
            override fun live(channels: List<LiveChannel>) { staged.live += channels }
            override fun vod(items: List<VodItem>) { staged.vod += items }
            override fun episodes(rows: List<EpisodeRow>) { staged.episodes += rows }
        }

        // Filled outside the lock and swapped in only on success, so a failed
        // refresh leaves the previous contents untouched.
        fill(sink)
        staged.refreshedAt = now()

        lock.withLock { buckets[sourceId] = staged }
    }

    override suspend fun categories(sourceId: String, kind: ContentKind): List<Category> =
        lock.withLock {
            val bucket = buckets[sourceId] ?: return emptyList()
            val names = when (kind) {
                ContentKind.LIVE -> bucket.live.map { it.categoryId to it.categoryName }
                ContentKind.VOD -> bucket.vod.map { it.categoryId to it.categoryName }
                ContentKind.SERIES ->
                    bucket.episodes.map { it.series.categoryId to it.series.categoryName }
            }
            names.filter { (id, name) -> id != null && name != null }
                .distinctBy { it.first }
                .sortedBy { it.second }
                .map { (id, name) -> Category(id = id!!, name = name!!, kind = kind) }
        }

    override suspend fun liveChannels(
        sourceId: String,
        categoryId: String?,
        query: String?,
        limit: Int,
        offset: Int,
    ): List<LiveChannel> = lock.withLock {
        buckets[sourceId]?.live.orEmpty()
            .asSequence()
            .filter { categoryId == null || it.categoryId == categoryId }
            .filter { matches(it.name, query) }
            .drop(offset)
            .take(limit)
            .toList()
    }

    override suspend fun vod(
        sourceId: String,
        categoryId: String?,
        query: String?,
        limit: Int,
        offset: Int,
    ): List<VodItem> = lock.withLock {
        buckets[sourceId]?.vod.orEmpty()
            .asSequence()
            .filter { categoryId == null || it.categoryId == categoryId }
            .filter { matches(it.name, query) }
            .drop(offset)
            .take(limit)
            .toList()
    }

    override suspend fun series(
        sourceId: String,
        categoryId: String?,
        query: String?,
        limit: Int,
        offset: Int,
    ): List<Series> = lock.withLock {
        buckets[sourceId]?.episodes.orEmpty()
            .asSequence()
            .filter { categoryId == null || it.series.categoryId == categoryId }
            .filter { matches(it.series.name, query) }
            .groupBy { it.series.id }
            .map { (_, rows) -> rows.first().series.copy(episodeCount = rows.size) }
            .sortedBy { it.name }
            .drop(offset)
            .take(limit)
            .toList()
    }

    override suspend fun episodes(sourceId: String, seriesId: String): List<Episode> =
        lock.withLock {
            buckets[sourceId]?.episodes.orEmpty()
                .filter { it.series.id == seriesId }
                .map { it.episode }
                .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
        }

    override suspend fun counts(sourceId: String): ContentCounts = lock.withLock {
        val bucket = buckets[sourceId]
        ContentCounts(
            live = bucket?.live?.size ?: 0,
            vod = bucket?.vod?.size ?: 0,
            episodes = bucket?.episodes?.size ?: 0,
        )
    }

    override suspend fun lastRefreshedAt(sourceId: String): Long? =
        lock.withLock { buckets[sourceId]?.refreshedAt }

    override suspend fun epgUrl(sourceId: String): String? =
        lock.withLock { buckets[sourceId]?.epgUrl }

    override suspend fun clear(sourceId: String) {
        lock.withLock { buckets.remove(sourceId) }
    }
}
