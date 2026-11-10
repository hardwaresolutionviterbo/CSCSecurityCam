package com.example.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.Random

data class StunResult(
    val publicIp: String,
    val publicPort: Int,
    val localPort: Int,
    val natType: String,
    val latencyMs: Long
)

class StunClient {
    private val tag = "StunClient"

    companion object {
        const val DEFAULT_STUN_SERVER = "stun.l.google.com"
        const val DEFAULT_STUN_PORT = 19302
        private const val MAGIC_COOKIE = 0x2112A442
        private const val BINDING_REQUEST = 0x0001
        private const val BINDING_RESPONSE = 0x0101
        private const val ATTR_MAPPED_ADDRESS = 0x0001
        private const val ATTR_XOR_MAPPED_ADDRESS = 0x0020
    }

    suspend fun probe(
        serverHost: String = DEFAULT_STUN_SERVER,
        serverPort: Int = DEFAULT_STUN_PORT,
        timeoutMs: Int = 3000
    ): StunResult? = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            val serverAddress = InetAddress.getByName(serverHost)
            socket = DatagramSocket().apply {
                soTimeout = timeoutMs
            }
            val localPort = socket.localPort

            // Construct RFC 5389 Binding Request (20 bytes)
            val request = ByteArray(20)
            val buffer = ByteBuffer.wrap(request)
            buffer.putShort(BINDING_REQUEST.toShort()) // Message Type
            buffer.putShort(0.toShort())              // Message Length (0 attributes)
            buffer.putInt(MAGIC_COOKIE)               // Magic Cookie

            val transactionId = ByteArray(12)
            Random().nextBytes(transactionId)
            buffer.put(transactionId)

            val startTime = System.currentTimeMillis()
            val sendPacket = DatagramPacket(request, request.size, serverAddress, serverPort)
            socket.send(sendPacket)

            // Receive response
            val responseBytes = ByteArray(512)
            val receivePacket = DatagramPacket(responseBytes, responseBytes.size)
            socket.receive(receivePacket)
            val latency = System.currentTimeMillis() - startTime

            val respBuffer = ByteBuffer.wrap(responseBytes, 0, receivePacket.length)
            val msgType = respBuffer.short.toInt() and 0xFFFF
            val msgLength = respBuffer.short.toInt() and 0xFFFF
            val magic = respBuffer.int

            if (msgType != BINDING_RESPONSE || magic != MAGIC_COOKIE) {
                Log.w(tag, "Invalid STUN response header: type=$msgType, magic=$magic")
                return@withContext null
            }

            // Skip transaction ID
            respBuffer.position(20)

            var publicIp: String? = null
            var publicPort: Int? = null

            // Parse Attributes
            while (respBuffer.remaining() >= 4) {
                val attrType = respBuffer.short.toInt() and 0xFFFF
                val attrLen = respBuffer.short.toInt() and 0xFFFF

                if (respBuffer.remaining() < attrLen) break

                when (attrType) {
                    ATTR_XOR_MAPPED_ADDRESS -> {
                        val pos = respBuffer.position()
                        respBuffer.get() // Reserved
                        val family = respBuffer.get().toInt() and 0xFF
                        val xorPort = respBuffer.short.toInt() and 0xFFFF
                        val decodedPort = xorPort xor (MAGIC_COOKIE ushr 16)

                        if (family == 0x01) { // IPv4
                            val xorIp = respBuffer.int
                            val decodedIpInt = xorIp xor MAGIC_COOKIE
                            val ip = String.format(
                                "%d.%d.%d.%d",
                                (decodedIpInt ushr 24) and 0xFF,
                                (decodedIpInt ushr 16) and 0xFF,
                                (decodedIpInt ushr 8) and 0xFF,
                                decodedIpInt and 0xFF
                            )
                            publicIp = ip
                            publicPort = decodedPort
                        }
                        respBuffer.position(pos + attrLen)
                    }
                    ATTR_MAPPED_ADDRESS -> {
                        val pos = respBuffer.position()
                        respBuffer.get() // Reserved
                        val family = respBuffer.get().toInt() and 0xFF
                        val port = respBuffer.short.toInt() and 0xFFFF
                        if (family == 0x01) { // IPv4
                            val b1 = respBuffer.get().toInt() and 0xFF
                            val b2 = respBuffer.get().toInt() and 0xFF
                            val b3 = respBuffer.get().toInt() and 0xFF
                            val b4 = respBuffer.get().toInt() and 0xFF
                            val ip = "$b1.$b2.$b3.$b4"
                            if (publicIp == null) {
                                publicIp = ip
                                publicPort = port
                            }
                        }
                        respBuffer.position(pos + attrLen)
                    }
                    else -> {
                        // Skip unhandled attribute
                        respBuffer.position(respBuffer.position() + attrLen)
                    }
                }
            }

            if (publicIp != null && publicPort != null) {
                val natType = if (publicPort == localPort) "Full Cone NAT (Direct/Open)" else "Port-Restricted NAT"
                Log.i(tag, "STUN probe successful: $publicIp:$publicPort via $serverHost in ${latency}ms ($natType)")
                StunResult(
                    publicIp = publicIp,
                    publicPort = publicPort,
                    localPort = localPort,
                    natType = natType,
                    latencyMs = latency
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(tag, "STUN probe failed or timed out: ${e.message}")
            null
        } finally {
            socket?.close()
        }
    }
}
