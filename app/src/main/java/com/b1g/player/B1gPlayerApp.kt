package com.b1g.player

import android.app.Application
import android.content.Context
import com.b1g.player.core.http.HttpClient
import com.b1g.player.core.http.OkHttpEngine
import com.b1g.player.core.source.ContentSource
import com.b1g.player.core.source.ContentSourceFactory
import com.b1g.player.data.SourceStore

/**
 * Manual dependency container.
 *
 * Small enough that a DI framework would add more ceremony than it removes; if the
 * app grows past a handful of screens this is the seam to replace with Hilt.
 */
class AppContainer(context: Context) {
    val httpClient: HttpClient = OkHttpEngine()
    val sourceFactory = ContentSourceFactory(httpClient)
    val sourceStore = SourceStore(context.applicationContext)

    /** The source the user is currently browsing, kept alive across screens. */
    @Volatile
    var activeSource: ContentSource? = null
}

class B1gPlayerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as B1gPlayerApp).container
