package com.example.pairing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** Esito esplicito della prova di connessione verso una telecamera. */
sealed class ProbeResult {
    /** Il server ha risposto a /status con un JSON valido. */
    data class Reachable(val fps: Int, val clients: Int) : ProbeResult()

    /** Nessuna risposta entro il timeout (IP inesistente, host spento, Wi-Fi diversa). */
    data object Timeout : ProbeResult()

    /** La porta è chiusa: connessione rifiutata. */
    data object Refused : ProbeResult()

    /** Qualcosa ha risposto, ma non è un server CSCSecurityCam. */
    data class Unexpected(val reason: String) : ProbeResult()

    fun describe(): String = when (this) {
        is Reachable -> "Raggiungibile • fps $fps • ${if (clients == 1) "1 client" else "$clients client"}"
        Timeout -> "Timeout: nessuna risposta"
        Refused -> "Connessione rifiutata"
        is Unexpected -> "Risposta inattesa: $reason"
    }
}

/**
 * Prova reale: GET http://<ip>:<porta>/status con timeout breve. Non salva nulla, serve solo a
 * decidere se il dispositivo esiste davvero prima di aggiungerlo all'elenco.
 */
object CameraProbe {

    suspend fun probe(ip: String, port: Int = PairingPayload.DEFAULT_PORT, timeoutMs: Int = 2_500): ProbeResult =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("http://$ip:$port/status")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    requestMethod = "GET"
                    useCaches = false
                }

                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    return@withContext ProbeResult.Unexpected("HTTP $code")
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val fps = Regex("\"fps\"\\s*:\\s*(\\d+)")
                    .find(body)?.groupValues?.get(1)?.toIntOrNull()
                val clients = Regex("\"clients\"\\s*:\\s*(\\d+)")
                    .find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0

                if (!body.contains("\"status\"") || fps == null) {
                    ProbeResult.Unexpected("non sembra un server CSCSecurityCam")
                } else {
                    ProbeResult.Reachable(fps = fps, clients = clients)
                }
            } catch (e: SocketTimeoutException) {
                ProbeResult.Timeout
            } catch (e: ConnectException) {
                ProbeResult.Refused
            } catch (e: Exception) {
                ProbeResult.Unexpected(e.message ?: e.javaClass.simpleName)
            } finally {
                connection?.disconnect()
            }
        }
}
