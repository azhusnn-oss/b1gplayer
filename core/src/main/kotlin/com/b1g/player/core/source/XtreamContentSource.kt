package com.b1g.player.core.source

import com.b1g.player.core.model.Category
import com.b1g.player.core.model.ContentKind
import com.b1g.player.core.model.EpgEntry
import com.b1g.player.core.model.Episode
import com.b1g.player.core.model.LiveChannel
import com.b1g.player.core.model.Series
import com.b1g.player.core.model.SourceConfig
import com.b1g.player.core.model.VodItem
import com.b1g.player.core.xtream.XtreamAccount
import com.b1g.player.core.xtream.XtreamAuthResult
import com.b1g.player.core.xtream.XtreamClient
import com.b1g.player.core.xtream.XtreamUrls
import java.io.IOException

class XtreamContentSource(
    override val config: SourceConfig.Xtream,
    private val client: XtreamClient,
) : ContentSource {

    /** Populated by [connect]; decides the container requested for live playback. */
    @Volatile
    var account: XtreamAccount? = null
        private set

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

    override suspend fun categories(kind: ContentKind): List<Category> = client.categories(config, kind)

    override suspend fun liveChannels(categoryId: String?): List<LiveChannel> =
        client.liveStreams(config, categoryId, account?.preferredLiveExtension ?: "m3u8")

    override suspend fun vod(categoryId: String?): List<VodItem> = client.vodStreams(config, categoryId)

    override suspend fun series(categoryId: String?): List<Series> = client.series(config, categoryId)

    override suspend fun episodes(seriesId: String): List<Episode> = client.episodes(config, seriesId)

    override fun epgUrl(): String = XtreamUrls.xmltv(config)

    override suspend fun shortEpg(channel: LiveChannel, limit: Int): List<EpgEntry> =
        client.shortEpg(config, channel.id, limit)

    private fun describe(account: XtreamAccount): String = buildString {
        append(account.username)
        account.maxConnections?.let { append(" · ").append(account.activeConnections ?: 0).append('/').append(it).append(" connections") }
        if (account.isTrial) append(" · trial")
    }
}
