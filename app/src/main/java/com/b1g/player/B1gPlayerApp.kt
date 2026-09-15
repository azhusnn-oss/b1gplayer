package com.b1g.player

import android.app.Application
import android.content.Context
import com.b1g.player.core.http.HttpClient
import com.b1g.player.core.http.OkHttpEngine
import com.b1g.player.core.source.ContentSource
import com.b1g.player.core.source.ContentSourceFactory
import com.b1g.player.data.CrashReporter
import com.b1g.player.data.RoomContentStore
import com.b1g.player.data.SourceStore
import com.b1g.player.data.db.B1gDatabase

/**
 * Manual dependency container.
 *
 * Small enough that a DI framework would add more ceremony than it removes; if the
 * app grows past a handful of screens this is the seam to replace with Hilt.
 */
class AppContainer(context: Context) {
    val httpClient: HttpClient = OkHttpEngine()

    // Parsed playlists live here rather than in memory, so a large catalogue neither
    // exhausts the heap nor has to be re-downloaded on every launch.
    val database = B1gDatabase.create(context.applicationContext)
    val contentStore = RoomContentStore(database)

    val sourceFactory = ContentSourceFactory(httpClient, contentStore)
    val sourceStore = SourceStore(context.applicationContext)
    val crashReporter = CrashReporter(context.applicationContext)

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
        container.crashReporter.install()
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as B1gPlayerApp).container
