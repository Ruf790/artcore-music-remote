package com.ruf790.artcore.remote

import com.ruf790.artcore.remote.proto.Incoming
import com.ruf790.artcore.remote.proto.Protocol
import com.ruf790.artcore.remote.proto.Status
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The JSON of ArtCore Music → models. The fixtures in src/test/resources were made by the Python server itself. */
class ProtocolTest {

    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun playlistsFromTheServer() {
        val m = Protocol.parse(res("playlists.json"))
        assertTrue(m is Incoming.PlaylistsMsg)
        val p = (m as Incoming.PlaylistsMsg).playlists
        assertEquals(0, p.playing)
        assertEquals(listOf("Rock", "Radio"), p.items.map { it.name })
        val rock = p.items[0]
        assertEquals(4, rock.count)
        assertEquals(listOf("album", "singles"), rock.groups.map { it.kind })
        val album = rock.groups[0]
        assertEquals("Album", album.title)
        assertEquals("Band", album.artist)
        assertEquals("2001", album.year)
        assertEquals(listOf("Song 1", "Song 2", "Song 3"), album.tracks.map { it.title })
        assertEquals(101.0, album.tracks[0].duration, 0.001)
        assertTrue(album.tracks[0].cover.isNotEmpty())
        assertEquals(album.tracks[0].cover, album.tracks[2].cover)          // one cover per album
        assertNull(album.tracks[0].hasCover)                                 // not known yet
        val radio = p.items[1].groups.single()
        assertEquals("radio", radio.kind)
        assertTrue(radio.tracks.single().stream)
        assertEquals("", radio.tracks.single().cover)
        assertEquals(4, rock.tracks.size)
    }

    @Test
    fun stateFromTheServer() {
        val s = (Protocol.parse(res("state.json")) as Incoming.StateMsg).state
        assertEquals(Status.PLAY, s.status)
        assertEquals("Song 2", s.track!!.title)
        assertEquals(2, s.index)
        assertEquals(4, s.count)
        assertEquals("Song 3", s.next!!.title)
        assertEquals("FLAC · 24 bit · 96 kHz", s.format)
        assertEquals(12.5, s.position, 0.001)
        assertEquals(102.0, s.duration, 0.001)
        assertEquals(70, s.volume)
        assertEquals("off", s.repeat)
        assertFalse(s.shuffle)

        val r = (Protocol.parse(res("state_radio.json")) as Incoming.StateMsg).state
        assertEquals(1, r.playlist)
        assertTrue(r.track!!.stream)
        assertEquals("Example FM", r.track!!.title)
        assertNull(r.next)
    }

    @Test
    fun smallMessages() {
        val w = Protocol.parse("""{"type":"welcome","proto":1,"pc":"RUFFNECK-PC","version":"1.8.0","id":"ab12","token":"t0k"}""")
        assertEquals(Incoming.Welcome("RUFFNECK-PC", "1.8.0", "ab12", "t0k"), w)
        val w2 = Protocol.parse("""{"type":"welcome","proto":1,"pc":"PC","version":"1.8.0","id":"ab12"}""") as Incoming.Welcome
        assertNull(w2.token)
        assertEquals(Incoming.Error("bad_code"), Protocol.parse("""{"type":"error","error":"bad_code"}"""))
        assertEquals(Incoming.Position(61.5, 302.0, Status.PAUSE),
            Protocol.parse("""{"type":"position","position":61.5,"duration":302.0,"status":"pause"}"""))
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val b64 = java.util.Base64.getEncoder().encodeToString(jpeg)
        val c = Protocol.parse("""{"type":"cover","id":"0c5a","size":128,"mime":"image/jpeg","data":"$b64"}""") as Incoming.Cover
        assertEquals("0c5a", c.id)
        assertEquals(128, c.size)
        assertTrue(c.jpeg!!.contentEquals(jpeg))
        assertNull((Protocol.parse("""{"type":"cover","id":"x","size":512,"data":""}""") as Incoming.Cover).jpeg)
        assertEquals(Incoming.Unknown, Protocol.parse("not json"))
        assertEquals(Incoming.Unknown, Protocol.parse("""{"type":"something_new"}"""))
    }

    @Test
    fun beacon() {
        val bytes = res("beacon.json").toByteArray()
        val b = Protocol.beacon(bytes, bytes.size, "192.168.1.10")!!
        assertEquals("192.168.1.10", b.host)
        assertEquals(47870, b.port)
        assertEquals("1.8.0", b.version)
        assertTrue(b.id.isNotEmpty())
        val other = """{"app":"something-else","proto":1}""".toByteArray()
        assertNull(Protocol.beacon(other, other.size, "192.168.1.11"))
        val newer = """{"app":"artcore-music","proto":2,"port":1}""".toByteArray()
        assertNull(Protocol.beacon(newer, newer.size, "192.168.1.11"))
        assertNull(Protocol.beacon("garbage".toByteArray(), 7, "x"))
    }

    @Test
    fun commandsToThePc() {
        val hello = JSONObject(Protocol.hello("Pixel 7", "dev-1", "4821", null))
        assertEquals("hello", hello.getString("type"))
        assertEquals(1, hello.getInt("proto"))
        assertEquals("4821", hello.getString("code"))
        assertFalse(hello.has("token"))
        val again = JSONObject(Protocol.hello("Pixel 7", "dev-1", null, "tok"))
        assertEquals("tok", again.getString("token"))
        assertFalse(again.has("code"))

        val play = JSONObject(Protocol.play(1, 42L))
        assertEquals("play", play.getString("type"))
        assertEquals(1, play.getInt("playlist"))
        assertEquals(42L, play.getLong("track"))
        assertEquals("pause", JSONObject(Protocol.simple("pause")).getString("type"))
        assertEquals(93.5, JSONObject(Protocol.seek(93.5)).getDouble("position"), 0.001)
        assertEquals(100, JSONObject(Protocol.volume(140)).getInt("value"))
        assertEquals(-5, JSONObject(Protocol.volumeDelta(-5)).getInt("delta"))
        assertEquals("one", JSONObject(Protocol.repeat("one")).getString("mode"))
        assertTrue(JSONObject(Protocol.shuffle(true)).getBoolean("on"))
        val cov = JSONObject(Protocol.getCover("0c5a", 128))
        assertEquals("get_cover", cov.getString("type"))
        assertEquals(128, cov.getInt("size"))
        assertEquals("all", Protocol.nextRepeat("off"))
        assertEquals("one", Protocol.nextRepeat("all"))
        assertEquals("off", Protocol.nextRepeat("one"))
        assertNotNull(Protocol.simple("stop"))
    }
}
