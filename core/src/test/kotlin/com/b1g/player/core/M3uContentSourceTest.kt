package com.b1g.player.core

import com.b1g.player.core.model.ContentKind
import com.b1g.player.core.model.SourceConfig
import com.b1g.player.core.source.ConnectResult
import com.b1g.player.core.source.M3uContentSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class M3uContentSourceTest {

    private val playlist = """
        #EXTM3U url-tvg="http://host/xmltv.php"
        #EXTINF:-1 tvg-id="bbc1.uk" tvg-logo="http://host/bbc.png" group-title="UK",BBC One
        http://host:8080/live/u/p/1.ts
        #EXTINF:-1 tvg-id="sky.uk" group-title="UK",Sky Sports
        #EXTVLCOPT:http-user-agent=SpecialAgent/2.0
        http://host:8080/live/u/p/2.ts
        #EXTINF:-1 group-title="DE",Das Erste
        http://host:8080/live/u/p/3.ts
        #EXTINF:-1 group-title="MOVIES",Some Film
        http://host:8080/movie/u/p/55.mkv
    """.trimIndent()

    private fun config(epgUrl: String? = null) = SourceConfig.M3u(
        id = "1",
        displayName = "Playlist",
        playlistUrl = "http://host/get.php",
        epgUrl = epgUrl,
        userAgent = "B1GPlayer/1.0",
    )

    @Test
    fun `connect reports what was loaded`() = runTest {
        val source = M3uContentSource(config(), FakeHttpClient(playlist))

        val result = source.connect()

        assertTrue(result is ConnectResult.Success)
        assertEquals("3 channels, 1 movie", (result as ConnectResult.Success).summary)
    }

    @Test
    fun `separates live entries from on-demand entries`() = runTest {
        val source = M3uContentSource(config(), FakeHttpClient(playlist))
        source.connect()

        assertEquals(listOf("BBC One", "Sky Sports", "Das Erste"), source.liveChannels().map { it.name })
        assertEquals(listOf("Some Film"), source.vod().map { it.name })
    }

    @Test
    fun `derives categories from group titles and filters by them`() = runTest {
        val source = M3uContentSource(config(), FakeHttpClient(playlist))
        source.connect()

        val categories = source.categories(ContentKind.LIVE)
        assertEquals(listOf("DE", "UK"), categories.map { it.name })

        val uk = categories.first { it.name == "UK" }
        assertEquals(listOf("BBC One", "Sky Sports"), source.liveChannels(uk.id).map { it.name })
    }

    @Test
    fun `per-entry user agent overrides the account-wide one`() = runTest {
        val source = M3uContentSource(config(), FakeHttpClient(playlist))
        source.connect()
        val channels = source.liveChannels()

        assertEquals("B1GPlayer/1.0", channels[0].stream.headers["User-Agent"])
        assertEquals("SpecialAgent/2.0", channels[1].stream.headers["User-Agent"])
    }

    @Test
    fun `uses the configured guide url and otherwise the one the playlist declares`() = runTest {
        val declared = M3uContentSource(config(), FakeHttpClient(playlist))
        declared.connect()
        assertEquals("http://host/xmltv.php", declared.epgUrl())

        val overridden = M3uContentSource(config(epgUrl = "http://mine/guide.xml"), FakeHttpClient(playlist))
        overridden.connect()
        assertEquals("http://mine/guide.xml", overridden.epgUrl())
    }

    @Test
    fun `ids stay stable across a refresh so favourites survive`() = runTest {
        val source = M3uContentSource(config(), FakeHttpClient(playlist))
        source.connect()

        val before = source.liveChannels().map { it.id }
        source.refresh()
        val after = source.liveChannels().map { it.id }

        assertEquals(before, after)
    }

    @Test
    fun `gives entries sharing a tvg-id distinct ids`() = runTest {
        val shared = """
            #EXTM3U
            #EXTINF:-1 tvg-id="sports.uk",Sports One
            http://host:8080/live/u/p/10.ts
            #EXTINF:-1 tvg-id="sports.uk",Sports Two
            http://host:8080/live/u/p/11.ts
        """.trimIndent()

        val source = M3uContentSource(config(), FakeHttpClient(shared))
        source.connect()
        val channels = source.liveChannels()

        assertEquals(2, channels.map { it.id }.distinct().size)
        // The guide id is still shared, which is what EPG matching needs.
        assertEquals(listOf("sports.uk", "sports.uk"), channels.map { it.epgChannelId })
    }

    @Test
    fun `an empty playlist is a failure, not an empty screen`() = runTest {
        val source = M3uContentSource(config(), FakeHttpClient("#EXTM3U\n"))

        val result = source.connect()

        assertTrue(result is ConnectResult.Failure)
        assertTrue((result as ConnectResult.Failure).message.contains("no channels"))
    }

    @Test
    fun `an http error becomes a failure with the status code`() = runTest {
        val source = M3uContentSource(config(), FakeHttpClient(listOf("" to ""), statusCode = 403))

        val result = source.connect()

        assertTrue(result is ConnectResult.Failure)
        assertTrue((result as ConnectResult.Failure).message.contains("403"))
    }

    @Test
    fun `sends the configured user agent when downloading the playlist`() = runTest {
        val http = FakeHttpClient(playlist)
        M3uContentSource(config(), http).connect()

        assertEquals("B1GPlayer/1.0", http.requestedHeaders.single()["User-Agent"])
    }
}
