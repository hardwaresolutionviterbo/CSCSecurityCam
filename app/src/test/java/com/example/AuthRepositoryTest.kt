package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.auth.GoogleAccount
import com.example.data.AppDatabase
import com.example.data.AuthRepository
import com.example.data.AuthResult
import com.example.model.SubscriptionPlan
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthRepositoryTest {

    private lateinit var db: AppDatabase

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context(), AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
        context().getSharedPreferences("csc_auth", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun repository() = AuthRepository(
        context = context(),
        dao = db.userDao(),
        iterations = 800
    )

    @Test
    fun `register stores a hash and logs the new account in`() = runBlocking {
        val repository = repository()
        val result = repository.register("Mario Rossi", "Mario.Rossi@Example.com", "Segreta123")

        assertTrue(result is AuthResult.Success)
        val user = (result as AuthResult.Success).user
        assertTrue(user.isLoggedIn)
        assertFalse(user.isGuest)
        assertEquals("mario.rossi@example.com", user.email)
        assertEquals("Mario Rossi", user.fullName)
        assertEquals(SubscriptionPlan.FREE, user.plan)

        // La password non è mai salvata in chiaro: solo hash + salt.
        val stored = db.userDao().findById(user.userId)
        assertNotNull(stored)
        assertFalse(stored!!.passwordHash.contains("Segreta123"))
        assertTrue(stored.passwordSalt.isNotBlank())
    }

    @Test
    fun `login verifies the password and rejects the wrong one`() = runBlocking {
        val repository = repository()
        repository.register("Mario Rossi", "mario@example.com", "Segreta123")

        val wrong = repository.login("mario@example.com", "sbagliata")
        assertTrue(wrong is AuthResult.Failure)
        assertEquals("Password non corretta", (wrong as AuthResult.Failure).message)

        val ok = repository.login("mario@example.com", "Segreta123")
        assertTrue(ok is AuthResult.Success)
        assertEquals("mario@example.com", (ok as AuthResult.Success).user.email)

        val unknown = repository.login("nessuno@example.com", "Segreta123")
        assertTrue(unknown is AuthResult.Failure)
    }

    @Test
    fun `register rejects a duplicate email`() = runBlocking {
        val repository = repository()
        repository.register("Mario Rossi", "mario@example.com", "Segreta123")
        val duplicate = repository.register("Altro Nome", "MARIO@example.com", "Altra456")

        assertTrue(duplicate is AuthResult.Failure)
        assertEquals(
            "Esiste già un account con questa email",
            (duplicate as AuthResult.Failure).message
        )
    }

    @Test
    fun `google account is created once and reused on the next sign in`() = runBlocking {
        val repository = repository()
        val account = GoogleAccount("luca@example.com", "Luca Bianchi", "google-sub-1")

        val first = repository.signInWithGoogle(account)
        val second = repository.signInWithGoogle(account)

        assertTrue(first is AuthResult.Success && second is AuthResult.Success)
        assertEquals(
            (first as AuthResult.Success).user.userId,
            (second as AuthResult.Success).user.userId
        )
        val stored = db.userDao().findByEmail("luca@example.com")
        assertNotNull(stored)
        // Account Google: nessuna password locale da verificare.
        assertEquals("", stored!!.passwordHash)
    }

    @Test
    fun `session is restored until the user signs out`() = runBlocking {
        repository().register("Mario Rossi", "mario@example.com", "Segreta123")

        val restored = repository().restoreSession()
        assertNotNull(restored)
        assertEquals("mario@example.com", restored!!.email)

        repository().signOut()
        assertNull(repository().restoreSession())
    }

    @Test
    fun `subscription expiry makes premium inactive`() = runBlocking {
        val repository = repository()
        val user = (repository.register("Mario", "mario@example.com", "Segreta123") as AuthResult.Success).user

        val active = repository.setSubscription(
            user.userId,
            SubscriptionPlan.PREMIUM,
            System.currentTimeMillis() + 25L * 86_400_000L
        )
        assertNotNull(active)
        assertTrue(active!!.isPremium())

        val expired = repository.setSubscription(
            user.userId,
            SubscriptionPlan.PREMIUM,
            System.currentTimeMillis() - 86_400_000L
        )
        assertNotNull(expired)
        assertFalse(expired!!.isPremium())
    }
}
