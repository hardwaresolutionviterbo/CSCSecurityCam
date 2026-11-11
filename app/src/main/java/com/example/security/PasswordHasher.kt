package com.example.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * PBKDF2-HMAC-SHA256 scritto a mano invece di SecretKeyFactory: su Android
 * "PBKDF2WithHmacSHA256" esiste solo da API 26 e questo progetto parte da API 24.
 * Salt casuale per utente, confronto a tempo costante, nessuna password in chiaro.
 */
object PasswordHasher {

    const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BYTES = 32
    private const val SALT_BYTES = 16

    fun newSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun hash(password: String, saltHex: String, iterations: Int = ITERATIONS): String {
        val salt = saltHex.hexToBytes()
        return derive(password, salt, iterations, KEY_LENGTH_BYTES).toHex()
    }

    fun verify(
        password: String,
        saltHex: String,
        expectedHashHex: String,
        iterations: Int = ITERATIONS
    ): Boolean {
        // HMAC non accetta una chiave vuota: una password vuota non è mai valida.
        if (password.isEmpty() || expectedHashHex.isBlank()) return false
        val expected = expectedHashHex.hexToBytes()
        val actual = derive(password, saltHex.hexToBytes(), iterations, expected.size)
        return java.security.MessageDigest.isEqual(expected, actual)
    }

    /** PBKDF2 (RFC 2898) con HMAC-SHA256, blocchi concatenati e troncamento finale. */
    internal fun derive(
        password: String,
        salt: ByteArray,
        iterations: Int,
        keyLengthBytes: Int
    ): ByteArray {
        require(iterations > 0) { "Le iterazioni devono essere positive" }
        require(keyLengthBytes > 0) { "La lunghezza della chiave deve essere positiva" }

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hashLength = mac.macLength
        val blocks = (keyLengthBytes + hashLength - 1) / hashLength
        val output = ByteArray(blocks * hashLength)

        for (block in 1..blocks) {
            mac.update(salt)
            mac.update(
                byteArrayOf(
                    (block ushr 24).toByte(),
                    (block ushr 16).toByte(),
                    (block ushr 8).toByte(),
                    block.toByte()
                )
            )
            var u = mac.doFinal()
            val t = u.copyOf()
            for (round in 2..iterations) {
                u = mac.doFinal(u)
                for (i in t.indices) {
                    t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
                }
            }
            System.arraycopy(t, 0, output, (block - 1) * hashLength, hashLength)
        }
        return output.copyOf(keyLengthBytes)
    }

    private fun ByteArray.toHex(): String {
        val builder = StringBuilder(size * 2)
        for (byte in this) {
            builder.append(HEX[(byte.toInt() shr 4) and 0x0f])
            builder.append(HEX[byte.toInt() and 0x0f])
        }
        return builder.toString()
    }

    private fun String.hexToBytes(): ByteArray {
        val clean = trim()
        require(clean.length % 2 == 0) { "Hex non valido" }
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            val high = Character.digit(clean[i * 2], 16)
            val low = Character.digit(clean[i * 2 + 1], 16)
            require(high >= 0 && low >= 0) { "Hex non valido" }
            result[i] = ((high shl 4) or low).toByte()
        }
        return result
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
