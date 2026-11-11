package com.example.remote

import android.content.Context
import android.util.Log
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import java.nio.ByteBuffer

data class ViewerState(
    val signalingOpen: Boolean = false,
    val connected: Boolean = false,
    val detail: String? = null,
    val error: String? = null
)

/**
 * Lato centro di controllo: entra nella stanza, risponde all'offerta della telecamera e disegna
 * la traccia remota sul renderer. I comandi viaggiano sul data channel della telecamera.
 */
class WebRtcViewer(
    private val context: Context,
    private val config: RemoteConfig,
    private val renderer: SurfaceViewRenderer,
    private val onState: (ViewerState) -> Unit
) {
    private val tag = "WebRtcViewer"
    private val eglBase = EglBase.create()

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var signaling: SignalingClient? = null
    private var controlChannel: DataChannel? = null
    private var state = ViewerState()

    fun start() {
        try {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )
            factory = PeerConnectionFactory.builder()
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
                .createPeerConnectionFactory()

            val client = SignalingClient(
                url = config.signalingUrl,
                listener = object : SignalingClient.Listener {
                    override fun onOpen() {
                        emit(signalingOpen = true, detail = "In attesa della telecamera")
                    }

                    override fun onMessage(message: SignalMessage) {
                        when (message.type) {
                            SignalingProtocol.SDP -> {
                                if (message.sdpType == "offer" && message.sdp != null) {
                                    onRemoteOffer(message.sdp)
                                }
                            }

                            SignalingProtocol.ICE -> {
                                val candidate = message.candidate ?: return
                                peerConnection?.addIceCandidate(
                                    IceCandidate(message.mid ?: "", message.mlineIndex, candidate)
                                )
                            }

                            SignalingProtocol.PEER_LEFT ->
                                emit(connected = false, detail = "Telecamera disconnessa")

                            SignalingProtocol.PEER_JOINED ->
                                emit(detail = "Telecamera presente, attendo l'offerta")
                        }
                    }

                    override fun onClosed(reason: String) {
                        emit(signalingOpen = false, detail = reason)
                    }

                    override fun onFailure(error: String) {
                        fail(error)
                    }
                }
            )
            signaling = client
            client.connect(config.roomCode, RemoteConfig.ROLE_VIEWER)
        } catch (e: Exception) {
            Log.e(tag, "Viewer start failed", e)
            fail("WebRTC non disponibile: ${e.message}")
        }
    }

    fun sendCommand(command: String, value: String) {
        val channel = controlChannel ?: return
        val payload = "$command:$value".toByteArray()
        channel.send(DataChannel.Buffer(ByteBuffer.wrap(payload), false))
    }

    fun stop() {
        signaling?.close()
        signaling = null
        controlChannel = null
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        factory?.dispose()
        factory = null
        eglBase.release()
        emit(signalingOpen = false, connected = false, detail = "Connessione chiusa")
    }

    private fun onRemoteOffer(sdp: String) {
        val connection = ensurePeerConnection() ?: return
        connection.setRemoteDescription(
            object : SdpAdapter() {
                override fun onSetSuccess() {
                    connection.createAnswer(
                        object : SdpAdapter() {
                            override fun onCreateSuccess(answer: SessionDescription) {
                                connection.setLocalDescription(SdpAdapter(), answer)
                                signaling?.send(
                                    SignalingProtocol.sdp("answer", answer.description)
                                )
                                emit(detail = "Risposta inviata")
                            }

                            override fun onCreateFailure(error: String?) {
                                fail("Risposta WebRTC rifiutata: $error")
                            }
                        },
                        MediaConstraints()
                    )
                }

                override fun onSetFailure(error: String?) {
                    fail("Offerta non applicata: $error")
                }
            },
            SessionDescription(SessionDescription.Type.OFFER, sdp)
        )
    }

    private fun ensurePeerConnection(): PeerConnection? {
        peerConnection?.let { return it }
        val peerFactory = factory ?: return null
        val rtcConfig = PeerConnection.RTCConfiguration(
            config.iceServers().map { server ->
                PeerConnection.IceServer.builder(server.url)
                    .setUsername(server.username)
                    .setPassword(server.credential)
                    .createIceServer()
            }
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val connection = peerFactory.createPeerConnection(rtcConfig, peerObserver) ?: return null
        peerConnection = connection
        connection.addTransceiver(
            org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        )
        return connection
    }

    private fun emit(
        signalingOpen: Boolean = state.signalingOpen,
        connected: Boolean = state.connected,
        detail: String? = state.detail,
        error: String? = state.error
    ) {
        state = ViewerState(signalingOpen, connected, detail, error)
        onState(state)
    }

    private fun fail(message: String) {
        Log.e(tag, message)
        emit(error = message, detail = message)
    }

    private val peerObserver = object : PeerConnection.Observer {
        override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidate(candidate: IceCandidate) {
            signaling?.send(
                SignalingProtocol.ice(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
            )
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) = Unit

        override fun onDataChannel(dataChannel: DataChannel) {
            // Il canale è creato dalla telecamera: qui si conserva solo per inviare i comandi.
            controlChannel = dataChannel
        }

        override fun onTrack(transceiver: RtpTransceiver) {
            val track = transceiver.receiver.track()
            if (track is VideoTrack) {
                track.setEnabled(true)
                track.addSink(renderer)
                emit(detail = "Video remoto ricevuto")
            }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            when (newState) {
                PeerConnection.PeerConnectionState.CONNECTED ->
                    emit(connected = true, detail = "Video remoto attivo")
                PeerConnection.PeerConnectionState.DISCONNECTED ->
                    emit(connected = false, detail = "Connessione in pausa")
                PeerConnection.PeerConnectionState.FAILED ->
                    fail("Connessione WebRTC fallita: verifica il TURN")
                else -> Unit
            }
        }
    }

    private open class SdpAdapter : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }
}
