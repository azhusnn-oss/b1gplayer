package com.b1g.player.core.source

import com.b1g.player.core.model.Category
import com.b1g.player.core.model.ContentKind
import com.b1g.player.core.model.EpgEntry
import com.b1g.player.core.model.Episode
import com.b1g.player.core.model.LiveChannel
import com.b1g.player.core.model.Series
import com.b1g.player.core.model.SourceConfig
import com.b1g.player.core.model.VodItem
import com.b1g.player.core.store.matches
import com.b1g.player.core.xtream.XtreamAccount
import com.b1g.player.core.xtream.XtreamAuthResult
import com.b1g.player.core.xtream.XtreamClient
import com.b1g.player.core.xtream.XtreamUrls
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

class XtreamContentSource(
    override val config: SourceConfig.Xtream,
    private val client: XtreamClient,
) : ContentSource {

    /** Populated by [connect]; decides the container requested for live playback. */
    @Volatile
    var account: XtreamAccount? = null
        private set

    /**
     * The panel already partitions its catalogue by category and has no search
     * endpoint, so a category is fetched once and then filtered and paged here.
     * Only the most recent category is held, which is the one being scrolled.
     */
    private val pageLock = Mutex()
    private var cachedKey: Pair<ContentKind, String?>? = null
    private var cachedLive: List<LiveChannel> = emptyList()
    private var cachedVod: List<VodItem> = emptyList()
    private var cachedSeries: List<Series> = emptyList()

    override suspend fun connect(): ConnectResult = try {
        when (val result = client.authenticate(config)) {
            is XtreamAuthResult.Success -> {
                account = result.account
                ConnectResult.Success(describe(result.account))
            }
            is XtreamAuthResult.Rejected -> ConnectResult.Failure(result.reason)
        }
    } catch (e: IOException) {
        ConnectResult.Failure(
            describeNetworkFailure("Could not reach ${config.server.baseUrl}", e),
            e,
        )
    } catch (e: Exception) {
        ConnectResult.Failure("Unexpected response from server", e)
    }

    override suspend fun categories(kind: ContentKind): List<Category> =
        client.categories(config, kind)

    override suspend fun liveChannels(
        categoryId: String?,
        query: String?,
        limit: Int,
        offset: Int,
    ): List<LiveChannel> = pageLock.withLock {
        val key = ContentKind.LIVE to categoryId
        if (cachedKey != key) {
            cachedLive = client.liveStreams(config, categoryId, account?.preferredLiveExtension ?: "m3u8")
            cachedVod = emptyList()
            cachedSeries = emptyList()
            cachedKey = key
        }
        cachedLive.asSequence().filter { matches(it.name, query) }.drop(offset).take(limit).toList()
    }

    override suspend fun vod(
        categoryId: String?,
        query: String?,
        limit: Int,
        offset: Int,
    ): List<VodItem> = pageLock.withLock {
        val key = ContentKind.VOD to categoryId
        if (cachedKey != key) {
            cachedVod = client.vodStreams(config, categoryId)
            cachedLive = emptyList()
            cachedSeries = emptyList()
            cachedKey = key
        }
        cachedVod.asSequence().filter { matches(it.name, query) }.drop(offset).take(limit).toList()
    }

    override suspend fun series(
        categoryId: String?,
        query: String?,
        limit: Int,
        offset: Int,
    ): List<Series> = pageLock.withLock {
        val key = ContentKind.SERIES to categoryId
        if (cachedKey != key) {
            cachedSeries = client.series(config, categoryId)
            cachedLive = emptyList()
            cachedVod = emptyList()
            cachedKey = key
        }
        cachedSeries.asSequence().filter { matches(it.name, query) }.drop(offset).take(limit).toList()
    }

    override suspend fun episodes(seriesId: String): List<Episode> = client.episodes(config, seriesId)

    override suspend fun epgUrl(): String = XtreamUrls.xmltv(config)

    override suspend fun shortEpg(channel: LiveChannel, limit: Int): List<EpgEntry> =
        client.shortEpg(config, channel.id, limit)

    private fun describe(account: XtreamAccount): String = buildString {
        append(account.username)
        account.maxConnections?.let {
            append(" · ").append(account.activeConnections ?: 0).append('/').append(it).append(" connections")
        }
        if (account.isTrial) append(" · trial")
    }
}
