package com.ruf790.artcore.remote

import com.ruf790.artcore.remote.net.Sightings
import com.ruf790.artcore.remote.proto.Backoff
import com.ruf790.artcore.remote.proto.Beacon
import com.ruf790.artcore.remote.proto.Group
import com.ruf790.artcore.remote.proto.PlaylistInfo
import com.ruf790.artcore.remote.proto.Playlists
import com.ruf790.artcore.remote.proto.Track
import com.ruf790.artcore.remote.proto.currentPosition
import com.ruf790.artcore.remote.proto.filterGroups
import com.ruf790.artcore.remote.proto.fmtTime
import com.ruf790.artcore.remote.proto.isLanAddress
import com.ruf790.artcore.remote.proto.matches
import com.ruf790.artcore.remote.proto.radioStations
import com.ruf790.artcore.remote.proto.subtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {

    @Test
    fun time() {
        assertEquals("0:00", fmtTime(0.0))
        assertEquals("1:54", fmtTime(114.9))
        assertEquals("5:02", fmtTime(302.0))
        assertEquals("1:02:05", fmtTime(3725.0))
        assertEquals("0:00", fmtTime(-3.0))
        assertEquals("0:00", fmtTime(Double.NaN))
    }

    private val t1 = Track(1, "Звезда по имени Солнце", "Кино", "Звезда по имени Солнце", "1989")
    private val t2 = Track(2, "Ёлка", "Кто-то", "Новый год")
    private val t3 = Track(3, "Second Song", "ArtCore Band", "Test Album")

    @Test
    fun search() {
        assertTrue(matches(t1, ""))
        assertTrue(matches(t1, "кино"))
        assertTrue(matches(t1, "звезда КИНО"))
        assertFalse(matches(t1, "звезда цой"))
        assertTrue(matches(t2, "елка"))                     // ё = е
        assertTrue(matches(t3, "test"))                     // the album too
        val groups = listOf(Group("album", "A", tracks = listOf(t1, t2)), Group("singles", tracks = listOf(t3)))
        assertEquals(groups, filterGroups(groups, "  "))
        val f = filterGroups(groups, "song")
        assertEquals(1, f.size)
        assertEquals("singles", f[0].kind)
        assertTrue(filterGroups(groups, "nothing like this").isEmpty())
    }

    @Test
    fun radioOfAllPlaylists() {
        val fm = Track(10, "Example FM", stream = true)
        val jazz = Track(11, "Jazz Radio", stream = true)
        val p = Playlists(1, 0, listOf(
            PlaylistInfo(0, "Rock", groups = listOf(Group("album", tracks = listOf(t3)), Group("radio", tracks = listOf(fm)))),
            PlaylistInfo(1, "Radio", groups = listOf(Group("radio", tracks = listOf(jazz)))),
        ))
        assertEquals(listOf(0 to fm, 1 to jazz), radioStations(p))
    }

    @Test
    fun positionMovesOnBetweenUpdates() {
        assertEquals(10.0, currentPosition(10.0, 1000, 1500, playing = false, duration = 100.0), 0.001)
        assertEquals(10.5, currentPosition(10.0, 1000, 1500, playing = true, duration = 100.0), 0.001)
        assertEquals(100.0, currentPosition(99.8, 1000, 5000, playing = true, duration = 100.0), 0.001)
        assertEquals(14.0, currentPosition(10.0, 1000, 5000, playing = true, duration = 0.0), 0.001)   // radio
        assertEquals(10.0, currentPosition(10.0, 5000, 1000, playing = true, duration = 100.0), 0.001)  // clock oddity
    }

    @Test
    fun reconnectDelays() {
        assertEquals(listOf(0L, 1000L, 2000L, 4000L, 8000L, 10000L, 10000L), (0..6).map { Backoff.delayMs(it) })
        assertEquals(10000L, Backoff.delayMs(100))
        assertTrue(Backoff.shouldRetry(null))
        assertTrue(Backoff.shouldRetry("timeout"))
        for (e in listOf("bad_code", "too_many", "bad_token", "forgotten", "proto")) assertFalse(e, Backoff.shouldRetry(e))
    }

    @Test
    fun manualAddress() {
        assertTrue(isLanAddress("192.168.1.10"))
        assertTrue(isLanAddress(" 10.0.0.2 "))
        assertTrue(isLanAddress("172.20.1.1"))
        assertFalse(isLanAddress("172.32.1.1"))
        assertFalse(isLanAddress("8.8.8.8"))
        assertFalse(isLanAddress("192.168.1"))
        assertFalse(isLanAddress("192.168.1.300"))
        assertFalse(isLanAddress("pc.local"))
    }

    @Test
    fun subtitleLine() {
        assertEquals("Кино · Звезда по имени Солнце (1989)", subtitle(t1))
        assertEquals("Кто-то · Новый год", subtitle(t2))
        assertEquals("", subtitle(Track(5, "x")))
    }

    @Test
    fun pcsComeAndGo() {
        val s = Sightings(ttlMs = 7000)
        val a = Beacon("RUFFNECK-PC", "192.168.1.10", 47870, "1.8.0", "id-a")
        val b = Beacon("KITCHEN", "192.168.1.11", 47870, "1.8.0", "id-b")
        assertTrue(s.heard(a, 0))
        assertFalse(s.heard(a, 2000))                               // the same again: nothing new
        assertTrue(s.heard(b, 3000))
        assertEquals(listOf("KITCHEN", "RUFFNECK-PC"), s.list().map { it.name })
        assertTrue(s.heard(a.copy(host = "192.168.1.20"), 4000))   // a new address (DHCP)
        assertEquals("192.168.1.20", s.list().first { it.id == "id-a" }.host)
        assertFalse(s.prune(9000))
        assertTrue(s.prune(10500))                                  // b was silent for 7.5 s
        assertEquals(listOf("RUFFNECK-PC"), s.list().map { it.name })
    }
}
