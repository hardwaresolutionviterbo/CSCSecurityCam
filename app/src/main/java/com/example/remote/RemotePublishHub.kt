package com.example.remote

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Punto unico della pubblicazione WebRTC lato telecamera.
 *
 * Vive a livello di processo perché il servizio in primo piano continua a produrre fotogrammi con
 * l'app in background: se il publisher fosse nel ViewModel, uscendo dall'app il centro di
 * controllo perderebbe il video.
 */
object RemotePublishHub {

    data class State(
        val active: Boolean = false,
        val signalingOpen: Boolean = false,
        val peerConnected: Boolean = false,
        val usingRelay: Boolean = false,
        val detail: String? = null,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Il ViewModel registra qui i comandi che arrivano dal data channel. */
    @Volatile
    var controlSink: ((command: String, value: String) -> Unit)? = null

    private var publisher: WebRtcPublisher? = null

    fun start(context: Context, config: RemoteConfig) {
        if (publisher != null) return
        val instance = WebRtcPublisher(
            context = context.applicationContext,
            config = config,
            onState = { publishState ->
                _state.value = State(
                    active = true,
                    signalingOpen = publishState.signalingOpen,
                    peerConnected = publishState.peerConnected,
                    usingRelay = publishState.usingRelay,
                    detail = publishState.detail,
                    error = publishState.error
                )
            },
            onControl = { command, value -> controlSink?.invoke(command, value) }
        )
        publisher = instance
        _state.value = State(active = true, detail = "Connessione al server di signaling...")
        instance.start()
    }

    fun stop() {
        publisher?.stop()
        publisher = null
        _state.value = State()
    }

    fun pushFrame(nv21: ByteArray, width: Int, height: Int, rotationDegrees: Int) {
        publisher?.push(nv21, width, height, rotationDegrees)
    }
}
