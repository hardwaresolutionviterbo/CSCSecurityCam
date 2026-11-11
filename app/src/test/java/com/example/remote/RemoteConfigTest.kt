package com.example.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RemoteConfigTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `room code is generated once and reused`() {
        val store = RemoteConfigStore(context)
        val first = store.load().roomCode
        val second = store.load().roomCode
        assertTrue(first.startsWith("CSC-"))
        assertEquals(first, second)
    }

    @Test
    fun `signaling url must be encrypted outside localhost`() {
        assertTrue(RemoteConfigStore.isValidSignalingUrl("wss://signal.example.com"))
        assertTrue(RemoteConfigStore.isValidSignalingUrl("ws://localhost:8787"))
        assertFalse(RemoteConfigStore.isValidSignalingUrl("ws://signal.example.com"))
        assertFalse(RemoteConfigStore.isValidSignalingUrl("https://signal.example.com"))
        assertFalse(RemoteConfigStore.isValidSignalingUrl(""))
    }

    @Test
    fun `turn url gets its scheme when missing`() {
        assertEquals("turn:turn.example.com:3478", RemoteConfigStore.normalizeTurnUrl("turn.example.com:3478"))
        assertEquals("turns:turn.example.com:5349", RemoteConfigStore.normalizeTurnUrl("turns:turn.example.com:5349"))
    }

    @Test
    fun `ice servers always include stun and the configured turn`() {
        val withoutTurn = RemoteConfig(roomCode = "CSC-1-2").iceServers()
        assertEquals(1, withoutTurn.size)
        assertEquals(RemoteConfigStore.DEFAULT_STUN_URL, withoutTurn.first().url)

        val withTurn = RemoteConfig(
            roomCode = "CSC-1-2",
            turnUrl = "turn.example.com",
            turnUsername = "user",
            turnPassword = "secret"
        ).iceServers()
        assertEquals(2, withTurn.size)
        assertEquals("turn:turn.example.com", withTurn.last().url)
        assertEquals("user", withTurn.last().username)
        assertEquals("secret", withTurn.last().credential)
    }

    @Test
    fun `saved configuration is reloaded from disk`() {
        val store = RemoteConfigStore(context)
        val saved = store.save(
            RemoteConfig(
                signalingUrl = "wss://signal.example.com",
                roomCode = "CSC-7777-8888",
                turnUrl = "turn.example.com:3478",
                turnUsername = "utente",
                turnPassword = "password"
            )
        )
        val reloaded = RemoteConfigStore(context).load()
        assertEquals(saved, reloaded)
    }
}
