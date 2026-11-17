package com.ruf790.artcore.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.runtime.mutableStateMapOf
import com.ruf790.artcore.remote.data.SavedPc
import com.ruf790.artcore.remote.data.Store
import com.ruf790.artcore.remote.net.Discovery
import com.ruf790.artcore.remote.net.Link
import com.ruf790.artcore.remote.net.LinkState
import com.ruf790.artcore.remote.net.Target
import com.ruf790.artcore.remote.net.Wol
import com.ruf790.artcore.remote.proto.Beacon
import com.ruf790.artcore.remote.proto.Incoming
import com.ruf790.artcore.remote.proto.PlayerState
import com.ruf790.artcore.remote.proto.Playlists
import com.ruf790.artcore.remote.proto.Protocol
import com.ruf790.artcore.remote.proto.Status
import com.ruf790.artcore.remote.proto.currentPosition
import com.ruf790.artcore.remote.service.RemoteService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Everything the screens and the notification show, and every button: one per app (see [App]). */
class Remote(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val store = Store(context)
    val discovery = Discovery(context, scope)

    private val _link = MutableStateFlow<LinkState>(LinkState.Idle)
    val link: StateFlow<LinkState> = _link
    private val _playlists = MutableStateFlow(Playlists())
    val playlists: StateFlow<Playlists> = _playlists
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state
    private val _pcName = MutableStateFlow(store.pc?.name ?: "")
    val pcName: StateFlow<String> = _pcName

    /** The position the PC sent last and when it came (the seek bar moves on by itself in between). */
    @Volatile private var posBase = 0.0
    @Volatile private var posAt = 0L

    /** Covers by "id@size"; ids known to have none. Compose state: rows redraw when a cover arrives. */
    val covers = mutableStateMapOf<String, Bitmap>()
    private val noCover = HashSet<String>()
    private val asked = HashSet<String>()
    var onCover: ((String, Int, Bitmap?) -> Unit)? = null

    private var pending: Beacon? = null              // pairing with this PC right now

    private val conn = Link(scope, ::onMessage, ::onLink)

    init {
        discovery.onBeacon = { b ->
            val saved = store.pc
            if (saved != null && b.id.isNotEmpty() && b.id == saved.id && (b.host != saved.host || b.port != saved.port)) {
                store.pc = saved.copy(host = b.host, port = b.port, name = b.name)
                conn.moveTo(b.host, b.port)
            }
        }
    }

    val paired: Boolean get() = store.pc != null

    private val deviceName: String
        get() = listOf(Build.MANUFACTURER, Build.MODEL).filter { !it.isNullOrBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }.ifEmpty { "Android" }

    // ── connecting
    fun connectSaved() {
        val pc = store.pc ?: return
        if (conn.state is LinkState.Online || conn.state is LinkState.Connecting) return
        _pcName.value = pc.name
        conn.connect(Target(pc.host, pc.port, deviceName, store.deviceId, token = pc.token))
    }

    /** The first time: the 4-digit code from ArtCore Music → Settings → Remote. */
    fun pair(b: Beacon, code: String) {
        pending = b
        _pcName.value = b.name
        conn.connect(Target(b.host, b.port, deviceName, store.deviceId, code = code.trim()))
    }

    fun retryNow() = conn.retryNow()

    /** 1.2.0: Wake-on-LAN — the magic packet for the MAC address from the settings; false if there is none. */
    fun wake(): Boolean {
        val mac = Wol.parse(store.wolMac) ?: return false
        val host = store.pc?.host
        scope.launch(Dispatchers.IO) { Wol.send(mac, host) }
        Toast.makeText(context, R.string.wol_sent, Toast.LENGTH_SHORT).show()
        return true
    }

    /** 1.0.2: Menu → Exit — close the connection and the notification; the PC stays paired (next start connects). */
    fun quit() {
        conn.disconnect()
        discovery.stop()
        asked.clear()
        RemoteService.stop(context)
    }

    /** Menu → Disconnect: forget the PC on this phone. */
    fun forget() {
        conn.disconnect()
        store.pc = null
        pending = null
        _playlists.value = Playlists()
        _state.value = PlayerState()
        covers.clear()
        noCover.clear()
        asked.clear()
        RemoteService.stop(context)
    }

    private fun onLink(s: LinkState) {
        _link.value = s
        if (s !is LinkState.Online) {
            asked.clear()                              // a cover asked for on the old socket comes never
            if (s is LinkState.Refused || s is LinkState.Idle) RemoteService.stop(context)
        }
    }

    private fun onMessage(m: Incoming) {
        when (m) {
            is Incoming.Welcome -> {
                _pcName.value = m.pc
                val b = pending
                val saved = store.pc
                if (m.token != null && b != null) {
                    store.pc = SavedPc(m.id, m.pc, b.host, b.port, m.token)
                    pending = null
                } else if (saved != null && (saved.name != m.pc || saved.id != m.id)) {
                    store.pc = saved.copy(name = m.pc, id = m.id)
                }
            }
            is Incoming.PlaylistsMsg -> _playlists.value = m.playlists
            is Incoming.StateMsg -> {
                posBase = m.state.position
                posAt = SystemClock.elapsedRealtime()
                _state.value = m.state
                RemoteService.sync(context, this)
            }
            is Incoming.Position -> {
                posBase = m.position
                posAt = SystemClock.elapsedRealtime()
                val st = _state.value
                if (st.status != m.status || st.duration != m.duration) {
                    _state.value = st.copy(status = m.status, duration = m.duration, position = m.position)
                }
            }
            is Incoming.Cover -> {
                val key = "${m.id}@${m.size}"
                asked.remove(key)
                val bmp = m.jpeg?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                if (bmp != null) covers[key] = bmp else noCover.add(m.id)
                onCover?.invoke(m.id, m.size, bmp)
            }
            else -> {}
        }
    }

    /** Seconds into the playing track, now. */
    fun position(): Double {
        val st = _state.value
        return currentPosition(posBase, posAt, SystemClock.elapsedRealtime(), st.status == Status.PLAY, st.duration)
    }

    // ── covers
    /** The cover if it is here; otherwise asks the PC once (the map fills in later). */
    fun cover(id: String, size: Int): Bitmap? {
        if (id.isEmpty() || id in noCover) return null
        val key = "$id@$size"
        covers[key]?.let { return it }
        if (size < 512) covers["$id@512"]?.let { return it }     // a bigger one will do for a thumbnail
        if (conn.state is LinkState.Online && asked.add(key)) conn.send(Protocol.getCover(id, size))
        return null
    }

    fun hasNoCover(id: String) = id.isEmpty() || id in noCover

    // ── the buttons
    private fun send(text: String) {
        if (!conn.send(text)) conn.retryNow()
    }

    fun play(playlist: Int, track: Long) = send(Protocol.play(playlist, track))

    fun playPause() {
        val st = _state.value
        when (st.status) {
            Status.PLAY -> {
                _state.value = st.copy(status = Status.PAUSE)          // the button answers at once
                posBase = position()
                send(Protocol.simple("pause"))
            }
            else -> send(Protocol.simple("resume"))
        }
    }

    fun stop() = send(Protocol.simple("stop"))
    fun next() = send(Protocol.simple("next"))
    fun prev() = send(Protocol.simple("prev"))

    fun seek(sec: Double) {
        posBase = sec
        posAt = SystemClock.elapsedRealtime()
        send(Protocol.seek(sec))
    }

    fun setVolume(v: Int) {
        _state.value = _state.value.copy(volume = v.coerceIn(0, 100))
        send(Protocol.volume(v))
    }

    fun volumeStep(delta: Int) = setVolume(_state.value.volume + delta)

    fun toggleMute() = send(Protocol.simple("mute"))

    fun cycleRepeat() = send(Protocol.repeat(Protocol.nextRepeat(_state.value.repeat)))

    fun toggleShuffle() = send(Protocol.shuffle(!_state.value.shuffle))
}
