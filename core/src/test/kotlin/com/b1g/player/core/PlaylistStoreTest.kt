package com.b1g.player.core

import com.b1g.player.core.http.HttpClient
import com.b1g.player.core.http.HttpResponse
import com.b1g.player.core.model.ContentKind
import com.b1g.player.core.model.LiveChannel
import com.b1g.player.core.model.SourceConfig
import com.b1g.player.core.model.VodItem
import com.b1g.player.core.source.ConnectResult
import com.b1g.player.core.source.M3uContentSource
import com.b1g.player.core.store.ContentSink
import com.b1g.player.core.store.ContentStore
import com.b1g.player.core.store.InMemoryContentStore
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStream

/**
 * The contract a [ContentStore] has to honour, exercised through the playlist
 * source. The Room implementation in the app module has to behave the same way.
 */
class PlaylistStoreTest {

    private fun playlist(channels: Int, groups: Int = 4): String = buildString {
        appendLine("#EXTM3U url-tvg=\"http://host/guide.xml\"")
        repeat(channels) { index ->
            appendLine("#EXTINF:-1 tvg-id=\"ch$index\" group-title=\"Group ${index % groups}\",Channel $index")
            appendLine("http://host:8080/live/u/p/$index.ts")
        }
    }

    private fun config() = SourceConfig.M3u(
        id = "source-1",
        displayName = "Playlist",
        playlistUrl = "http://host/get.php",
    )

    /** Counts how many batches arrive and how large each one is. */
    private class RecordingStore(private val delegate: ContentStore = InMemoryContentStore()) :
        ContentStore by delegate {
        val liveBatchSizes = mutableListOf<Int>()

        override suspend fun replace(sourceId: String, fill: (ContentSink) -> Unit) {
            delegate.replace(sourceId) { sink ->
                fill(object : ContentSink {
                    override fun header(epgUrl: String?) = sink.header(epgUrl)
                    override fun live(channels: List<LiveChannel>) {
                        liveBatchSizes += channels.size
                        sink.live(channels)
                    }
                    override fun vod(items: List<VodItem>) = sink.vod(items)
                })
            }
        }
    }

    @Test
    fun `writes the playlist in bounded batches rather than one list`() = runTest {
        val store = RecordingStore()
        val source = M3uContentSource(
            config(),
            FakeHttpClient(playlist(channels = 1_250)),
            store,
            batchSize = 500,
        )

        source.connect()

        // 500 + 500 + 250: nothing larger than a batch is ever held.
        assertEquals(listOf(500, 500, 250), store.liveBatchSizes)
        assertTrue(store.liveBatchSizes.all { it <= 500 })
        assertEquals(1_250, store.counts("source-1").live)
    }

    @Test
    fun `a second connect reads what is stored instead of downloading again`() = runTest {
        val http = FakeHttpClient(playlist(channels = 10))
        val store = InMemoryContentStore()

        M3uContentSource(config(), http, store).connect()
        val result = M3uContentSource(config(), http, store).connect()

        assertEquals(1, http.requestedUrls.size)
        assertTrue(result is ConnectResult.Success)
        assertEquals(10, store.counts("source-1").live)
    }

    @Test
    fun `downloads again once what is stored has aged out`() = runTest {
        val http = FakeHttpClient(playlist(channels = 5))
        var clock = 1_000L
        val store = InMemoryContentStore(now = { clock })

        val maxAge = 60_000L
        M3uContentSource(config(), http, store, maxCacheAgeMillis = maxAge, now = { clock }).connect()
        clock += maxAge + 1
        M3uContentSource(config(), http, store, maxCacheAgeMillis = maxAge, now = { clock }).connect()

        assertEquals(2, http.requestedUrls.size)
    }

    @Test
    fun `a refresh that fails halfway leaves the stored playlist intact`() = runTest {
        var clock = 1_000L
        val store = InMemoryContentStore(now = { clock })
        M3uContentSource(config(), FakeHttpClient(playlist(channels = 20)), store, now = { clock }).connect()

        clock += M3uContentSource.DEFAULT_MAX_CACHE_AGE + 1

        // A body that dies mid-stream, as a dropped connection would.
        val truncating = object : HttpClient {
            override suspend fun get(url: String, headers: Map<String, String>) =
                HttpResponse(200, FailingStream(playlist(channels = 20), failAfterBytes = 200))
        }
        val result = M3uContentSource(config(), truncating, store, now = { clock }).connect()

        assertTrue(result is ConnectResult.Failure)
        assertEquals(20, store.counts("source-1").live)
        assertEquals("Channel 0", store.liveChannels("source-1", limit = 1).single().name)
    }

    @Test
    fun `pages through channels without loading them all`() = runTest {
        val store = InMemoryContentStore()
        val source = M3uContentSource(config(), FakeHttpClient(playlist(channels = 50)), store)
        source.connect()

        val first = source.liveChannels(limit = 10, offset = 0)
        val third = source.liveChannels(limit = 10, offset = 20)

        assertEquals(10, first.size)
        assertEquals("Channel 0", first.first().name)
        assertEquals("Channel 20", third.first().name)
    }

    @Test
    fun `filters by category and search in the store, not the caller`() = runTest {
        val store = InMemoryContentStore()
        val source = M3uContentSource(config(), FakeHttpClient(playlist(channels = 40, groups = 4)), store)
        source.connect()

        val group1 = source.liveChannels(categoryId = "group 1", limit = 100)
        assertEquals(10, group1.size)
        assertTrue(group1.all { it.categoryName == "Group 1" })

        val searched = source.liveChannels(query = "Channel 3", limit = 100)
        // Channel 3, and 30 through 39.
        assertEquals(11, searched.size)

        val both = source.liveChannels(categoryId = "group 1", query = "Channel 3", limit = 100)
        assertEquals(listOf("Channel 33", "Channel 37"), both.map { it.name })
    }

    @Test
    fun `keeps the guide url the stored playlist declared`() = runTest {
        val store = InMemoryContentStore()
        val source = M3uContentSource(config(), FakeHttpClient(playlist(channels = 3)), store)
        source.connect()

        assertEquals("http://host/guide.xml", source.epgUrl())
        // A fresh source over the same store still finds it, with no download.
        assertEquals("http://host/guide.xml", M3uContentSource(config(), FakeHttpClient(""), store).epgUrl())
    }

    @Test
    fun `reports the stored categories`() = runTest {
        val store = InMemoryContentStore()
        val source = M3uContentSource(config(), FakeHttpClient(playlist(channels = 12, groups = 3)), store)
        source.connect()

        assertEquals(
            listOf("Group 0", "Group 1", "Group 2"),
            source.categories(ContentKind.LIVE).map { it.name },
        )
    }

    /** Fails partway through, the way a dropped connection does. */
    private class FailingStream(content: String, private val failAfterBytes: Int) : InputStream() {
        private val delegate = content.byteInputStream()
        private var read = 0

        override fun read(): Int {
            if (read++ >= failAfterBytes) throw java.io.IOException("connection reset by peer")
            return delegate.read()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (read >= failAfterBytes) throw java.io.IOException("connection reset by peer")
            val capped = minOf(len, failAfterBytes - read)
            val count = delegate.read(b, off, capped)
            if (count > 0) read += count
            return count
        }
    }
}
