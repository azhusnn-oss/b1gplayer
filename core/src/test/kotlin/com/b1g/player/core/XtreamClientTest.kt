package com.b1g.player.core

import com.b1g.player.core.model.ContentKind
import com.b1g.player.core.model.SourceConfig
import com.b1g.player.core.model.XtreamServer
import com.b1g.player.core.xtream.XtreamAuthResult
import com.b1g.player.core.xtream.XtreamClient
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XtreamClientTest {

    private val config = SourceConfig.Xtream(
        id = "1",
        displayName = "Test",
        server = XtreamServer.parse("http://host.tv:8080")!!,
        username = "user",
        password = "pass",
        userAgent = "B1GPlayer/1.0",
    )

    @Test
    fun `accepts a valid login and reports account limits`() = runTest {
        val http = FakeHttpClient(
            """
            {"user_info":{"username":"user","auth":1,"status":"Active","exp_date":"1789000000",
            "is_trial":"0","active_cons":"1","max_connections":"3",
            "allowed_output_formats":["m3u8","ts","rtmp"]},
            "server_info":{"url":"host.tv","port":"8080","server_protocol":"http"}}
            """.trimIndent()
        )

        val result = XtreamClient(http).authenticate(config)

        val success = assertIs<XtreamAuthResult.Success>(result)
        assertEquals("user", success.account.username)
        assertEquals(1789000000L, success.account.expiresAtEpochSeconds)
        assertEquals(3, success.account.maxConnections)
        assertFalse(success.account.isTrial)
        assertEquals("m3u8", success.account.preferredLiveExtension)
        // The configured agent must be sent, or strict panels reject the call.
        assertEquals("B1GPlayer/1.0", http.requestedHeaders.single()["User-Agent"])
    }

    @Test
    fun `rejects bad credentials without throwing`() = runTest {
        val result = XtreamClient(FakeHttpClient("""{"user_info":{"auth":0}}"""))
            .authenticate(config)

        val rejected = assertIs<XtreamAuthResult.Rejected>(result)
        assertEquals("Invalid username or password", rejected.reason)
    }

    @Test
    fun `reports a non-active account as rejected`() = runTest {
        val result = XtreamClient(
            FakeHttpClient("""{"user_info":{"auth":1,"status":"Expired","username":"user"}}""")
        ).authenticate(config)

        assertTrue(assertIs<XtreamAuthResult.Rejected>(result).reason.contains("Expired"))
    }

    @Test
    fun `falls back to ts when the panel does not offer hls`() = runTest {
        val result = XtreamClient(
            FakeHttpClient(
                """{"user_info":{"auth":1,"status":"Active","allowed_output_formats":["ts"]}}"""
            )
        ).authenticate(config)

        assertEquals("ts", assertIs<XtreamAuthResult.Success>(result).account.preferredLiveExtension)
    }

    @Test
    fun `maps live streams and builds playback urls`() = runTest {
        val http = FakeHttpClient(
            """
            [{"num":1,"name":"BBC One","stream_id":1001,"stream_icon":"http://host/bbc.png",
              "epg_channel_id":"bbc1.uk","category_id":"5","tv_archive":1},
             {"num":2,"name":"Sky Sports","stream_id":"1002","stream_icon":"","epg_channel_id":null,
              "category_id":"5"}]
            """.trimIndent()
        )

        val channels = XtreamClient(http).liveStreams(config, categoryId = "5", extension = "ts")

        assertEquals(2, channels.size)
        assertEquals("http://host.tv:8080/live/user/pass/1001.ts", channels[0].stream.url)
        assertEquals("ts", channels[0].stream.extension)
        assertEquals("bbc1.uk", channels[0].epgChannelId)
        // A string stream_id must map exactly like a numeric one.
        assertEquals("http://host.tv:8080/live/user/pass/1002.ts", channels[1].stream.url)
        // Empty strings are absent values, not content.
        assertNull(channels[1].logoUrl)
        assertNull(channels[1].epgChannelId)
        assertTrue(http.requestedUrls.single().contains("category_id=5"))
    }

    @Test
    fun `prefers direct_source when the panel supplies one`() = runTest {
        val channels = XtreamClient(
            FakeHttpClient(
                """[{"name":"Direct","stream_id":7,"direct_source":"http://origin.tv/feed.m3u8"}]"""
            )
        ).liveStreams(config)

        assertEquals("http://origin.tv/feed.m3u8", channels.single().stream.url)
    }

    @Test
    fun `maps vod using the container extension the panel reports`() = runTest {
        val items = XtreamClient(
            FakeHttpClient(
                """
                [{"name":"A Movie","stream_id":55,"container_extension":"mkv","rating":"7.4",
                  "added":"1700000000","category_id":"9"},
                 {"name":"No Rating","stream_id":56,"container_extension":"mp4","rating":""}]
                """.trimIndent()
            )
        ).vodStreams(config)

        assertEquals("http://host.tv:8080/movie/user/pass/55.mkv", items[0].stream.url)
        assertEquals(7.4, items[0].rating)
        assertEquals(1700000000L, items[0].addedEpochSeconds)
        assertNull(items[1].rating)
    }

    @Test
    fun `flattens the season-keyed episodes object and sorts it`() = runTest {
        val http = FakeHttpClient(
            """
            {"info":{"name":"Show","series_id":"3"},
             "episodes":{
               "2":[{"id":"202","episode_num":2,"title":"S2E2","container_extension":"mkv",
                     "info":{"duration_secs":2700,"plot":"Second"}},
                    {"id":"201","episode_num":"1","title":"S2E1","container_extension":"mp4"}],
               "1":[{"id":"101","episode_num":1,"title":"S1E1","container_extension":"mp4",
                     "info":{"duration_secs":2640}}]}}
            """.trimIndent()
        )

        val episodes = XtreamClient(http).episodes(config, "3")

        assertEquals(listOf("S1E1", "S2E1", "S2E2"), episodes.map { it.title })
        assertEquals(listOf(1, 2, 2), episodes.map { it.seasonNumber })
        assertEquals("http://host.tv:8080/series/user/pass/101.mp4", episodes[0].stream.url)
        assertEquals("http://host.tv:8080/series/user/pass/202.mkv", episodes[2].stream.url)
        assertEquals(2700, episodes[2].durationSeconds)
    }

    @Test
    fun `treats an empty episodes array as no episodes`() = runTest {
        val episodes = XtreamClient(FakeHttpClient("""{"info":{"name":"Show"},"episodes":[]}"""))
            .episodes(config, "3")

        assertTrue(episodes.isEmpty())
    }

    @Test
    fun `decodes base64 epg titles and descriptions`() = runTest {
        val episodes = XtreamClient(
            FakeHttpClient(
                """
                {"epg_listings":[{"epg_id":"1","channel_id":"bbc1.uk",
                  "title":"UHJlbWllciBMZWFndWU6IEFyc2VuYWwgdiBTcHVycw==",
                  "description":"TGl2ZSBjb3ZlcmFnZSBmcm9tIHRoZSBFbWlyYXRlcy4=",
                  "start_timestamp":"1700000000","stop_timestamp":"1700007200"}]}
                """.trimIndent()
            )
        ).shortEpg(config, "1001")

        val entry = episodes.single()
        assertEquals("Premier League: Arsenal v Spurs", entry.title)
        assertEquals("Live coverage from the Emirates.", entry.description)
        assertEquals(1700000000L, entry.startEpochSeconds)
        assertTrue(entry.isLiveAt(1700003600L))
        assertFalse(entry.isLiveAt(1700007200L))
    }

    @Test
    fun `maps categories for each content kind`() = runTest {
        val http = FakeHttpClient(
            """[{"category_id":"5","category_name":"UK | SPORTS","parent_id":0}]"""
        )

        val categories = XtreamClient(http).categories(config, ContentKind.VOD)

        assertEquals("5", categories.single().id)
        assertEquals("UK | SPORTS", categories.single().name)
        assertEquals(ContentKind.VOD, categories.single().kind)
        assertTrue(http.requestedUrls.single().contains("action=get_vod_categories"))
    }

    @Test
    fun `skips rows that have no usable id instead of failing the response`() = runTest {
        val channels = XtreamClient(
            FakeHttpClient("""[{"name":"Broken"},{"name":"Fine","stream_id":9}]""")
        ).liveStreams(config)

        assertEquals(listOf("Fine"), channels.map { it.name })
    }

    private inline fun <reified T> assertIs(value: Any?): T {
        assertTrue(value is T) { "Expected ${T::class.simpleName} but was $value" }
        return value as T
    }
}
