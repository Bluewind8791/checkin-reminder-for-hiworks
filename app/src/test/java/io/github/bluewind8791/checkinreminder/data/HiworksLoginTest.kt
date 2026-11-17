package io.github.bluewind8791.checkinreminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HiworksLoginTest {
    @Test
    fun `the dashboard means logged in`() {
        assertTrue(HiworksLogin.isLoggedIn("https://dashboard.office.hiworks.com/"))
        assertTrue(HiworksLogin.isLoggedIn("https://dashboard.office.hiworks.com/home?x=1"))
    }

    @Test
    fun `other pages do not mean logged in`() {
        assertFalse(HiworksLogin.isLoggedIn("https://office.hiworks.com/"))
        assertFalse(HiworksLogin.isLoggedIn("https://login.office.hiworks.com/dashboard.office.hiworks.com"))
        assertFalse(HiworksLogin.isLoggedIn("https://dashboard.office.hiworks.com.example.com/"))
        assertFalse(HiworksLogin.isLoggedIn("http://dashboard.office.hiworks.com/"))
        assertFalse(HiworksLogin.isLoggedIn("not a url"))
        assertFalse(HiworksLogin.isLoggedIn(null))
    }

    @Test
    fun `finds the session id among other cookies`() {
        assertEquals("abc123", HiworksLogin.sessionId("lbg_a=1; PHPSESSID=abc123; other=2"))
        assertEquals("abc123", HiworksLogin.sessionId("PHPSESSID=abc123"))
    }

    @Test
    fun `ignores cookies whose name only contains PHPSESSID`() {
        assertEquals("real", HiworksLogin.sessionId("XPHPSESSID=fake; PHPSESSID=real"))
        assertNull(HiworksLogin.sessionId("PHPSESSID_OLD=fake"))
    }

    @Test
    fun `no session id when the cookie is missing or empty`() {
        assertNull(HiworksLogin.sessionId(null))
        assertNull(HiworksLogin.sessionId(""))
        assertNull(HiworksLogin.sessionId("a=1; b=2"))
        assertNull(HiworksLogin.sessionId("PHPSESSID="))
    }
}
