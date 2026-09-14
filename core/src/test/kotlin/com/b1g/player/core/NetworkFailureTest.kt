package com.b1g.player.core

import com.b1g.player.core.model.SourceConfig
import com.b1g.player.core.model.XtreamServer
import com.b1g.player.core.source.ConnectResult
import com.b1g.player.core.source.M3uContentSource
import com.b1g.player.core.source.XtreamContentSource
import com.b1g.player.core.xtream.XtreamClient
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.UnknownHostException
import java.net.UnknownServiceException

/**
 * Connection failures have to name their cause: a generic message here is what made
 * a blocked-cleartext failure indistinguishable from a wrong address.
 */
class NetworkFailureTest {

    private val m3u = SourceConfig.M3u(
        id = "1",
        displayName = "Playlist",
        playlistUrl = "http://host.tv/list.m3u",
    )

    private val xtream = SourceConfig.Xtream(
        id = "2",
        displayName = "Panel",
        server = XtreamServer.parse("http://host.tv:8080")!!,
        username = "user",
        password = "pass",
    )

    private fun failureMessage(result: ConnectResult): String {
        assertTrue(result is ConnectResult.Failure) { "Expected a failure but was $result" }
        return (result as ConnectResult.Failure).message
    }

    @Test
    fun `explains a blocked cleartext request rather than blaming the server`() = runTest {
        val http = FakeHttpClient(
            failWith = UnknownServiceException(
                "CLEARTEXT communication to host.tv not permitted by network security policy"
            )
        )

        val message = failureMessage(M3uContentSource(m3u, http).connect())

        assertTrue(message.contains("plain HTTP"), message)
    }

    @Test
    fun `explains an unresolvable host`() = runTest {
        val http = FakeHttpClient(failWith = UnknownHostException("host.tv"))

        val message = failureMessage(XtreamContentSource(xtream, XtreamClient(http)).connect())

        assertTrue(message.contains("host.tv:8080"), message)
        assertTrue(message.contains("could not be resolved"), message)
    }

    @Test
    fun `passes through the underlying message when there is no better explanation`() = runTest {
        val http = FakeHttpClient(failWith = java.io.IOException("connection reset by peer"))

        val message = failureMessage(M3uContentSource(m3u, http).connect())

        assertTrue(message.contains("connection reset by peer"), message)
    }
}
