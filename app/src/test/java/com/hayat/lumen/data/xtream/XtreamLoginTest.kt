package com.hayat.lumen.data.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XtreamLoginTest {

    @Test
    fun `get php link becomes a login`() {
        val login = XtreamLogin.fromPlaylistUrl("http://h.example.com:80/get.php?username=u1&password=p1&type=m3u&output=hls")!!
        assertEquals("u1", login.username)
        assertEquals("p1", login.password)
        assertEquals("http://h.example.com/player_api.php?username=u1&password=p1", login.apiUrl().toString())
        assertEquals(
            "http://h.example.com/player_api.php?username=u1&password=p1&action=get_live_streams",
            login.apiUrl("get_live_streams").toString(),
        )
        assertEquals("http://h.example.com/live/u1/p1/42.m3u8", login.liveStreamUrl("42", "m3u8"))
    }

    @Test
    fun `keeps port and sub path`() {
        val login = XtreamLogin.fromPlaylistUrl("http://h.example.com:8080/panel/get.php?username=u&password=p")!!
        assertEquals("http://h.example.com:8080/panel/player_api.php?username=u&password=p", login.apiUrl().toString())
    }

    @Test
    fun `other urls are not xtream`() {
        assertNull(XtreamLogin.fromPlaylistUrl("http://h.example.com/list.m3u"))
        assertNull(XtreamLogin.fromPlaylistUrl("http://h.example.com/get.php?username=u"))
        assertNull(XtreamLogin.fromPlaylistUrl("not a url"))
    }

    @Test
    fun `typed server is normalised`() {
        val login = XtreamLogin.of(" h.example.com:8080/ ", " u ", "p")!!
        assertEquals("http://h.example.com:8080/player_api.php?username=u&password=p", login.apiUrl().toString())
        assertNull(XtreamLogin.of("h.example.com", "", "p"))
        assertNull(XtreamLogin.of("", "u", "p"))
    }

    @Test
    fun `credentials with special characters are encoded`() {
        val login = XtreamLogin.of("http://h.example.com", "a b", "p&q")!!
        assertEquals("http://h.example.com/player_api.php?username=a%20b&password=p%26q", login.apiUrl().toString())
    }
}
