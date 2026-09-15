package com.b1g.player.core

import com.b1g.player.core.m3u.SeriesNaming
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SeriesNamingTest {

    private fun parse(name: String) = SeriesNaming.parse(name)

    @Test
    fun `reads the common season and episode spellings`() {
        assertEquals(
            SeriesNaming.Parsed("Breaking Bad", 1, 1, null),
            parse("Breaking Bad S01 E01"),
        )
        assertEquals(
            SeriesNaming.Parsed("Breaking Bad", 1, 5, "Gray Matter"),
            parse("Breaking Bad S01E05 - Gray Matter"),
        )
        assertEquals(
            SeriesNaming.Parsed("Dark", 2, 8, "Endings and Beginnings"),
            parse("Dark s02e08 Endings and Beginnings"),
        )
        assertEquals(
            SeriesNaming.Parsed("The Wire", 1, 3, null),
            parse("The Wire - 1x03"),
        )
        assertEquals(
            SeriesNaming.Parsed("Chernobyl", 2, 10, null),
            parse("Chernobyl Season 2 Episode 10"),
        )
    }

    @Test
    fun `keeps episode numbers above ninety-nine`() {
        assertEquals(SeriesNaming.Parsed("One Piece", 21, 1071, null), parse("One Piece S21 E1071"))
    }

    @Test
    fun `returns nothing when there is no episode marker`() {
        assertNull(parse("A Film 2019"))
        assertNull(parse("Documentary - The Deep Ocean"))
    }

    @Test
    fun `returns nothing when no show name precedes the marker`() {
        // Also guards the bare digit-x-digit pattern against titles like "4x4".
        assertNull(parse("S01E01"))
        assertNull(parse("4x4"))
    }

    @Test
    fun `gives one id to spellings of the same show`() {
        val id = SeriesNaming.seriesId("Breaking Bad")

        assertEquals(id, SeriesNaming.seriesId("breaking.bad"))
        assertEquals(id, SeriesNaming.seriesId("  BREAKING   BAD  "))
        assertEquals(id, SeriesNaming.seriesId("Breaking-Bad"))
    }

    @Test
    fun `gives different shows different ids`() {
        val breakingBad = SeriesNaming.seriesId("Breaking Bad")
        val betterCallSaul = SeriesNaming.seriesId("Better Call Saul")

        assertEquals(false, breakingBad == betterCallSaul)
    }
}
