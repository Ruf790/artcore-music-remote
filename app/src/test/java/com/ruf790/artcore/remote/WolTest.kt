package com.ruf790.artcore.remote

import com.ruf790.artcore.remote.net.Wol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WolTest {
    @Test
    fun macFormats() {
        val want = byteArrayOf(0x1A, 0x2B, 0x3C, 0x4D, 0x5E, 0x6F)
        assertArrayEquals(want, Wol.parse("1A:2B:3C:4D:5E:6F"))
        assertArrayEquals(want, Wol.parse("1a-2b-3c-4d-5e-6f"))
        assertArrayEquals(want, Wol.parse(" 1A2B3C4D5E6F "))
        assertNull(Wol.parse(""))
        assertNull(Wol.parse("1A:2B:3C:4D:5E"))
        assertNull(Wol.parse("1A:2B:3C:4D:5E:6G"))
    }

    @Test
    fun magicPacket() {
        val mac = byteArrayOf(1, 2, 3, 4, 5, 6)
        val p = Wol.packet(mac)
        assertEquals(102, p.size)
        for (i in 0 until 6) assertEquals(0xFF.toByte(), p[i])
        for (k in 0 until 16) assertArrayEquals(mac, p.copyOfRange(6 + k * 6, 12 + k * 6))
    }

    @Test
    fun subnet() {
        assertEquals("192.168.1.255", Wol.subnetBroadcast("192.168.1.20"))
        assertNull(Wol.subnetBroadcast("ruffneck-pc"))
        assertNull(Wol.subnetBroadcast(null))
    }
}
