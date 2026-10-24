// AI-generated with Claude Code, 2026-10-23, reviewed by Joonhyung Han
package com.lastpenguin.pix.session

import com.lastpenguin.pix.session.signaling.SignalCodec
import com.lastpenguin.pix.session.signaling.SignalMessage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Partitions for the signaling socket (Design 2.5.2), against a MockWebServer that upgrades to a WebSocket: the
 * socket opened; a known message decoded and an unknown one ignored; a message sent reaching the server; the server
 * closing (Closed without an error) and the connection failing (Closed with one); a second connect, after which the
 * first socket is silent; close, which emits nothing, and send without a socket, which only logs.
 */
class OkHttpSignalingClientTest {
    private val server = MockWebServer().apply { start() }
    private val events = LinkedBlockingQueue<SignalEvent>()
    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val client = OkHttpSignalingClient(OkHttpClient()).also { client ->
        scope.launch { client.events.collect { events.add(it) } }
    }

    /** What the server saw on its side of the socket: the texts received, and the socket to answer with. */
    private class ServerSide {
        val received = LinkedBlockingQueue<String>()
        val sockets = LinkedBlockingQueue<WebSocket>()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                sockets.add(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                received.add(text)
            }

            // Finish the close handshake the client starts, or the server never lets go of the socket.
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }
        }
    }

    private fun serve(side: ServerSide = ServerSide()): ServerSide {
        server.enqueue(MockResponse().withWebSocketUpgrade(side.listener))
        return side
    }

    private fun url() = "ws://${server.hostName}:${server.port}/ws"

    private fun next(): SignalEvent? = events.poll(5, TimeUnit.SECONDS)

    @After
    fun tearDown() {
        client.close()
        scope.cancel()
        server.shutdown()
    }

    @Test
    fun connect_opensTheSocket() {
        serve()

        client.connect(url())

        assertEquals(SignalEvent.Opened, next())
    }

    @Test
    fun aKnownMessageIsReceived_anUnknownOneIgnored() {
        val side = serve()
        client.connect(url())
        assertEquals(SignalEvent.Opened, next())
        val socket = side.sockets.poll(5, TimeUnit.SECONDS)!!

        socket.send("""{"type":"nope"}""")
        socket.send(SignalCodec.encode(SignalMessage.RoomJoin("482915")))

        assertEquals(SignalEvent.Received(SignalMessage.RoomJoin("482915")), next())
    }

    @Test
    fun send_reachesTheServerAsJson() {
        val side = serve()
        client.connect(url())
        assertEquals(SignalEvent.Opened, next())

        client.send(SignalMessage.RoomCreate)

        assertEquals("""{"type":"room.create"}""", side.received.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun theServerClosing_isClosedWithoutAnError() {
        val side = serve()
        client.connect(url())
        assertEquals(SignalEvent.Opened, next())

        side.sockets.poll(5, TimeUnit.SECONDS)!!.close(1000, "room gone")

        assertEquals(SignalEvent.Closed(null), next())
    }

    @Test
    fun aFailedConnection_isClosedWithTheError() {
        val url = url()
        server.shutdown()

        client.connect(url)

        val closed = next() as SignalEvent.Closed
        assertNotNull(closed.error)
    }

    @Test
    fun aSecondConnect_silencesTheFirstSocket() {
        serve()
        serve()
        client.connect(url())
        assertEquals(SignalEvent.Opened, next())

        client.connect(url())

        assertEquals(SignalEvent.Opened, next())
        // The first socket closes now, and the client stays quiet about it.
        assertNull(events.poll(500, TimeUnit.MILLISECONDS))
    }

    @Test
    fun close_emitsNothing_andSendWithoutASocketOnlyLogs() {
        serve()
        client.connect(url())
        assertEquals(SignalEvent.Opened, next())

        client.close()
        client.send(SignalMessage.Leave)

        assertNull(events.poll(500, TimeUnit.MILLISECONDS))
        assertTrue(events.isEmpty())
    }
}
