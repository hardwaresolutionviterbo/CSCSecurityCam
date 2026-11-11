package com.example.pairing

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Indirizzo IPv4 privato del telefono sulla rete locale.
 *
 * `WifiManager.connectionInfo.ipAddress` è deprecato e su Android 10+ restituisce spesso 0: il QR
 * finiva con `ip=` vuoto, quindi chi lo scansionava non riusciva ad accoppiare nulla e restava
 * solo l'inserimento manuale. Qui si legge prima da ConnectivityManager, poi dalle interfacce.
 */
object LocalAddress {

    fun resolve(context: Context): String? {
        connectivityAddress(context)?.let { return it }
        interfaceAddress()?.let { return it }
        legacyWifiAddress(context)?.let { return it }
        return null
    }

    private fun connectivityAddress(context: Context): String? {
        return try {
            val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
            val network = manager.activeNetwork ?: return null
            val properties = manager.getLinkProperties(network) ?: return null
            properties.linkAddresses.firstNotNullOfOrNull { link ->
                val address = link.address
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    address.hostAddress?.takeIf { isPrivate(it) }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun interfaceAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.firstNotNullOfOrNull { nif ->
                if (!nif.isUp || nif.isLoopback) return@firstNotNullOfOrNull null
                nif.inetAddresses.toList().firstNotNullOfOrNull { address ->
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        address.hostAddress?.takeIf { isPrivate(it) }
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyWifiAddress(context: Context): String? {
        return try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val value = wifi?.connectionInfo?.ipAddress ?: 0
            if (value == 0) return null
            val ip = "${value and 0xFF}.${(value shr 8) and 0xFF}." +
                "${(value shr 16) and 0xFF}.${(value shr 24) and 0xFF}"
            ip.takeIf { isPrivate(it) }
        } catch (e: Exception) {
            null
        }
    }

    /** Solo indirizzi di rete locale: un IP pubblico o loopback non serve all'accoppiamento. */
    private fun isPrivate(ip: String?): Boolean {
        if (ip.isNullOrBlank()) return false
        val parts = ip.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        val first = parts[0]
        val second = parts[1]
        return first == 10 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168)
    }
}
