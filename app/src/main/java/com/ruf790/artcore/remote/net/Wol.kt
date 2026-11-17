package com.ruf790.artcore.remote.net

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Wake-on-LAN: the "magic packet" (6 × 0xFF, then the MAC address 16 times) sent as a UDP broadcast. */
object Wol {
    /** "1A:2B:3C:4D:5E:6F" (also with - or spaces, any case) → 6 bytes; null if it is not a MAC address. */
    fun parse(text: String): ByteArray? {
        val hex = text.filter { it != ':' && it != '-' && it != '.' && !it.isWhitespace() }
        if (hex.length != 12 || !hex.all { it in "0123456789abcdefABCDEF" }) return null
        return ByteArray(6) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    fun packet(mac: ByteArray): ByteArray {
        val out = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) out[i] = 0xFF.toByte()
        for (i in 0 until 16) System.arraycopy(mac, 0, out, 6 + i * 6, 6)
        return out
    }

    /** The broadcast address of the PC's /24 network (192.168.1.20 → 192.168.1.255); null for a host name. */
    fun subnetBroadcast(host: String?): String? {
        val parts = host?.split(".") ?: return null
        if (parts.size != 4 || parts.any { it.toIntOrNull() == null }) return null
        return "${parts[0]}.${parts[1]}.${parts[2]}.255"
    }

    /** Blocking: call it off the main thread. Never throws. */
    fun send(mac: ByteArray, pcHost: String?) {
        val data = packet(mac)
        val targets = listOfNotNull("255.255.255.255", subnetBroadcast(pcHost)).distinct()
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                for (target in targets) for (port in intArrayOf(9, 7)) {
                    try {
                        socket.send(DatagramPacket(data, data.size, InetAddress.getByName(target), port))
                    } catch (e: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
        }
    }
}
