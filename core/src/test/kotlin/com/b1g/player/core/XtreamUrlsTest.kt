package com.b1g.player.core

import com.b1g.player.core.model.SourceConfig
import com.b1g.player.core.model.XtreamServer
import com.b1g.player.core.xtream.XtreamUrls
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class XtreamUrlsTest {

    private val config = SourceConfig.Xtream(
        id = "1",
        displayName = "Test",
        server = XtreamServer.parse("http://host.tv:8080")!!,
        username = "user one",
        password = "p@ss/word",
    )

    @Test
    fun `escapes credentials in api urls`() {
        assertEquals(
            "http://host.tv:8080/player_api.php?username=user+one&password=p%40ss%2Fword",
            XtreamUrls.playerApi(config),
        )
    }

    @Test
    fun `appends action and parameters`() {
        assertEquals(
            "http://host.tv:8080/player_api.php?username=user+one&password=p%40ss%2Fword" +
                "&action=get_live_streams&category_id=12",
            XtreamUrls.playerApi(config, "get_live_streams", mapOf("category_id" to "12")),
        )
    }

    @Test
    fun `builds playback urls for each content type`() {
        assertEquals(
            "http://host.tv:8080/live/user+one/p%40ss%2Fword/1001.m3u8",
            XtreamUrls.live(config, "1001"),
        )
        assertEquals(
            "http://host.tv:8080/live/user+one/p%40ss%2Fword/1001.ts",
            XtreamUrls.live(config, "1001", "ts"),
        )
        assertEquals(
            "http://host.tv:8080/movie/user+one/p%40ss%2Fword/55.mkv",
            XtreamUrls.vod(config, "55", "mkv"),
        )
        assertEquals(
            "http://host.tv:8080/series/user+one/p%40ss%2Fword/77.mp4",
            XtreamUrls.episode(config, "77", "mp4"),
        )
    }

    @Test
    fun `builds guide and playlist export urls`() {
        assertEquals(
            "http://host.tv:8080/xmltv.php?username=user+one&password=p%40ss%2Fword",
            XtreamUrls.xmltv(config),
        )
        assertEquals(
            "http://host.tv:8080/get.php?username=user+one&password=p%40ss%2Fword&type=m3u_plus&output=ts",
            XtreamUrls.m3uExport(config),
        )
    }
}
