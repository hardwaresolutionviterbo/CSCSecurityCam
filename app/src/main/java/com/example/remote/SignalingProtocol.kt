package com.example.remote

import org.json.JSONObject

/** Messaggio scambiato con il server di signaling. */
data class SignalMessage(
    val type: String,
    val room: String? = null,
    val role: String? = null,
    val sdpType: String? = null,
    val sdp: String? = null,
    val mid: String? = null,
    val mlineIndex: Int = 0,
    val candidate: String? = null,
    val message: String? = null
)

/**
 * Protocollo JSON minimale, indipendente dal server: una stanza contiene una telecamera e un
 * centro di controllo, il relay inoltra i messaggi all'altro partecipante senza interpretarli.
 */
object SignalingProtocol {
    const val JOIN = "join"
    const val SDP = "sdp"
    const val ICE = "ice"
    const val BYE = "bye"
    const val PEER_JOINED = "peer-joined"
    const val PEER_LEFT = "peer-left"
    const val ERROR = "error"

    fun join(room: String, role: String): String = JSONObject()
        .put("type", JOIN)
        .put("room", room)
        .put("role", role)
        .toString()

    fun sdp(sdpType: String, sdp: String): String = JSONObject()
        .put("type", SDP)
        .put("sdpType", sdpType)
        .put("sdp", sdp)
        .toString()

    fun ice(candidate: String, mid: String?, mlineIndex: Int): String = JSONObject()
        .put("type", ICE)
        .put("candidate", candidate)
        .put("mid", mid ?: "")
        .put("index", mlineIndex)
        .toString()

    fun bye(): String = JSONObject().put("type", BYE).toString()

    /** Restituisce null per payload non JSON o senza `type`: il chiamante chiude la sessione. */
    fun parse(raw: String?): SignalMessage? {
        if (raw.isNullOrBlank()) return null
        return try {
            val json = JSONObject(raw)
            val type = json.optString("type").takeIf { it.isNotBlank() } ?: return null
            SignalMessage(
                type = type,
                room = json.optStringOrNull("room"),
                role = json.optStringOrNull("role"),
                sdpType = json.optStringOrNull("sdpType"),
                sdp = json.optStringOrNull("sdp"),
                mid = json.optStringOrNull("mid"),
                mlineIndex = json.optInt("index", 0),
                candidate = json.optStringOrNull("candidate"),
                message = json.optStringOrNull("message")
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = optString(key)
        return if (value.isBlank()) null else value
    }
}
