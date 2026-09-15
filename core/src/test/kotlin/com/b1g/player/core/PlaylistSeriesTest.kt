package com.b1g.player.core

import com.b1g.player.core.model.ContentKind
import com.b1g.player.core.model.SourceConfig
import com.b1g.player.core.source.M3uContentSource
import com.b1g.player.core.store.InMemoryContentStore
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A playlist lists episodes as flat rows, so the series view is reconstructed from
 * their titles. These pin down that reconstruction.
 */
class PlaylistSeriesTest {

    private val playlist = """
        #EXTM3U
        #EXTINF:-1 tvg-logo="http://host/bb.png" group-title="SERIES | DRAMA",Breaking Bad S01 E02
        http://host:8080/series/u/p/102.mkv
        #EXTINF:-1 tvg-logo="http://host/bb.png" group-title="SERIES | DRAMA",Breaking Bad S01 E01
        http://host:8080/series/u/p/101.mkv
        #EXTINF:-1 group-title="SERIES | DRAMA",Breaking Bad S02 E01
        http://host:8080/series/u/p/201.mkv
        #EXTINF:-1 group-title="SERIES | COMEDY",The Office US 1x03 - The Dundies
        http://host:8080/series/u/p/303.mkv
        #EXTINF:-1 group-title="SERIES | DRAMA",A One Off Special
        http://host:8080/series/u/p/999.mkv
        #EXTINF:-1 group-title="MOVIES",A Film
        http://host:8080/movie/u/p/55.mkv
    """.trimIndent()

    private fun config() = SourceConfig.M3u(
        id = "source-1",
        displayName = "Playlist",
        playlistUrl = "http://host/get.php",
        userAgent = "B1GPlayer/1.0",
    )

    private suspend fun connectedSource(): M3uContentSource =
        M3uContentSource(config(), FakeHttpClient(playlist), InMemoryContentStore())
            .also { it.connect() }

    @Test
    fun `groups episodes into shows`() = runTest {
        val series = connectedSource().series()

        assertEquals(listOf("Breaking Bad", "The Office US"), series.map { it.name })
        assertEquals(listOf(3, 1), series.map { it.episodeCount })
    }

    @Test
    fun `orders episodes by season then episode`() = runTest {
        val source = connectedSource()
        val breakingBad = source.series().first { it.name == "Breaking Bad" }

        val episodes = source.episodes(breakingBad.id)

        assertEquals(listOf(1 to 1, 1 to 2, 2 to 1), episodes.map { it.seasonNumber to it.episodeNumber })
        assertEquals(
            "http://host:8080/series/u/p/101.mkv",
            episodes.first().stream.url,
        )
        // Playback headers reach episodes the same way they reach channels.
        assertEquals("B1GPlayer/1.0", episodes.first().stream.headers["User-Agent"])
    }

    @Test
    fun `keeps the episode title when the entry carries one`() = runTest {
        val source = connectedSource()
        val office = source.series().first { it.name == "The Office US" }

        assertEquals("The Dundies", source.episodes(office.id).single().title)
    }

    @Test
    fun `files an entry with no episode marker under movies instead of losing it`() = runTest {
        val source = connectedSource()

        val movies = source.vod(limit = 100).map { it.name }
        assertTrue(movies.contains("A One Off Special"), movies.toString())
        assertTrue(movies.contains("A Film"), movies.toString())
    }

    @Test
    fun `derives series categories from group titles`() = runTest {
        val categories = connectedSource().categories(ContentKind.SERIES)

        assertEquals(listOf("SERIES | COMEDY", "SERIES | DRAMA"), categories.map { it.name })
    }

    @Test
    fun `filters series by category and search`() = runTest {
        val source = connectedSource()

        assertEquals(
            listOf("Breaking Bad"),
            source.series(categoryId = "series | drama").map { it.name },
        )
        assertEquals(
            listOf("The Office US"),
            source.series(query = "office").map { it.name },
        )
    }

    @Test
    fun `reports episodes in the connect summary`() = runTest {
        val source = M3uContentSource(config(), FakeHttpClient(playlist), InMemoryContentStore())

        val summary = (source.connect() as com.b1g.player.core.source.ConnectResult.Success).summary

        assertEquals("0 channels, 2 movies, 4 episodes", summary)
    }
}
