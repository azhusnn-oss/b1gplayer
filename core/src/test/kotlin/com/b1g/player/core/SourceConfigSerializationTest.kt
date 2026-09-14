package com.b1g.player.core

import com.b1g.player.core.model.SourceConfig
import com.b1g.player.core.model.XtreamServer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The Android app persists saved accounts as JSON, so the sealed hierarchy has to
 * round-trip through one list serializer. Verified here rather than on a device.
 */
class SourceConfigSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(SourceConfig.serializer())

    private val sources = listOf(
        SourceConfig.Xtream(
            id = "1",
            displayName = "Panel",
            server = XtreamServer.parse("http://host.tv:8080")!!,
            username = "user",
            password = "pass",
        ),
        SourceConfig.M3u(
            id = "2",
            displayName = "Playlist",
            playlistUrl = "https://host.tv/list.m3u",
            epgUrl = "https://host.tv/guide.xml",
            userAgent = "B1GPlayer/1.0",
        ),
    )

    @Test
    fun `round-trips both kinds of source in one list`() {
        val decoded = json.decodeFromString(serializer, json.encodeToString(serializer, sources))
        assertEquals(sources, decoded)
    }

    @Test
    fun `tags each variant so an older blob stays readable`() {
        val encoded = json.encodeToString(serializer, sources)
        assertEquals(true, encoded.contains("\"type\":\"xtream\""))
        assertEquals(true, encoded.contains("\"type\":\"m3u\""))
    }
}
