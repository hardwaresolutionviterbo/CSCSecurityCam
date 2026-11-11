package com.example.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Collegamento WebSocket al relay di signaling. È l'unico traffico che deve essere raggiungibile
 * da internet: video e audio viaggiano poi in WebRTC (DTLS/SRTP) tra i due telefoni.
 */
class SignalingClient(
    private val url: String,
    private val listener: Listener
) {
    interface Listener {
        fun onOpen()
        fun onMessage(message: SignalMessage)
        fun onClosed(reason: String)
        fun onFailure(error: String)
    }

    private val httpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var socket: WebSocket? = null
    private var closedByUs = false

    fun connect(room: String, role: String) {
        if (socket != null) return
        closedByUs = false
        val request = Request.Builder().url(url).build()
        socket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(SignalingProtocol.join(room, role))
                listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = SignalingProtocol.parse(text) ?: return
                if (message.type == SignalingProtocol.ERROR) {
                    listener.onFailure(message.message ?: "Errore del server di signaling")
                    return
                }
                listener.onMessage(message)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socket = null
                listener.onClosed(if (closedByUs) "Connessione chiusa" else "Relay disconnesso ($code)")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                socket = null
                listener.onFailure(t.message ?: "Signaling non raggiungibile")
            }
        })
    }

    fun send(payload: String) {
        socket?.send(payload)
    }

    fun close() {
        closedByUs = true
        socket?.send(SignalingProtocol.bye())
        socket?.close(1000, "bye")
        socket = null
    }
}
