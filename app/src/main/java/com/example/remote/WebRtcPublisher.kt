package com.example.remote

import android.content.Context
import android.util.Log
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

data class PublisherState(
    val signalingOpen: Boolean = false,
    val peerConnected: Boolean = false,
    val usingRelay: Boolean = false,
    val detail: String? = null,
    val error: String? = null
)

/**
 * Lato telecamera: pubblica il video CameraX come traccia WebRTC e apre un data channel per i
 * comandi del centro di controllo (torcia, registrazione, qualità).
 */
class WebRtcPublisher(
    private val context: Context,
    private val config: RemoteConfig,
    private val onState: (PublisherState) -> Unit,
    private val onControl: (command: String, value: String) -> Unit
) {
    private val tag = "WebRtcPublisher"
    private val eglBase = EglBase.create()

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var capturer: CameraFrameCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var signaling: SignalingClient? = null
    private var dataChannel: DataChannel? = null

    private var state = PublisherState()

    fun start() {
        try {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )
            val peerFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(
                    DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
                )
                .createPeerConnectionFactory()
            factory = peerFactory

            val rtcConfig = PeerConnection.RTCConfiguration(
                config.iceServers().map { server ->
                    PeerConnection.IceServer.builder(server.url)
                        .setUsername(server.username)
                        .setPassword(server.credential)
                        .createIceServer()
                }
            ).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy =
                    PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }

            val connection = peerFactory.createPeerConnection(rtcConfig, peerObserver)
            if (connection == null) {
                fail("Impossibile creare la connessione WebRTC")
                return
            }
            peerConnection = connection

            val source = peerFactory.createVideoSource(false)
            videoSource = source
            val track = peerFactory.createVideoTrack("csc-video", source)
            videoTrack = track
            connection.addTrack(track, listOf("csc-stream"))

            // I fotogrammi arrivano da CameraX: nessuna seconda apertura della fotocamera.
            val frameCapturer = CameraFrameCapturer()
            capturer = frameCapturer
            val helper = SurfaceTextureHelper.create("csc-webrtc", eglBase.eglBaseContext)
            surfaceTextureHelper = helper
            frameCapturer.initialize(helper, context, source.capturerObserver)
            frameCapturer.startCapture(1280, 720, 30)

            createControlChannel()
            connectSignaling()
        } catch (e: Exception) {
            Log.e(tag, "WebRTC start failed", e)
            fail("WebRTC non disponibile: ${e.message}")
        }
    }

    fun push(nv21: ByteArray, width: Int, height: Int, rotationDegrees: Int) {
        capturer?.push(nv21, width, height, rotationDegrees)
    }

    fun stop() {
        signaling?.close()
        signaling = null
        dataChannel?.unregisterObserver()
        dataChannel?.dispose()
        dataChannel = null
        try {
            capturer?.stopCapture()
        } catch (e: InterruptedException) {
            Log.w(tag, "capturer stop: ${e.message}")
        }
        capturer?.dispose()
        capturer = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        videoSource?.dispose()
        videoSource = null
        videoTrack?.dispose()
        videoTrack = null
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        factory?.dispose()
        factory = null
        eglBase.release()
        emit(signalingOpen = false, peerConnected = false, detail = "Accesso remoto fermato")
    }

    private fun createControlChannel() {
        val channel = peerConnection?.createDataChannel("control", DataChannel.Init()) ?: return
        dataChannel = channel
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit
            override fun onStateChange() = Unit

            override fun onMessage(buffer: DataChannel.Buffer) {
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                val text = String(bytes)
                val separator = text.indexOf(':')
                if (separator <= 0) return
                onControl(text.substring(0, separator), text.substring(separator + 1))
            }
        })
    }

    private fun connectSignaling() {
        val client = SignalingClient(
            url = config.signalingUrl,
            listener = object : SignalingClient.Listener {
                override fun onOpen() {
                    emit(
                        signalingOpen = true,
                        detail = "In attesa del centro di controllo"
                    )
                }

                override fun onMessage(message: SignalMessage) {
                    when (message.type) {
                        SignalingProtocol.PEER_JOINED -> {
                            createOffer()
                        }

                        SignalingProtocol.SDP -> {
                            val sdp = message.sdp ?: return
                            if (message.sdpType == "answer") {
                                peerConnection?.setRemoteDescription(
                                    SdpAdapter(),
                                    SessionDescription(SessionDescription.Type.ANSWER, sdp)
                                )
                            }
                        }

                        SignalingProtocol.ICE -> {
                            val candidate = message.candidate ?: return
                            peerConnection?.addIceCandidate(
                                IceCandidate(message.mid ?: "", message.mlineIndex, candidate)
                            )
                        }

                        SignalingProtocol.PEER_LEFT -> {
                            emit(peerConnected = false, detail = "Centro di controllo disconnesso")
                        }
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
        client.connect(config.roomCode, RemoteConfig.ROLE_CAMERA)
    }

    private fun createOffer() {
        val connection = peerConnection ?: return
        connection.createOffer(
            object : SdpAdapter() {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    connection.setLocalDescription(SdpAdapter(), sdp)
                    signaling?.send(SignalingProtocol.sdp("offer", sdp.description))
                    emit(detail = "Offerta inviata, negoziazione in corso")
                }

                override fun onCreateFailure(error: String?) {
                    fail("Offerta WebRTC rifiutata: $error")
                }
            },
            MediaConstraints()
        )
    }

    private fun detectRoute() {
        val connection = peerConnection ?: return
        connection.getStats { report ->
            try {
                val transport = report.statsMap.values.firstOrNull { it.type == "transport" }
                val pairId = transport?.members?.get("selectedCandidatePairId")?.toString()
                val pair = pairId?.let { report.statsMap[it] }
                val localId = pair?.members?.get("localCandidateId")?.toString()
                val localCandidate = localId?.let { report.statsMap[it] }
                val candidateType = localCandidate?.members?.get("candidateType")
                    ?.toString()
                    ?.trim('"')
                    ?.lowercase()
                val relay = candidateType == "relay"
                emit(
                    peerConnected = true,
                    usingRelay = relay,
                    detail = if (relay) {
                        "Collegato tramite relay TURN"
                    } else {
                        "Collegato in P2P diretto"
                    }
                )
            } catch (e: Exception) {
                Log.w(tag, "Stats non leggibili: ${e.message}")
                emit(peerConnected = true, detail = "Collegato")
            }
        }
    }

    private fun emit(
        signalingOpen: Boolean = state.signalingOpen,
        peerConnected: Boolean = state.peerConnected,
        usingRelay: Boolean = state.usingRelay,
        detail: String? = state.detail,
        error: String? = state.error
    ) {
        state = PublisherState(signalingOpen, peerConnected, usingRelay, detail, error)
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
        override fun onDataChannel(dataChannel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) = Unit

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            when (newState) {
                PeerConnection.PeerConnectionState.CONNECTED -> detectRoute()
                PeerConnection.PeerConnectionState.DISCONNECTED ->
                    emit(peerConnected = false, detail = "Connessione in pausa")
                PeerConnection.PeerConnectionState.FAILED ->
                    fail("Connessione WebRTC fallita: serve un TURN raggiungibile")
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
