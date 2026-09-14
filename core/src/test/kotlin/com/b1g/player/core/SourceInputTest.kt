package com.b1g.player.core

import com.b1g.player.core.login.M3uInput
import com.b1g.player.core.login.SourceBuildResult
import com.b1g.player.core.login.SourceError
import com.b1g.player.core.login.SourceField
import com.b1g.player.core.login.XtreamInput
import com.b1g.player.core.model.SourceConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SourceInputTest {

    @Test
    fun `builds an xtream config from a valid form`() {
        val result = XtreamInput(
            displayName = "My provider",
            server = "host.tv:8080",
            username = " user ",
            password = "pass",
        ).build("id-1")

        val config = (result as SourceBuildResult.Valid).config as SourceConfig.Xtream
        assertEquals("My provider", config.displayName)
        assertEquals("http://host.tv:8080", config.server.baseUrl)
        assertEquals("user", config.username)
        assertNull(config.userAgent)
    }

    @Test
    fun `names an unnamed xtream source after its host`() {
        val result = XtreamInput(server = "host.tv:8080", username = "u", password = "p").build("id-1")
        assertEquals("host.tv", (result as SourceBuildResult.Valid).config.displayName)
    }

    @Test
    fun `reports every empty xtream field at once`() {
        val result = XtreamInput().build("id-1")

        val errors = (result as SourceBuildResult.Invalid).errors
        assertEquals(
            listOf(SourceField.SERVER, SourceField.USERNAME, SourceField.PASSWORD),
            errors.map { it.field },
        )
        assertTrue(errors.all { it.error == SourceError.REQUIRED })
    }

    @Test
    fun `flags a server that cannot be parsed`() {
        val result = XtreamInput(server = "not a host", username = "u", password = "p").build("id-1")

        val error = (result as SourceBuildResult.Invalid).errors.single()
        assertEquals(SourceField.SERVER, error.field)
        assertEquals(SourceError.INVALID_SERVER, error.error)
    }

    @Test
    fun `builds an m3u config and defaults its name to the host`() {
        val result = M3uInput(playlistUrl = " http://host.tv:8080/get.php?type=m3u_plus ").build("id-2")

        val config = (result as SourceBuildResult.Valid).config as SourceConfig.M3u
        assertEquals("host.tv", config.displayName)
        assertEquals("http://host.tv:8080/get.php?type=m3u_plus", config.playlistUrl)
        assertNull(config.epgUrl)
    }

    @Test
    fun `rejects a playlist url that is not http`() {
        val result = M3uInput(playlistUrl = "file:///sdcard/list.m3u").build("id-2")

        val error = (result as SourceBuildResult.Invalid).errors.single()
        assertEquals(SourceField.PLAYLIST_URL, error.field)
        assertEquals(SourceError.INVALID_URL, error.error)
    }

    @Test
    fun `keeps optional epg and user agent values when supplied`() {
        val result = M3uInput(
            playlistUrl = "https://host.tv/list.m3u",
            epgUrl = "https://host.tv/guide.xml",
            userAgent = "B1GPlayer/1.0",
        ).build("id-2")

        val config = (result as SourceBuildResult.Valid).config as SourceConfig.M3u
        assertEquals("https://host.tv/guide.xml", config.epgUrl)
        assertEquals("B1GPlayer/1.0", config.userAgent)
    }
}
