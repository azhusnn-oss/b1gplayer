package com.b1g.player.core.m3u

/**
 * Recovers series structure from playlist entry titles.
 *
 * A playlist lists episodes as flat rows — `Breaking Bad S01 E01`, `The Wire - 1x03`
 * — so the only way to offer a series view is to read the season and episode out of
 * the title and group by what precedes it.
 */
object SeriesNaming {

    data class Parsed(
        val showName: String,
        val season: Int,
        val episodeNumber: Int,
        val episodeTitle: String?,
    )

    /**
     * Ordered by how unambiguous each form is. `1x03` comes last because a bare
     * digit-x-digit also appears in ordinary titles, and the earliest match in the
     * string wins regardless of which pattern found it.
     */
    private val patterns = listOf(
        Regex("""(?i)\bS\s*(\d{1,3})\s*[\s._·-]*E\s*(\d{1,4})\b"""),
        Regex("""(?i)\bSeason\s*(\d{1,3})\s*[\s._·-]*Episode\s*(\d{1,4})\b"""),
        Regex("""(?i)(?<![\d.])(\d{1,2})\s*x\s*(\d{1,3})(?![\d.])"""),
    )

    private val separators = charArrayOf(' ', '-', '–', '—', '|', '.', '_', ':', '\t')

    /** Returns null when the title carries no recognisable season and episode. */
    fun parse(displayName: String): Parsed? {
        val match = patterns
            .mapNotNull { it.find(displayName) }
            .minByOrNull { it.range.first }
            ?: return null

        val season = match.groupValues[1].toIntOrNull() ?: return null
        val episode = match.groupValues[2].toIntOrNull() ?: return null

        val showName = displayName.substring(0, match.range.first).trim(*separators)
        if (showName.isBlank()) return null

        val episodeTitle = displayName.substring(match.range.last + 1)
            .trim(*separators)
            .takeIf { it.isNotBlank() }

        return Parsed(
            showName = showName,
            season = season,
            episodeNumber = episode,
            episodeTitle = episodeTitle,
        )
    }

    /**
     * A stable id for a show.
     *
     * Derived from the name because that is the only identity a playlist gives a
     * series; normalising punctuation and case keeps `Breaking Bad` and
     * `breaking.bad` together, which providers do mix within one playlist.
     */
    fun seriesId(showName: String): String {
        val normalised = showName
            .lowercase()
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()
        return "series:${normalised.hashCode().toUInt().toString(16)}"
    }
}
