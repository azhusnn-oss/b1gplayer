package com.b1g.player.core.source

import com.b1g.player.core.http.HttpClient
import com.b1g.player.core.http.HttpException
import com.b1g.player.core.m3u.M3uEntry
import com.b1g.player.core.m3u.M3uHeader
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * A [ContentSource] backed by a single M3U playlist.
 *
 * A playlist has no query interface — it is one flat document — so it is fetched
 * once, normalised, and held as a snapshot that the filtering methods read from.
 * [refresh] re-fetches it.
 */
class M3uContentSource(
    override val config: SourceConfig.M3u,
    private val http: HttpClient,
) : ContentSource {

    private data class Snapshot(
        val header: M3uHeader,
        val live: List<LiveChannel>,
        val vod: List<VodItem>,
        val categories: Map<ContentKind, List<Category>>,
    )

    private val loadLock = Mutex()

    @Volatile
    private var snapshot: Snapshot? = null

    override suspend fun connect(): ConnectResult = try {
        val loaded = reload()
        if (loaded.live.isEmpty() && loaded.vod.isEmpty()) {
            ConnectResult.Failure("Playlist loaded but contained no channels")
        } else {
            ConnectResult.Success("${loaded.live.size} channels, ${loaded.vod.size} on-demand items")
        }
    } catch (e: HttpException) {
        ConnectResult.Failure("Playlist rejected the request (HTTP ${e.statusCode})", e)
    } catch (e: IOException) {
        ConnectResult.Failure(describeNetworkFailure("Could not download the playlist", e), e)
    }

    /** Re-downloads and re-parses the playlist, replacing the in-memory snapshot. */
    suspend fun refresh() {
        reload()
    }

    private suspend fun reload(): Snapshot = loadLock.withLock { load().also { snapshot = it } }

    override suspend fun categories(kind: ContentKind): List<Category> =
        require().categories[kind].orEmpty()

    override suspend fun liveChannels(categoryId: String?): List<LiveChannel> =
        require().live.filter { categoryId == null || it.categoryId == categoryId }

    override suspend fun vod(categoryId: String?): List<VodItem> =
        require().vod.filter { categoryId == null || it.categoryId == categoryId }

    /** A flat playlist has no season/episode structure to report. */
    override suspend fun series(categoryId: String?): List<Series> = emptyList()

    override suspend fun episodes(seriesId: String): List<Episode> = emptyList()

    /** Prefer the URL the user typed; fall back to the `url-tvg` the playlist declares. */
    override fun epgUrl(): String? = config.epgUrl ?: snapshot?.header?.epgUrl

    /**
     * Playlists carry no inline guide data — programme information comes from the
     * separate XMLTV document at [epgUrl], which the app fetches on its own schedule.
     */
    override suspend fun shortEpg(channel: LiveChannel, limit: Int): List<EpgEntry> = emptyList()

    private suspend fun require(): Snapshot = snapshot ?: reload()

    private suspend fun load(): Snapshot {
        val headers = config.userAgent?.let { mapOf("User-Agent" to it) } ?: emptyMap()
        var header = M3uHeader()
        val live = ArrayList<LiveChannel>()
        val vod = ArrayList<VodItem>()

        http.get(config.playlistUrl, headers).use { response ->
            if (!response.isSuccessful) throw HttpException(response.statusCode, config.playlistUrl)
            M3uParser.parse(
                response.body,
                onHeader = { header = it },
                onEntry = { entry ->
                    when (entry.kind) {
                        ContentKind.VOD, ContentKind.SERIES -> vod += entry.toVodItem(config)
                        ContentKind.LIVE -> live += entry.toLiveChannel(config)
                    }
                },
            )
        }

        return Snapshot(
            header = header,
            live = live,
            vod = vod,
            categories = mapOf(
                ContentKind.LIVE to categoriesOf(live.map { it.categoryName }, ContentKind.LIVE),
                ContentKind.VOD to categoriesOf(vod.map { it.categoryName }, ContentKind.VOD),
                ContentKind.SERIES to emptyList(),
            ),
        )
    }

    private fun categoriesOf(names: List<String?>, kind: ContentKind): List<Category> =
        names.filterNotNull()
            .distinct()
            .sorted()
            .map { Category(id = categoryId(it), name = it, kind = kind) }

    companion object {
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
