package com.example.pairing

import java.net.URI
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

    fun parse(raw: String): PairingInfo? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (text.startsWith("csc://", ignoreCase = true)) {
            return parseUri(text) ?: parseHostAndPort(text.substringAfter("://").substringAfter("cam"))
        }
        return parseHostAndPort(text)
    }

    private fun parseUri(text: String): PairingInfo? {
        return try {
            val uri = URI(text)
            if (!uri.scheme.equals("csc", ignoreCase = true)) return null
            if (!uri.host.equals("cam", ignoreCase = true)) return null

            val params = (uri.rawQuery ?: "")
                .split("&")
                .mapNotNull { entry ->
                    val separator = entry.indexOf('=')
                    if (separator <= 0) {
                        null
                    } else {
                        URLDecoder.decode(entry.substring(0, separator), "UTF-8") to
                            URLDecoder.decode(entry.substring(separator + 1), "UTF-8")
                    }
                }
                .toMap()

            val ip = params["ip"]?.trim() ?: return null
            if (!isValidIp(ip)) return null
            val port = params["port"]?.toIntOrNull() ?: DEFAULT_PORT
            if (!isValidPort(port)) return null
            PairingInfo(ip = ip, port = port, id = params["id"]?.trim()?.takeIf { it.isNotEmpty() })
        } catch (e: Exception) {
            null
        }
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
