package com.example.remote

import android.content.Context
import java.security.SecureRandom

/** Un server ICE (STUN o TURN) usato dal PeerConnection. */
data class RemoteIceServer(
    val url: String,
    val username: String = "",
    val credential: String = ""
)

/**
 * Configurazione del controllo remoto via WebRTC.
 *
 * Il traffico video non passa dal server di signaling: questo serve solo a scambiare SDP e
 * candidati ICE, quindi può essere un relay minuscolo (vedi `signaling/server.mjs`). Lo STUN
 * apre la strada ai NAT domestici, il TURN è indispensabile per NAT simmetrici e reti mobili.
 */
data class RemoteConfig(
    val signalingUrl: String = "",
    val roomCode: String = "",
    val turnUrl: String = "",
    val turnUsername: String = "",
    val turnPassword: String = ""
) {
    val hasSignaling: Boolean get() = RemoteConfigStore.isValidSignalingUrl(signalingUrl)

    fun iceServers(): List<RemoteIceServer> {
        val servers = mutableListOf(
            RemoteIceServer(RemoteConfigStore.DEFAULT_STUN_URL)
        )
        if (turnUrl.isNotBlank()) {
            servers += RemoteIceServer(
                url = RemoteConfigStore.normalizeTurnUrl(turnUrl),
                username = turnUsername,
                credential = turnPassword
            )
        }
        return servers
    }

    companion object {
        /** Ruolo dei due lati della stanza: la telecamera pubblica, il centro di controllo guarda. */
        const val ROLE_CAMERA = "camera"
        const val ROLE_VIEWER = "viewer"
    }
}

/**
 * Persistenza della configurazione remota (SharedPreferences, non Room): il codice stanza deve
 * restare stabile tra riavvii e le credenziali TURN non hanno senso nella tabella delle
 * impostazioni condivisa con l'interfaccia.
 */
class RemoteConfigStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): RemoteConfig {
        val stored = RemoteConfig(
            signalingUrl = prefs.getString(KEY_SIGNALING, "").orEmpty().trim(),
            roomCode = prefs.getString(KEY_ROOM, "").orEmpty().trim(),
            turnUrl = prefs.getString(KEY_TURN_URL, "").orEmpty().trim(),
            turnUsername = prefs.getString(KEY_TURN_USER, "").orEmpty().trim(),
            turnPassword = prefs.getString(KEY_TURN_PASSWORD, "").orEmpty()
        )
        // Ogni telefono ha il suo codice stanza: senza questo il QR inquadrerebbe una stanza vuota.
        return if (stored.roomCode.isBlank()) {
            save(stored.copy(roomCode = newRoomCode()))
        } else {
            stored
        }
    }

    fun save(config: RemoteConfig): RemoteConfig {
        val roomCode = config.roomCode.ifBlank { newRoomCode() }
        prefs.edit()
            .putString(KEY_SIGNALING, config.signalingUrl.trim())
            .putString(KEY_ROOM, roomCode)
            .putString(KEY_TURN_URL, config.turnUrl.trim())
            .putString(KEY_TURN_USER, config.turnUsername.trim())
            .putString(KEY_TURN_PASSWORD, config.turnPassword)
            .apply()
        return config.copy(roomCode = roomCode)
    }

    companion object {
        private const val PREFS_NAME = "remote_webrtc"
        private const val KEY_SIGNALING = "signaling_url"
        private const val KEY_ROOM = "room_code"
        private const val KEY_TURN_URL = "turn_url"
        private const val KEY_TURN_USER = "turn_username"
        private const val KEY_TURN_PASSWORD = "turn_password"

        const val DEFAULT_STUN_URL = "stun:stun.l.google.com:19302"

        /** Solo wss:// è accettabile fuori casa: ws:// in chiaro esporrebbe SDP e IP. */
        fun isValidSignalingUrl(url: String): Boolean {
            val trimmed = url.trim()
            return trimmed.startsWith("wss://") ||
                trimmed.startsWith("ws://localhost") ||
                trimmed.startsWith("ws://127.0.0.1")
        }

        /** turn:host:3478, turns:host:5349 e turn:host?transport=tcp sono tutti accettati. */
        fun normalizeTurnUrl(url: String): String {
            val trimmed = url.trim()
            return if (trimmed.startsWith("turn:") || trimmed.startsWith("turns:")) {
                trimmed
            } else {
                "turn:$trimmed"
            }
        }

        fun newRoomCode(random: SecureRandom = SecureRandom()): String {
            val first = 1000 + random.nextInt(9000)
            val second = 1000 + random.nextInt(9000)
            return "CSC-$first-$second"
        }
    }
}
