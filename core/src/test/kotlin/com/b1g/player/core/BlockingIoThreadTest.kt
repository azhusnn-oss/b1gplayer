package com.b1g.player.core

import com.b1g.player.core.http.HttpClient
import com.b1g.player.core.http.HttpResponse
import com.b1g.player.core.model.SourceConfig
import com.b1g.player.core.model.XtreamServer
import com.b1g.player.core.source.M3uContentSource
import com.b1g.player.core.source.XtreamContentSource
import com.b1g.player.core.xtream.XtreamClient
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.io.InputStream

/**
 * A response body is a live socket: reading it is network I/O wherever it happens.
 * Consuming it on the caller's thread crashed the Android app with
 * NetworkOnMainThreadException, which no ordinary JVM test would have caught — so
 * these assert the read lands on a different thread than the caller.
 */
class BlockingIoThreadTest {

    /** Records the thread that first touches the stream. */
    private class ThreadRecordingStream(content: String) : InputStream() {
        private val delegate = content.byteInputStream()

        @Volatile
        var readThread: String? = null
            private set

        override fun read(): Int = record { delegate.read() }

        override fun read(b: ByteArray, off: Int, len: Int): Int = record { delegate.read(b, off, len) }

        private inline fun record(block: () -> Int): Int {
            if (readThread == null) readThread = Thread.currentThread().name
            return block()
        }
    }

    private fun clientReturning(stream: InputStream) = object : HttpClient {
        override suspend fun get(url: String, headers: Map<String, String>) = HttpResponse(200, stream)
    }

    @Test
    fun `playlist body is read off the calling thread`() = runTest {
        val stream = ThreadRecordingStream("#EXTM3U\n#EXTINF:-1,One\nhttp://host/1.ts\n")
        val source = M3uContentSource(
            SourceConfig.M3u(id = "1", displayName = "P", playlistUrl = "http://host/list.m3u"),
            clientReturning(stream),
        )

        val caller = Thread.currentThread().name
        source.connect()

        assertNotNull(stream.readThread, "the playlist was never read")
        assertNotEquals(caller, stream.readThread)
    }

    @Test
    fun `xtream response body is read off the calling thread`() = runTest {
        val stream = ThreadRecordingStream("""{"user_info":{"auth":1,"status":"Active"}}""")
        val source = XtreamContentSource(
            SourceConfig.Xtream(
                id = "2",
                displayName = "X",
                server = XtreamServer.parse("http://host.tv:8080")!!,
                username = "u",
                password = "p",
            ),
            XtreamClient(clientReturning(stream)),
        )

        val caller = Thread.currentThread().name
        source.connect()

        assertNotNull(stream.readThread, "the response was never read")
        assertNotEquals(caller, stream.readThread)
    }
}
