package com.b1g.player.core.m3u

import com.b1g.player.core.model.ContentKind

/** One `#EXTINF` record plus the URL line that follows it. */
data class M3uEntry(
    val name: String,
    val url: String,
    val durationSeconds: Double = -1.0,
    val attributes: Map<String, String> = emptyMap(),
    /** Per-stream headers gathered from `#EXTVLCOPT`, `#KODIPROP` and `url|Header=v`. */
    val headers: Map<String, String> = emptyMap(),
) {
    val tvgId: String? get() = attributes["tvg-id"]?.takeIf { it.isNotBlank() }
    val tvgName: String? get() = attributes["tvg-name"]?.takeIf { it.isNotBlank() }
    val logoUrl: String? get() = attributes["tvg-logo"]?.takeIf { it.isNotBlank() }
    val groupTitle: String? get() = attributes["group-title"]?.takeIf { it.isNotBlank() }

    /**
     * Playlists exported by Xtream panels keep movies and episodes under `/movie/`
     * and `/series/` path segments, which is the only reliable signal a flat
     * playlist gives us about what a row actually is.
     */
    val kind: ContentKind
        get() {
            val path = url.substringBefore('?').lowercase()
            return when {
                path.contains("/movie/") || path.contains("/movies/") -> ContentKind.VOD
                path.contains("/series/") -> ContentKind.SERIES
                else -> ContentKind.LIVE
            }
        }
}

/** Playlist-level data taken from the `#EXTM3U` header line. */
data class M3uHeader(
    val epgUrl: String? = null,
    val attributes: Map<String, String> = emptyMap(),
)

data class M3uPlaylist(
    val header: M3uHeader,
    val entries: List<M3uEntry>,
)
