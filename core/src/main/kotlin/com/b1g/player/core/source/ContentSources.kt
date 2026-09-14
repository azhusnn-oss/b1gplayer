package com.b1g.player.core.source

import com.b1g.player.core.http.HttpClient
import com.b1g.player.core.http.OkHttpEngine
import com.b1g.player.core.model.SourceConfig
import com.b1g.player.core.xtream.XtreamClient

/** Builds the right [ContentSource] for a saved account. */
class ContentSourceFactory(
    private val http: HttpClient = OkHttpEngine(),
    private val xtreamClient: XtreamClient = XtreamClient(http),
) {
    fun create(config: SourceConfig): ContentSource = when (config) {
        is SourceConfig.M3u -> M3uContentSource(config, http)
        is SourceConfig.Xtream -> XtreamContentSource(config, xtreamClient)
    }
}
