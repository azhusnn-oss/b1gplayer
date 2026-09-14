package com.b1g.player.core.m3u

import java.io.BufferedReader
import java.io.InputStream
import java.nio.charset.Charset

/**
 * Streaming parser for M3U / M3U8 IPTV playlists.
 *
 * Real playlists routinely run to hundreds of thousands of lines, so entries are
 * emitted through a callback as they are completed rather than accumulated — the
 * caller decides whether to keep them in memory or write them straight to storage.
 *
 * Tolerates the dialects that show up in the wild: `#EXTGRP` group lines,
 * `#EXTVLCOPT` and `#KODIPROP` header directives, and headers appended to the URL
 * after a `|`.
 */
object M3uParser {

    private val attributeRegex = Regex("""([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"""")

    fun parse(
        input: InputStream,
        charset: Charset = Charsets.UTF_8,
        onHeader: (M3uHeader) -> Unit = {},
        onEntry: (M3uEntry) -> Unit,
    ) = parse(input.bufferedReader(charset), onHeader, onEntry)

    fun parse(
        reader: BufferedReader,
        onHeader: (M3uHeader) -> Unit = {},
        onEntry: (M3uEntry) -> Unit,
    ) {
        var pending: PendingEntry? = null
        var sawHeader = false
        var first = true

        reader.forEachLine { rawLine ->
            var line = rawLine
            if (first) {
                line = line.removePrefix("﻿") // strip UTF-8 BOM
                first = false
            }
            line = line.trim()
            if (line.isEmpty()) return@forEachLine

            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    if (!sawHeader) {
                        sawHeader = true
                        onHeader(parseHeader(line))
                    }
                }

                line.startsWith("#EXTINF:", ignoreCase = true) ->
                    pending = parseExtInf(line.substring("#EXTINF:".length))

                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    val group = line.substring("#EXTGRP:".length).trim()
                    // #EXTGRP only sets the group when #EXTINF did not already carry one.
                    if (group.isNotEmpty() && pending?.attributes?.get("group-title").isNullOrBlank()) {
                        pending?.attributes?.put("group-title", group)
                    }
                }

                line.startsWith("#EXTVLCOPT:", ignoreCase = true) ->
                    applyVlcOption(line.substring("#EXTVLCOPT:".length).trim(), pending)

                line.startsWith("#KODIPROP:", ignoreCase = true) ->
                    applyKodiProp(line.substring("#KODIPROP:".length).trim(), pending)

                line.startsWith("#") -> Unit // #EXTVLCOPT variants, comments, HLS tags

                else -> {
                    val entry = pending
                    if (entry != null) {
                        onEntry(entry.toEntry(line))
                        pending = null
                    }
                    // A URL with no preceding #EXTINF is not a playlist row we can name,
                    // so it is skipped rather than guessed at.
                }
            }
        }
    }

    /** Convenience wrapper for small playlists and for tests. */
    fun parseToPlaylist(input: InputStream, charset: Charset = Charsets.UTF_8): M3uPlaylist {
        var header = M3uHeader()
        val entries = ArrayList<M3uEntry>()
        parse(input, charset, onHeader = { header = it }, onEntry = { entries += it })
        return M3uPlaylist(header, entries)
    }

    fun parseToPlaylist(text: String): M3uPlaylist =
        parseToPlaylist(text.byteInputStream(Charsets.UTF_8))

    private fun parseHeader(line: String): M3uHeader {
        val attributes = attributeRegex.findAll(line)
            .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        val epg = attributes["url-tvg"] ?: attributes["x-tvg-url"] ?: attributes["tvg-url"]
        return M3uHeader(
            // A playlist may advertise several comma-separated guides; take the first.
            epgUrl = epg?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
            attributes = attributes,
        )
    }

    private fun parseExtInf(text: String): PendingEntry {
        val split = indexOfUnquotedComma(text)
        val meta = if (split >= 0) text.substring(0, split) else text
        val name = if (split >= 0) text.substring(split + 1).trim() else ""

        val duration = meta.trimStart().takeWhile { !it.isWhitespace() }.toDoubleOrNull() ?: -1.0
        val attributes = LinkedHashMap<String, String>()
        attributeRegex.findAll(meta).forEach { attributes[it.groupValues[1].lowercase()] = it.groupValues[2] }

        val displayName = name.ifBlank { attributes["tvg-name"].orEmpty() }.ifBlank { "Unnamed" }
        return PendingEntry(displayName, duration, attributes)
    }

    /**
     * The attribute block ends at the first comma that is not inside a quoted value,
     * which is what separates `group-title="News, Sport"` from a name like
     * `Movie, The`.
     */
    private fun indexOfUnquotedComma(text: String): Int {
        var inQuotes = false
        text.forEachIndexed { index, c ->
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> return index
            }
        }
        return -1
    }

    private fun applyVlcOption(option: String, pending: PendingEntry?) {
        if (pending == null) return
        val key = option.substringBefore('=').trim().lowercase()
        val value = option.substringAfter('=', "").trim()
        if (value.isEmpty()) return
        when (key) {
            "http-user-agent" -> pending.headers["User-Agent"] = value
            "http-referrer", "http-referer" -> pending.headers["Referer"] = value
            "http-origin" -> pending.headers["Origin"] = value
        }
    }

    private fun applyKodiProp(prop: String, pending: PendingEntry?) {
        if (pending == null) return
        val key = prop.substringBefore('=').trim().lowercase()
        val value = prop.substringAfter('=', "").trim()
        if (value.isEmpty()) return
        // Kodi carries playback headers in a single &-delimited property.
        if (key.endsWith("stream_headers") || key.endsWith("manifest_headers")) {
            pending.headers.putAll(parseHeaderPairs(value))
        }
    }

    /** Parses `User-Agent=Foo&Referer=Bar`, as used by Kodi props and `url|...`. */
    internal fun parseHeaderPairs(text: String): Map<String, String> =
        text.split('&')
            .mapNotNull { pair ->
                val name = pair.substringBefore('=').trim()
                val value = pair.substringAfter('=', "").trim()
                if (name.isEmpty() || value.isEmpty()) null else name to decode(value)
            }
            .toMap()

    private fun decode(value: String): String =
        try {
            java.net.URLDecoder.decode(value, "UTF-8")
        } catch (_: IllegalArgumentException) {
            value // malformed percent-escapes are far more common than encoded ones
        }

    private class PendingEntry(
        val name: String,
        val duration: Double,
        val attributes: LinkedHashMap<String, String>,
        val headers: LinkedHashMap<String, String> = LinkedHashMap(),
    ) {
        fun toEntry(urlLine: String): M3uEntry {
            // `http://host/x.ts|User-Agent=Foo&Referer=Bar` is a widespread convention.
            val pipe = urlLine.indexOf('|')
            val url = if (pipe >= 0) urlLine.substring(0, pipe).trim() else urlLine.trim()
            if (pipe >= 0) headers.putAll(parseHeaderPairs(urlLine.substring(pipe + 1)))

            return M3uEntry(
                name = name,
                url = url,
                durationSeconds = duration,
                attributes = attributes.toMap(),
                headers = headers.toMap(),
            )
        }
    }
}
