package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NetworkStatus(
    val isConnected: Boolean = true,
    val isWifi: Boolean = true,
    val currentIp: String = "192.168.0.9",
    val ssid: String? = null
)

class NetworkRecoveryManager(
    private val context: Context,
    private val onNetworkRestored: ((newIp: String) -> Unit)? = null,
    private val onNetworkLost: (() -> Unit)? = null
) {
    private val tag = "NetworkRecoveryManager"
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkStatus = MutableStateFlow(NetworkStatus())
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val ip = resolveLocalIp()

            Log.i(tag, "Network available. isWifi=$isWifi, IP=$ip")
            _networkStatus.value = NetworkStatus(
                isConnected = true,
                isWifi = isWifi,
                currentIp = ip
            )
            onNetworkRestored?.invoke(ip)
        }

        override fun onLost(network: Network) {
            Log.w(tag, "Network lost")
            _networkStatus.value = _networkStatus.value.copy(
                isConnected = false
            )
            onNetworkLost?.invoke()
        }
    }

    fun start() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            _networkStatus.value = _networkStatus.value.copy(currentIp = resolveLocalIp())
        } catch (e: Exception) {
            Log.e(tag, "Failed to register network callback", e)
        }
    }

    fun stop() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (ignored: Exception) {}
    }

    private fun resolveLocalIp(): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                @Suppress("DEPRECATION")
                val ipStr = Formatter.formatIpAddress(ipInt)
                if (ipStr.isNotEmpty() && ipStr != "0.0.0.0") ipStr else "192.168.0.9"
            } else "192.168.0.9"
        } catch (e: Exception) {
            "192.168.0.9"
        }
    }
}
