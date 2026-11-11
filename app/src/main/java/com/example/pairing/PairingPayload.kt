package com.example.pairing

import java.net.URLDecoder
import java.net.URLEncoder

/** Dati minimi per collegare una telecamera: indirizzo, porta e identificativo opzionale. */
data class PairingInfo(
    val ip: String,
    val port: Int = PairingPayload.DEFAULT_PORT,
    val id: String? = null
)

/**
 * Formato del QR mostrato dal telefono videocamera:
 * `csc://cam?ip=192.168.1.10&port=8765&id=cam_192.168.1.10`
 *
 * Per comodità accetta anche il testo `ip:porta` e il solo indirizzo IP, così l'accoppiamento
 * funziona anche incollando a mano quello che si legge dal telefono videocamera.
 */
object PairingPayload {

    const val DEFAULT_PORT = 8765

    private val IPV4 = Regex(
        "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$"
    )

    fun isValidIp(ip: String): Boolean = IPV4.matches(ip.trim())

    fun isValidPort(port: Int): Boolean = port in 1..65_535

    fun build(ip: String, port: Int = DEFAULT_PORT, id: String? = null): String {
        val base = "csc://cam?ip=$ip&port=$port"
        return if (id.isNullOrBlank()) {
            base
        } else {
            "$base&id=${URLEncoder.encode(id, "UTF-8")}"
        }
    }

    /**
     * Lettura manuale della query, senza URI: i link generati dalla telecamera contengono anche
     * `p2p` e `nat` con spazi e parentesi (es. "Symmetric NAT (Fallback TURN Relay)") che facevano
     * fallire la parsers basata su java.net.URI, quindi il QR non accoppiava mai.
     */
    fun parse(raw: String): PairingInfo? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (!text.startsWith("csc://", ignoreCase = true)) {
            return parseHostAndPort(text)
        }

        val withoutScheme = text.substring("csc://".length)
        val host = withoutScheme.substringBefore('?').substringBefore('/').trim()
        if (!host.equals("cam", ignoreCase = true)) return null

        val query = withoutScheme.substringAfter('?', "")
        if (query.isBlank()) return null

        val params = query
            .split("&")
            .mapNotNull { entry ->
                val separator = entry.indexOf('=')
                if (separator <= 0) {
                    null
                } else {
                    decode(entry.substring(0, separator)) to decode(entry.substring(separator + 1))
                }
            }
            .toMap()

        val ip = params["ip"]?.trim() ?: return null
        if (!isValidIp(ip)) return null
        val port = params["port"]?.trim()?.toIntOrNull() ?: DEFAULT_PORT
        if (!isValidPort(port)) return null
        return PairingInfo(ip = ip, port = port, id = params["id"]?.trim()?.takeIf { it.isNotEmpty() })
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (e: Exception) {
        value
    }

    private fun parseHostAndPort(text: String): PairingInfo? {
        val cleaned = text
            .removePrefix("http://")
            .removePrefix("https://")
            .trim()
            .trimEnd('/')
        if (cleaned.isEmpty()) return null

        val hostAndPort = cleaned.substringBefore('/')
        val parts = hostAndPort.split(':')
        val ip = parts.firstOrNull()?.trim() ?: return null
        if (!isValidIp(ip)) return null
        val port = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: DEFAULT_PORT
        if (!isValidPort(port)) return null
        return PairingInfo(ip = ip, port = port)
    }
}
