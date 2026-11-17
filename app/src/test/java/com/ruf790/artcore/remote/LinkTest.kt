package com.ruf790.artcore.remote

import com.ruf790.artcore.remote.net.Link
import com.ruf790.artcore.remote.net.LinkState
import com.ruf790.artcore.remote.net.Target
import com.ruf790.artcore.remote.proto.Incoming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.Executors

/** The connection against a stand-in ArtCore Music: pairing, the token next time, coming back after a drop. */
class LinkTest {
    private lateinit var server: MockWebServer
    private val exec = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(exec.asCoroutineDispatcher())
    private val states = Collections.synchronizedList(mutableListOf<LinkState>())
    private val messages = Collections.synchronizedList(mutableListOf<Incoming>())
    private val hellos = Collections.synchronizedList(mutableListOf<JSONObject>())
    private val sockets = Collections.synchronizedList(mutableListOf<WebSocket>())

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        scope.cancel()
        exec.shutdownNow()
        synchronized(sockets) { sockets.forEach { runCatching { it.close(1001, null) } } }
        try {
            server.shutdown()
        } catch (e: java.io.IOException) {
            // a WebSocket the stand-in PC still holds; the test itself is over
        }
    }

    /** A PC that answers hello: the right code gets a token, a known token gets in, anything else is refused. */
    private fun pc(dropAfterWelcome: Boolean = false) = MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            sockets += webSocket
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val m = JSONObject(text)
            if (m.optString("type") != "hello") return
            hellos += m
            when {
                m.optString("code") == "4821" -> {
                    webSocket.send("""{"type":"welcome","proto":1,"pc":"RUFFNECK-PC","version":"1.8.0","id":"pc1","token":"secret"}""")
                    webSocket.send("""{"type":"state","status":"play","volume":40,"track":{"id":7,"title":"Song"}}""")
                    if (dropAfterWelcome) webSocket.close(1001, "the PC goes to sleep")
                }
                m.optString("token") == "secret" ->
                    webSocket.send("""{"type":"welcome","proto":1,"pc":"RUFFNECK-PC","version":"1.8.0","id":"pc1"}""")
                else -> {
                    webSocket.send("""{"type":"error","error":"bad_code"}""")
                    webSocket.close(1000, null)
                }
            }
        }
    })

    private fun link() = Link(scope, { messages += it }, { states += it })

    private fun waitFor(ms: Long = 8000, what: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            if (what()) return true
            Thread.sleep(20)
        }
        return what()
    }

    private fun target(code: String? = null, token: String? = null) =
        Target(server.hostName, server.port, "Test phone", "dev-1", code = code, token = token)

    @Test
    fun pairsThenComesBackWithTheToken() {
        server.enqueue(pc(dropAfterWelcome = true))
        server.enqueue(pc())
        val l = link()
        l.connect(target(code = "4821"))
        assertTrue(waitFor { states.any { it is LinkState.Online } })
        assertTrue(waitFor { messages.any { it is Incoming.StateMsg } })
        // the PC dropped the line: the link waits a second and comes back by itself, with the token now
        assertTrue("no reconnect: $states", waitFor { states.count { it is LinkState.Online } >= 2 })
        assertTrue(states.any { it is LinkState.Waiting && it.inMs == 1000L })
        assertEquals(2, hellos.size)
        assertEquals("4821", hellos[0].optString("code"))
        assertEquals("secret", hellos[1].optString("token"))
        assertFalse(hellos[1].has("code"))
        assertTrue(l.state is LinkState.Online)
        assertTrue(l.send("""{"type":"pause"}"""))
        l.disconnect()
        assertTrue(waitFor { l.state is LinkState.Idle })
    }

    @Test
    fun wrongCodeIsNotRetried() {
        server.enqueue(pc())
        server.enqueue(pc())
        val l = link()
        l.connect(target(code = "0000"))
        assertTrue(waitFor { states.any { it is LinkState.Refused } })
        Thread.sleep(2500)                                        // longer than the first retry would wait
        assertEquals(1, hellos.size)
        assertEquals(LinkState.Refused("bad_code"), l.state)
        assertFalse(l.send("""{"type":"pause"}"""))
    }

    @Test
    fun unreachablePcIsRetried() {
        val port = server.port
        server.shutdown()                                         // nobody listens there now
        val l = Link(scope, { messages += it }, { states += it })
        l.connect(Target("127.0.0.1", port, "Test phone", "dev-1", token = "secret"))
        assertTrue(waitFor(6000) { states.count { it is LinkState.Waiting } >= 2 })
        val waits = states.filterIsInstance<LinkState.Waiting>().map { it.inMs }
        assertEquals(listOf(1000L, 2000L), waits.take(2))
        l.disconnect()
        server = MockWebServer().also { it.start() }              // for tearDown
    }
}
