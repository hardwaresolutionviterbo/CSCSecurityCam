package it.cscsecuritycam.network

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    fun localIpv4Address(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            val candidates = mutableListOf<Pair<Int, String>>()
            while (interfaces.hasMoreElements()) {
                val network = interfaces.nextElement()
                if (!network.isUp || network.isLoopback) continue
                val preferred = if (network.name.startsWith("wlan") || network.name.startsWith("ap")) 0 else 1
                val addresses = network.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) {
                        candidates += preferred to address.hostAddress.orEmpty()
                    }
                }
            }
            candidates.sortedBy { it.first }.firstOrNull()?.second
        } catch (_: Exception) {
            null
        }
    }
}
