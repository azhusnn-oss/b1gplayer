package com.b1g.player.core

import com.b1g.player.core.m3u.M3uParser
import com.b1g.player.core.model.ContentKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class M3uParserTest {

    @Test
    fun `reads header epg url and basic entries`() {
        val playlist = M3uParser.parseToPlaylist(
            """
            #EXTM3U url-tvg="http://host/xmltv.php?username=u&password=p"
            #EXTINF:-1 tvg-id="bbc1.uk" tvg-name="BBC One" tvg-logo="http://host/bbc1.png" group-title="UK",BBC One HD
            http://host:8080/live/u/p/1001.ts
            """.trimIndent()
        )

        assertEquals("http://host/xmltv.php?username=u&password=p", playlist.header.epgUrl)
        assertEquals(1, playlist.entries.size)

        val entry = playlist.entries.single()
        assertEquals("BBC One HD", entry.name)
        assertEquals("bbc1.uk", entry.tvgId)
        assertEquals("UK", entry.groupTitle)
        assertEquals("http://host/bbc1.png", entry.logoUrl)
        assertEquals("http://host:8080/live/u/p/1001.ts", entry.url)
        assertEquals(ContentKind.LIVE, entry.kind)
    }

    @Test
    fun `splits on the first comma outside quotes so commas in attributes survive`() {
        val entry = M3uParser.parseToPlaylist(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="x" group-title="UK, ENTERTAINMENT",Channel 4, The Best Of
            http://host/1.ts
            """.trimIndent()
        ).entries.single()

        assertEquals("UK, ENTERTAINMENT", entry.groupTitle)
        assertEquals("Channel 4, The Best Of", entry.name)
    }

    @Test
    fun `collects headers from EXTVLCOPT, KODIPROP and piped urls`() {
        val entries = M3uParser.parseToPlaylist(
            """
            #EXTM3U
            #EXTINF:-1,Agent via VLC opt
            #EXTVLCOPT:http-user-agent=CustomAgent/1.0
            #EXTVLCOPT:http-referrer=http://ref.example
            http://host/1.ts
            #EXTINF:-1,Agent via Kodi prop
            #KODIPROP:inputstream.adaptive.stream_headers=User-Agent=KodiAgent&Referer=http%3A%2F%2Fkodi.example
            http://host/2.ts
            #EXTINF:-1,Agent via pipe
            http://host/3.ts|User-Agent=PipeAgent&Referer=http://pipe.example
            """.trimIndent()
        ).entries

        assertEquals("CustomAgent/1.0", entries[0].headers["User-Agent"])
        assertEquals("http://ref.example", entries[0].headers["Referer"])

        assertEquals("KodiAgent", entries[1].headers["User-Agent"])
        assertEquals("http://kodi.example", entries[1].headers["Referer"])

        assertEquals("PipeAgent", entries[2].headers["User-Agent"])
        // The pipe suffix must not survive into the playable URL.
        assertEquals("http://host/3.ts", entries[2].url)
    }

    @Test
    fun `EXTGRP sets the group only when EXTINF did not`() {
        val entries = M3uParser.parseToPlaylist(
            """
            #EXTM3U
            #EXTINF:-1,No group here
            #EXTGRP:Sport
            http://host/1.ts
            #EXTINF:-1 group-title="News",Already grouped
            #EXTGRP:Sport
            http://host/2.ts
            """.trimIndent()
        ).entries

        assertEquals("Sport", entries[0].groupTitle)
        assertEquals("News", entries[1].groupTitle)
    }

    @Test
    fun `classifies movie and series paths used by xtream exports`() {
        val entries = M3uParser.parseToPlaylist(
            """
            #EXTM3U
            #EXTINF:-1,A movie
            http://host:8080/movie/u/p/55.mkv
            #EXTINF:-1,An episode
            http://host:8080/series/u/p/77.mp4
            #EXTINF:-1,A channel
            http://host:8080/live/u/p/99.ts
            """.trimIndent()
        ).entries

        assertEquals(ContentKind.VOD, entries[0].kind)
        assertEquals(ContentKind.SERIES, entries[1].kind)
        assertEquals(ContentKind.LIVE, entries[2].kind)
    }

    @Test
    fun `tolerates BOM, blank lines, stray comments and a missing header`() {
        val playlist = M3uParser.parseToPlaylist(
            "﻿#EXTM3U\n\n# just a comment\n" +
                "#EXTINF:-1,Channel One\n\nhttp://host/1.ts\n" +
                "http://host/orphan.ts\n"
        )

        assertNull(playlist.header.epgUrl)
        assertEquals(1, playlist.entries.size)
        assertEquals("Channel One", playlist.entries.single().name)
    }

    @Test
    fun `falls back to tvg-name when the display name is empty`() {
        val entry = M3uParser.parseToPlaylist(
            "#EXTM3U\n#EXTINF:-1 tvg-name=\"Fallback Name\",\nhttp://host/1.ts"
        ).entries.single()

        assertEquals("Fallback Name", entry.name)
    }

    @Test
    fun `streams entries without building an intermediate list`() {
        val playlist = buildString {
            appendLine("#EXTM3U")
            repeat(5_000) { index ->
                appendLine("#EXTINF:-1 tvg-id=\"ch$index\" group-title=\"Group ${index % 10}\",Channel $index")
                appendLine("http://host/$index.ts")
            }
        }

        var count = 0
        M3uParser.parse(playlist.byteInputStream(), onEntry = { count++ })

        assertEquals(5_000, count)
        assertTrue(count > 0)
    }
}
