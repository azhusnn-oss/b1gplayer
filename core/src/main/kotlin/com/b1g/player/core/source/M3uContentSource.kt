package com.b1g.player.core.source

import com.b1g.player.core.http.HttpClient
import com.b1g.player.core.http.HttpException
import com.b1g.player.core.m3u.M3uEntry
import com.b1g.player.core.m3u.M3uParser
import com.b1g.player.core.model.Category
import com.b1g.player.core.model.ContentKind
import com.b1g.player.core.model.EpgEntry
import com.b1g.player.core.model.Episode
import com.b1g.player.core.model.LiveChannel
import com.b1g.player.core.model.Series
import com.b1g.player.core.model.SourceConfig
import com.b1g.player.core.model.StreamRequest
import com.b1g.player.core.model.VodItem
import com.b1g.player.core.store.ContentCounts
import com.b1g.player.core.store.ContentSink
import com.b1g.player.core.store.ContentStore
import com.b1g.player.core.store.InMemoryContentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

/**
 * A [ContentSource] backed by a single M3U playlist.
 *
 * A playlist has no query interface — it is one flat document — so it is downloaded
 * once, parsed straight into [store] in batches, and queried from there afterwards.
 * Nothing accumulates the whole catalogue in memory, and a stored playlist survives
 * process death, so relaunching does not re-download a hundred megabytes.
 */
class M3uContentSource(
    override val config: SourceConfig.M3u,
    private val http: HttpClient,
    private val store: ContentStore = InMemoryContentStore(),
    private val batchSize: Int = DEFAULT_BATCH,
    private val maxCacheAgeMillis: Long = DEFAULT_MAX_CACHE_AGE,
    private val now: () -> Long = System::currentTimeMillis,
) : ContentSource {

    private val loadLock = Mutex()

    override suspend fun connect(): ConnectResult = try {
        val counts = ensureLoaded(force = false)
        if (counts.isEmpty) {
            ConnectResult.Failure("Playlist loaded but contained no channels")
        } else {
            ConnectResult.Success("${counts.live} channels, ${counts.vod} on-demand items")
        }
    } catch (e: HttpException) {
        ConnectResult.Failure("Playlist rejected the request (HTTP ${e.statusCode})", e)
    } catch (e: IOException) {
        ConnectResult.Failure(describeNetworkFailure("Could not download the playlist", e), e)
    }

    /** Re-downloads the playlist, replacing what is stored. */
    override suspend fun refresh() {
        ensureLoaded(force = true)
    }

    override suspend fun categories(kind: ContentKind): List<Category> =
        store.categories(config.id, kind)

    override suspend fun liveChannels(
        categoryId: String?,
        query: String?,
        limit: Int,
        offset: Int,
    ): List<LiveChannel> = store.liveChannels(config.id, categoryId, query, limit, offset)

    override suspend fun vod(
        categoryId: String?,
        query: String?,
        limit: Int,
        offset: Int,
    ): List<VodItem> = store.vod(config.id, categoryId, query, limit, offset)

    /** A flat playlist has no season/episode structure to report. */
    override suspend fun series(categoryId: String?): List<Series> = emptyList()

    override suspend fun episodes(seriesId: String): List<Episode> = emptyList()

    /** Prefer the URL the user typed; fall back to the one the playlist declared. */
    override suspend fun epgUrl(): String? = config.epgUrl ?: store.epgUrl(config.id)

    /**
     * Playlists carry no inline guide data — programme information comes from the
     * separate XMLTV document at [epgUrl], which the app fetches on its own schedule.
     */
    override suspend fun shortEpg(channel: LiveChannel, limit: Int): List<EpgEntry> = emptyList()

    /**
     * Downloads only when there is nothing stored or what is stored has aged out,
     * so a relaunch opens against the database instead of the network.
     */
    private suspend fun ensureLoaded(force: Boolean): ContentCounts = loadLock.withLock {
        val refreshedAt = store.lastRefreshedAt(config.id)
        val isStale = refreshedAt == null || now() - refreshedAt > maxCacheAgeMillis
        if (force || isStale) download()
        store.counts(config.id)
    }

    private suspend fun download() {
        val headers = config.userAgent?.let { mapOf("User-Agent" to it) } ?: emptyMap()

        // The response body is a live socket, so both the read and the parse are IO.
        withContext(Dispatchers.IO) {
            http.get(config.playlistUrl, headers).use { response ->
                if (!response.isSuccessful) throw HttpException(response.statusCode, config.playlistUrl)
                store.replace(config.id) { sink -> parseInto(response.body, sink) }
            }
        }
    }

    /** Streams the playlist into [sink], never holding more than [batchSize] entries. */
    private fun parseInto(body: InputStream, sink: ContentSink) {
        val live = ArrayList<LiveChannel>(batchSize)
        val vod = ArrayList<VodItem>(batchSize)

        M3uParser.parse(
            body,
            onHeader = { sink.header(it.epgUrl) },
            onEntry = { entry ->
                when (entry.kind) {
                    ContentKind.LIVE -> {
                        live += entry.toLiveChannel(config)
                        if (live.size >= batchSize) {
                            sink.live(live.toList())
                            live.clear()
                        }
                    }
                    ContentKind.VOD, ContentKind.SERIES -> {
                        vod += entry.toVodItem(config)
                        if (vod.size >= batchSize) {
                            sink.vod(vod.toList())
                            vod.clear()
                        }
                    }
                }
            },
        )

        if (live.isNotEmpty()) sink.live(live.toList())
        if (vod.isNotEmpty()) sink.vod(vod.toList())
    }

    companion object {
        /** Large enough to keep inserts cheap, small enough to bound memory. */
        const val DEFAULT_BATCH = 500

        /** Providers edit their line-ups daily rather than hourly. */
        const val DEFAULT_MAX_CACHE_AGE = 12L * 60 * 60 * 1000

        /** Group titles are the only identity a playlist gives a category. */
        internal fun categoryId(groupTitle: String): String = groupTitle.lowercase().trim()

        /**
         * Playlist rows have no server-assigned id, so the stream URL is the identity:
         * it is stable across refreshes, which favourites and resume points need, and
         * unique, which `tvg-id` is not — playlists routinely repeat one across many
         * entries. The `tvg-id` is kept separately for guide matching.
         */
        internal fun stableId(entry: M3uEntry): String =
            "url:${entry.url.hashCode().toUInt().toString(16)}"

        private fun M3uEntry.streamRequest(config: SourceConfig.M3u): StreamRequest {
            val merged = buildMap {
                config.userAgent?.let { put("User-Agent", it) }
                // Per-entry directives win over the playlist-wide agent.
                putAll(headers)
            }
            return StreamRequest(url, merged)
        }

        internal fun M3uEntry.toLiveChannel(config: SourceConfig.M3u) = LiveChannel(
            id = stableId(this),
            name = name,
            logoUrl = logoUrl,
            categoryId = groupTitle?.let(::categoryId),
            categoryName = groupTitle,
            epgChannelId = tvgId,
            stream = streamRequest(config),
        )

        internal fun M3uEntry.toVodItem(config: SourceConfig.M3u) = VodItem(
            id = stableId(this),
            name = name,
            logoUrl = logoUrl,
            categoryId = groupTitle?.let(::categoryId),
            categoryName = groupTitle,
            stream = streamRequest(config),
        )
    }
}
