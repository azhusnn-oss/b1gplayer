package com.b1g.player.data

import com.b1g.player.core.model.Category
import com.b1g.player.core.model.ContentKind
import com.b1g.player.core.model.LiveChannel
import com.b1g.player.core.model.StreamRequest
import com.b1g.player.core.model.VodItem
import com.b1g.player.core.store.ContentCounts
import com.b1g.player.core.store.ContentSink
import com.b1g.player.core.store.ContentStore
import com.b1g.player.data.db.B1gDatabase
import com.b1g.player.data.db.ChannelEntity
import com.b1g.player.data.db.SourceMetaEntity
import com.b1g.player.data.db.VodEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Room-backed [ContentStore]. Behaviour mirrors
 * [com.b1g.player.core.store.InMemoryContentStore], which is the tested reference.
 */
class RoomContentStore(
    private val database: B1gDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) : ContentStore {

    private val dao = database.contentDao()
    private val json = Json { ignoreUnknownKeys = true }
    private val headersSerializer = MapSerializer(String.serializer(), String.serializer())

    /**
     * One transaction: the old rows go, the new ones arrive as the playlist parses,
     * and a failure anywhere rolls the whole thing back — so an interrupted refresh
     * leaves the previous playlist in place rather than half of a new one.
     */
    override suspend fun replace(sourceId: String, fill: (ContentSink) -> Unit) =
        withContext(Dispatchers.IO) {
            var playlistEpgUrl: String? = null

            database.runInTransaction(Runnable {
                dao.deleteChannels(sourceId)
                dao.deleteVod(sourceId)

                fill(object : ContentSink {
                    override fun header(epgUrl: String?) {
                        playlistEpgUrl = epgUrl
                    }

                    override fun live(channels: List<LiveChannel>) {
                        dao.insertChannels(channels.map { it.toEntity(sourceId) })
                    }

                    override fun vod(items: List<VodItem>) {
                        dao.insertVod(items.map { it.toEntity(sourceId) })
                    }
                })

                dao.upsertMeta(SourceMetaEntity(sourceId, now(), playlistEpgUrl))
            })
        }

    override suspend fun categories(sourceId: String, kind: ContentKind): List<Category> =
        withContext(Dispatchers.IO) {
            val rows = when (kind) {
                ContentKind.LIVE -> dao.channelCategories(sourceId)
                ContentKind.VOD -> dao.vodCategories(sourceId)
                ContentKind.SERIES -> return@withContext emptyList()
            }
            rows.map { Category(id = it.categoryId, name = it.categoryName ?: it.categoryId, kind = kind) }
        }

    override suspend fun liveChannels(
        sourceId: String,
        categoryId: String?,
        query: String?,
        limit: Int,
        offset: Int,
    ): List<LiveChannel> = withContext(Dispatchers.IO) {
        dao.channels(sourceId, categoryId, likePattern(query), limit, offset).map { it.toChannel() }
    }

    override suspend fun vod(
        sourceId: String,
        categoryId: String?,
        query: String?,
        limit: Int,
        offset: Int,
    ): List<VodItem> = withContext(Dispatchers.IO) {
        dao.vod(sourceId, categoryId, likePattern(query), limit, offset).map { it.toVod() }
    }

    override suspend fun counts(sourceId: String): ContentCounts = withContext(Dispatchers.IO) {
        ContentCounts(live = dao.channelCount(sourceId), vod = dao.vodCount(sourceId))
    }

    override suspend fun lastRefreshedAt(sourceId: String): Long? =
        withContext(Dispatchers.IO) { dao.meta(sourceId)?.refreshedAt }

    override suspend fun epgUrl(sourceId: String): String? =
        withContext(Dispatchers.IO) { dao.meta(sourceId)?.epgUrl }

    override suspend fun clear(sourceId: String) = withContext(Dispatchers.IO) {
        database.runInTransaction(Runnable {
            dao.deleteChannels(sourceId)
            dao.deleteVod(sourceId)
            dao.deleteMeta(sourceId)
        })
    }

    /**
     * Channel names contain `%` and `_` often enough that an unescaped LIKE pattern
     * would quietly turn a search into a wildcard.
     */
    private fun likePattern(query: String?): String? {
        val trimmed = query?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val escaped = trimmed
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$escaped%"
    }

    private fun encodeHeaders(headers: Map<String, String>): String =
        json.encodeToString(headersSerializer, headers)

    private fun decodeHeaders(raw: String): Map<String, String> =
        runCatching { json.decodeFromString(headersSerializer, raw) }.getOrDefault(emptyMap())

    private fun LiveChannel.toEntity(sourceId: String) = ChannelEntity(
        sourceId = sourceId,
        contentId = id,
        name = name,
        logoUrl = logoUrl,
        categoryId = categoryId,
        categoryName = categoryName,
        epgChannelId = epgChannelId,
        number = number,
        streamUrl = stream.url,
        headersJson = encodeHeaders(stream.headers),
    )

    private fun ChannelEntity.toChannel() = LiveChannel(
        id = contentId,
        name = name,
        logoUrl = logoUrl,
        categoryId = categoryId,
        categoryName = categoryName,
        epgChannelId = epgChannelId,
        number = number,
        stream = StreamRequest(streamUrl, decodeHeaders(headersJson)),
    )

    private fun VodItem.toEntity(sourceId: String) = VodEntity(
        sourceId = sourceId,
        contentId = id,
        name = name,
        logoUrl = logoUrl,
        categoryId = categoryId,
        categoryName = categoryName,
        rating = rating,
        addedEpochSeconds = addedEpochSeconds,
        streamUrl = stream.url,
        headersJson = encodeHeaders(stream.headers),
    )

    private fun VodEntity.toVod() = VodItem(
        id = contentId,
        name = name,
        logoUrl = logoUrl,
        categoryId = categoryId,
        categoryName = categoryName,
        rating = rating,
        addedEpochSeconds = addedEpochSeconds,
        stream = StreamRequest(streamUrl, decodeHeaders(headersJson)),
    )
}
