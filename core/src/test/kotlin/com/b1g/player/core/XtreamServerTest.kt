package com.b1g.player.core

import com.b1g.player.core.model.XtreamServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class XtreamServerTest {

    @Test
    fun `accepts the shapes users actually paste`() {
        assertEquals("http://host.tv:8080", XtreamServer.parse("http://host.tv:8080")?.baseUrl)
        assertEquals("http://host.tv:8080", XtreamServer.parse("  host.tv:8080/ ")?.baseUrl)
        assertEquals("https://host.tv", XtreamServer.parse("https://host.tv/")?.baseUrl)
        assertEquals("http://host.tv", XtreamServer.parse("host.tv")?.baseUrl)
        assertEquals("http://host.tv:2095", XtreamServer.parse("HOST.TV:2095")?.baseUrl)
    }

    @Test
    fun `strips a pasted player_api url down to the host`() {
        val server = XtreamServer.parse("http://host.tv:8080/player_api.php?username=u&password=p")
        assertEquals("http://host.tv:8080", server?.baseUrl)
    }

    @Test
    fun `drops embedded credentials`() {
        assertEquals("http://host.tv:8080", XtreamServer.parse("http://user:pass@host.tv:8080")?.baseUrl)
    }

    @Test
    fun `omits the port when it is the default for the scheme`() {
        assertEquals("http://host.tv", XtreamServer.parse("http://host.tv:80")?.baseUrl)
        assertEquals("https://host.tv", XtreamServer.parse("https://host.tv:443")?.baseUrl)
    }

    @Test
    fun `infers https from port 443 when no scheme is given`() {
        assertEquals("https://host.tv", XtreamServer.parse("host.tv:443")?.baseUrl)
    }

    @Test
    fun `keeps ipv6 literals intact`() {
        assertEquals("http://[2001:db8::1]:8080", XtreamServer.parse("http://[2001:db8::1]:8080")?.baseUrl)
    }

    @Test
    fun `rejects input that is not a host`() {
        assertNull(XtreamServer.parse(""))
        assertNull(XtreamServer.parse("   "))
        assertNull(XtreamServer.parse("host.tv:notaport"))
        assertNull(XtreamServer.parse("host.tv:99999"))
        assertNull(XtreamServer.parse("my server:8080"))
    }
}
