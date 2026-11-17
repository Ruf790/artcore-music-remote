package com.ruf790.artcore.remote.net

import com.ruf790.artcore.remote.proto.Backoff
import com.ruf790.artcore.remote.proto.FATAL_ERRORS
import com.ruf790.artcore.remote.proto.Incoming
import com.ruf790.artcore.remote.proto.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** Where to connect and how to introduce ourselves. */
data class Target(
    val host: String,
    val port: Int,
    val device: String,
    val deviceId: String,
    val code: String? = null,
    val token: String? = null,
)

sealed class LinkState {
    data object Idle : LinkState()
    data class Connecting(val host: String, val attempt: Int) : LinkState()
    data class Online(val pc: String) : LinkState()
    /** Lost: the next attempt comes in [inMs]. */
    data class Waiting(val attempt: Int, val inMs: Long) : LinkState()
    /** The PC said no (wrong code, forgotten phone …): no retries until the user acts. */
    data class Refused(val code: String) : LinkState()
}

/**
 * One WebSocket to ArtCore Music that comes back by itself: when the Wi-Fi drops or the PC restarts,
 * it reconnects (1, 2, 4, 8, then every 10 s). Callbacks run on [scope] (the main thread).
 */
class Link(
    private val scope: CoroutineScope,
    private val onMessage: (Incoming) -> Unit,
    private val onState: (LinkState) -> Unit,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build(),
) {
    private var target: Target? = null
    private var ws: WebSocket? = null
    private var gen = 0                  // callbacks of an older socket are ignored
    private var attempt = 0
    private var retry: Job? = null
    var state: LinkState = LinkState.Idle
        private set

    fun connect(t: Target) {
        target = t
        attempt = 0
        open()
    }

    /** After pairing: from now on the token, never the code again. */
    fun useToken(token: String) {
        target = target?.copy(code = null, token = token)
    }

    /** The PC moved to another address (DHCP): go there. */
    fun moveTo(host: String, port: Int) {
        val t = target ?: return
        if (t.host == host && t.port == port) return
        target = t.copy(host = host, port = port)
        if (state !is LinkState.Online && state !is LinkState.Refused) open()
    }

    /** Try now (pull-to-refresh, the app came to the front). */
    fun retryNow() {
        if (target != null && (state is LinkState.Waiting || state is LinkState.Idle)) open()
    }

    fun disconnect() {
        target = null
        retry?.cancel()
        gen++
        ws?.close(1000, "bye")
        ws = null
        set(LinkState.Idle)
    }

    fun send(text: String): Boolean = state is LinkState.Online && ws?.send(text) == true

    private fun set(s: LinkState) {
        state = s
        onState(s)
    }

    private fun open() {
        val t = target ?: return
        retry?.cancel()
        ws?.cancel()
        val my = ++gen
        set(LinkState.Connecting(t.host, attempt))
        val req = Request.Builder().url("ws://${t.host}:${t.port}/").build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(Protocol.hello(t.device, t.deviceId, t.code, t.token))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val m = Protocol.parse(text)
                scope.launch {
                    if (my != gen) return@launch
                    when (m) {
                        is Incoming.Welcome -> {
                            attempt = 0
                            m.token?.let { useToken(it) }
                            set(LinkState.Online(m.pc))
                        }
                        is Incoming.Error -> if (m.code in FATAL_ERRORS) {
                            gen++                                   // the close that follows is expected
                            webSocket.close(1000, null)
                            set(LinkState.Refused(m.code))
                        }
                        else -> {}
                    }
                    onMessage(m)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = lost(my)

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = lost(my)
        })
    }

    private fun lost(my: Int) {
        scope.launch {
            if (my != gen || target == null) return@launch
            attempt++
            val wait = Backoff.delayMs(attempt)
            set(LinkState.Waiting(attempt, wait))
            retry = scope.launch {
                delay(wait)
                if (my == gen) open()
            }
        }
    }
}
