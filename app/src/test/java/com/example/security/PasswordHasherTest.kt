package com.example.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    private val iterations = 400

    @Test
    fun `same password and salt produce the same hash`() {
        val salt = PasswordHasher.newSalt()
        assertEquals(
            PasswordHasher.hash("Segreta123", salt, iterations),
            PasswordHasher.hash("Segreta123", salt, iterations)
        )
    }

    @Test
    fun `different salts produce different hashes`() {
        val first = PasswordHasher.hash("Segreta123", PasswordHasher.newSalt(), iterations)
        val second = PasswordHasher.hash("Segreta123", PasswordHasher.newSalt(), iterations)
        assertNotEquals(first, second)
    }

    @Test
    fun `verify accepts the right password only`() {
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash("Segreta123", salt, iterations)
        assertTrue(PasswordHasher.verify("Segreta123", salt, hash, iterations))
        assertFalse(PasswordHasher.verify("segreta123", salt, hash, iterations))
        assertFalse(PasswordHasher.verify("", salt, hash, iterations))
    }

    @Test
    fun `hash is hex and never contains the plain password`() {
        val hash = PasswordHasher.hash("Segreta123", PasswordHasher.newSalt(), iterations)
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
        assertFalse(hash.contains("Segreta"))
        assertTrue(PasswordHasher.newSalt().length >= 32)
    }
}
