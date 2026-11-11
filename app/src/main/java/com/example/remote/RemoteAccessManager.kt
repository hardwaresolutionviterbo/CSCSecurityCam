package com.example.remote

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RemoteRouteType {
    LOCAL_LAN,
    WEBRTC_P2P_DIRECT,
    TURN_RELAY_TUNNEL
}

enum class RemoteConnectionStatus {
    DISABLED,
    DISCOVERING_STUN,
    ONLINE_READY,
    P2P_CONNECTED,
    RELAY_CONNECTED,
    ERROR
}

data class RemoteTunnelStats(
    val status: RemoteConnectionStatus = RemoteConnectionStatus.DISABLED,
    val activeRoute: RemoteRouteType = RemoteRouteType.LOCAL_LAN,
    val publicIp: String? = null,
    val publicPort: Int = 0,
    val localPort: Int = 8765,
    val natType: String = "In attesa...",
    val pairingCode: String = "",
    val latencyMs: Long = 0L,
    val isEncrypted: Boolean = true,
    val encryptionProtocol: String = "DTLS 1.2 / SRTP (AES-128-GCM)",
    val stunServer: String = "stun.l.google.com:19302",
    val turnServer: String = "Non configurato",
    val lastError: String? = null
)

/**
 * Controlla la pubblicazione WebRTC della telecamera (vero relay, non simulato).
 *
 * Il publisher vive in [RemotePublishHub] per sopravvivere al ViewModel; qui si combinano lo stato
 * del signaling, la route ICE reale (P2P o relay TURN) e l'IP pubblico rilevato via STUN.
 */
class RemoteAccessManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val tag = "RemoteAccessManager"
    private val stunClient = StunClient()
    private val store = RemoteConfigStore(context)

    private var config = store.load()

    private val _tunnelStats = MutableStateFlow(
        RemoteTunnelStats(
            pairingCode = config.roomCode,
            turnServer = if (config.turnUrl.isBlank()) "Non configurato" else config.turnUrl
        )
    )
    val tunnelStats: StateFlow<RemoteTunnelStats> = _tunnelStats.asStateFlow()

    private var probeJob: Job? = null
    private var healthCheckJob: Job? = null

    init {
        scope.launch {
            RemotePublishHub.state.collect { hub ->
                if (!hub.active) return@collect
                _tunnelStats.update { current ->
                    val status = when {
                        hub.error != null -> RemoteConnectionStatus.ERROR
                        hub.peerConnected -> if (hub.usingRelay) {
                            RemoteConnectionStatus.RELAY_CONNECTED
                        } else {
                            RemoteConnectionStatus.P2P_CONNECTED
                        }
                        hub.signalingOpen -> RemoteConnectionStatus.ONLINE_READY
                        else -> RemoteConnectionStatus.DISCOVERING_STUN
                    }
                    current.copy(
                        status = status,
                        activeRoute = when {
                            hub.usingRelay -> RemoteRouteType.TURN_RELAY_TUNNEL
                            hub.peerConnected -> RemoteRouteType.WEBRTC_P2P_DIRECT
                            else -> current.activeRoute
                        },
                        lastError = hub.error
                    )
                }
            }
        }
    }

    fun startRemoteAccess() {
        if (probeJob?.isActive == true) return
        config = store.load()
        if (!config.hasSignaling) {
            _tunnelStats.update {
                it.copy(
                    status = RemoteConnectionStatus.ERROR,
                    lastError = "Imposta un server di signaling wss:// nelle impostazioni"
                )
            }
            return
        }

        _tunnelStats.update {
            it.copy(
                status = RemoteConnectionStatus.DISCOVERING_STUN,
                pairingCode = config.roomCode,
                turnServer = if (config.turnUrl.isBlank()) "Non configurato" else config.turnUrl,
                lastError = null
            )
        }

        probeJob = scope.launch(Dispatchers.IO) {
            try {
                val result = stunClient.probe(
                    StunClient.DEFAULT_STUN_SERVER,
                    StunClient.DEFAULT_STUN_PORT
                )
                if (result != null) {
                    _tunnelStats.update {
                        it.copy(
                            publicIp = result.publicIp,
                            publicPort = result.publicPort,
                            natType = result.natType,
                            latencyMs = result.latencyMs
                        )
                    }
                } else {
                    _tunnelStats.update {
                        it.copy(
                            publicIp = null,
                            natType = "STUN non raggiungibile (verifica la rete)"
                        )
                    }
                }

                // Il publisher parte anche senza STUN: TURN o connectivity-check possono bastare.
                RemotePublishHub.start(context, config)
                startPeriodicHealthCheck()
            } catch (e: Exception) {
                Log.e(tag, "Failed to start remote access", e)
                _tunnelStats.update {
                    it.copy(
                        status = RemoteConnectionStatus.ERROR,
                        lastError = e.message
                    )
                }
            }
        }
    }

    private fun startPeriodicHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(30000)
                val result = stunClient.probe(
                    StunClient.DEFAULT_STUN_SERVER,
                    StunClient.DEFAULT_STUN_PORT
                )
                if (result != null) {
                    _tunnelStats.update {
                        it.copy(
                            publicIp = result.publicIp,
                            publicPort = result.publicPort,
                            latencyMs = result.latencyMs
                        )
                    }
                }
            }
        }
    }

    fun stopRemoteAccess() {
        probeJob?.cancel()
        probeJob = null
        healthCheckJob?.cancel()
        healthCheckJob = null
        RemotePublishHub.stop()

        _tunnelStats.update {
            it.copy(
                status = RemoteConnectionStatus.DISABLED,
                activeRoute = RemoteRouteType.LOCAL_LAN,
                lastError = null
            )
        }
        Log.i(tag, "Remote Access stopped")
    }

    fun isCurrentlyCellular(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager
            val network = cm?.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
        } catch (e: Exception) {
            false
        }
    }

    fun selectOptimalRoute(targetIp: String, localIp: String): RemoteRouteType {
        if (isCurrentlyCellular()) {
            return if (_tunnelStats.value.activeRoute == RemoteRouteType.TURN_RELAY_TUNNEL) {
                RemoteRouteType.TURN_RELAY_TUNNEL
            } else {
                RemoteRouteType.WEBRTC_P2P_DIRECT
            }
        }

        val targetSubnet = targetIp.substringBeforeLast(".")
        val localSubnet = localIp.substringBeforeLast(".")
        return if (targetSubnet.isNotEmpty() && targetSubnet == localSubnet) {
            RemoteRouteType.LOCAL_LAN
        } else if (_tunnelStats.value.activeRoute == RemoteRouteType.TURN_RELAY_TUNNEL) {
            RemoteRouteType.TURN_RELAY_TUNNEL
        } else {
            RemoteRouteType.WEBRTC_P2P_DIRECT
        }
    }
}
