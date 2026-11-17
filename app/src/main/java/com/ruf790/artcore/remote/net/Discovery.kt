package com.ruf790.artcore.remote.net

import android.content.Context
import android.net.wifi.WifiManager
import com.ruf790.artcore.remote.proto.Beacon
import com.ruf790.artcore.remote.proto.DISCOVERY_PORT
import com.ruf790.artcore.remote.proto.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/** Remembers the PCs heard lately; one that was silent for [ttlMs] is gone. Pure logic, tested on its own. */
class Sightings(private val ttlMs: Long = 7000) {
    private val seen = LinkedHashMap<String, Pair<Beacon, Long>>()

    fun heard(b: Beacon, nowMs: Long): Boolean {
        val key = b.id.ifEmpty { "${b.host}:${b.port}" }
        val old = seen[key]?.first
        seen[key] = b to nowMs
        return old != b
    }

    fun prune(nowMs: Long): Boolean {
        val gone = seen.filterValues { nowMs - it.second > ttlMs }.keys
        gone.forEach { seen.remove(it) }
        return gone.isNotEmpty()
    }

    fun list(): List<Beacon> = seen.values.map { it.first }.sortedBy { it.name.lowercase() }
}

/**
 * ArtCore Music says "here I am" every 2 s by UDP broadcast to port 47871 (name, version, port).
 * Listening needs a Wi-Fi multicast lock on many phones (broadcasts are filtered while the screen is off).
 */
class Discovery(context: Context, private val scope: CoroutineScope) {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val _found = MutableStateFlow<List<Beacon>>(emptyList())
    val found: StateFlow<List<Beacon>> = _found
    private var job: Job? = null
    private var socket: DatagramSocket? = null
    private var lock: WifiManager.MulticastLock? = null
    var onBeacon: ((Beacon) -> Unit)? = null

    val running get() = job?.isActive == true

    fun start() {
        if (running) return
        job = scope.launch(Dispatchers.IO) {
            val sights = Sightings()
            try {
                lock = wifi.createMulticastLock("artcore-remote").apply { setReferenceCounted(false); acquire() }
            } catch (e: Exception) {
                lock = null
            }
            val s = try {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 1000
                    bind(InetSocketAddress(DISCOVERY_PORT))
                }
            } catch (e: Exception) {
                null
            }
            socket = s
            val buf = ByteArray(2048)
            while (isActive && s != null) {
                val pkt = DatagramPacket(buf, buf.size)
                try {
                    s.receive(pkt)
                    val now = System.currentTimeMillis()
                    val host = pkt.address?.hostAddress?.removePrefix("::ffff:") ?: continue
                    val b = Protocol.beacon(pkt.data, pkt.length, host) ?: continue
                    sights.heard(b, now)
                    onBeacon?.let { cb -> scope.launch(Dispatchers.Main) { cb(b) } }
                } catch (e: SocketTimeoutException) {
                    // nothing this second
                } catch (e: Exception) {
                    if (!isActive) break
                }
                sights.prune(System.currentTimeMillis())
                val list = sights.list()
                if (list != _found.value) _found.value = list
            }
            s?.close()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        socket?.close()
        socket = null
        try {
            lock?.release()
        } catch (e: Exception) {
        }
        lock = null
    }
}
