package com.b1g.player.ui.player

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.b1g.player.core.http.OkHttpEngine
import com.b1g.player.core.model.StreamRequest
import com.b1g.player.ui.theme.B1gPlayerTheme

/**
 * Full-screen playback.
 *
 * Streams are opened through OkHttp so the per-stream headers gathered during
 * parsing (`User-Agent`, `Referer`) are actually sent — Media3's default HTTP stack
 * would apply only a global agent, and providers reject requests without the right
 * one.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null

    /** Compose state so a playback failure replaces the surface with a message. */
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val url = intent.getStringExtra(EXTRA_URL)
        if (url == null) {
            finish()
            return
        }
        val headers = intent.getBundleExtra(EXTRA_HEADERS)?.let { bundle ->
            bundle.keySet().mapNotNull { key -> bundle.getString(key)?.let { key to it } }.toMap()
        } ?: emptyMap()

        setContent {
            B1gPlayerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PlayerSurface(title = title, url = url, headers = headers)
                }
            }
        }
    }

    @Composable
    private fun PlayerSurface(title: String, url: String, headers: Map<String, String>) {
        errorMessage?.let { message ->
            Text(text = message, modifier = Modifier.fillMaxSize())
            return
        }

        AndroidView(
            factory = { context ->
                val exoPlayer = createPlayer(context, url, headers, title)
                player = exoPlayer
                PlayerView(context).apply {
                    keepScreenOn = true
                    setPlayer(exoPlayer)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    private fun createPlayer(
        context: Context,
        url: String,
        headers: Map<String, String>,
        title: String,
    ): ExoPlayer {
        val dataSourceFactory = OkHttpDataSource.Factory(OkHttpEngine.defaultClient())
            .setUserAgent(headers["User-Agent"] ?: OkHttpEngine.DEFAULT_USER_AGENT)
            .setDefaultRequestProperties(headers.filterKeys { !it.equals("User-Agent", true) })

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.Builder().setUri(url).setMediaId(title).build())
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        errorMessage = "Playback failed: ${error.errorCodeName}"
                    }
                })
                playWhenReady = true
                prepare()
            }
    }

    override fun onStop() {
        super.onStop()
        // From Android 7 the activity can be visible while stopped only in
        // multi-window, where releasing here is still the documented behaviour.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) release()
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) release()
    }

    private fun release() {
        player?.release()
        player = null
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_URL = "url"
        private const val EXTRA_HEADERS = "headers"

        fun intent(context: Context, title: String, stream: StreamRequest): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_URL, stream.url)
                .putExtra(
                    EXTRA_HEADERS,
                    Bundle().apply { stream.headers.forEach { (key, value) -> putString(key, value) } },
                )
    }
}
