package com.example.data

import android.content.Context
import com.example.auth.GoogleAccount
import com.example.model.AuthProvider
import com.example.model.SubscriptionPlan
import com.example.model.UserProfile
import com.example.security.PasswordHasher
import java.util.UUID

sealed class AuthResult {
    data class Success(val user: UserProfile) : AuthResult()
    data class Failure(val message: String) : AuthResult()
}

/**
 * Account locali verificati davvero: la password non viene mai salvata, solo hash PBKDF2 con
 * salt casuale. La sessione sopravvive alla chiusura dell'app finché l'utente non esce.
 */
class AuthRepository(
    context: Context,
    private val dao: UserDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val iterations: Int = PasswordHasher.ITERATIONS
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun register(fullName: String, email: String, password: String): AuthResult {
        val name = fullName.trim()
        val normalized = normalizeEmail(email)
        if (name.length < 2) return AuthResult.Failure("Inserisci nome e cognome")
        if (!isValidEmail(normalized)) return AuthResult.Failure("Formato email non valido")
        if (password.length < MIN_PASSWORD_LENGTH) {
            return AuthResult.Failure("La password deve avere almeno $MIN_PASSWORD_LENGTH caratteri")
        }
        if (dao.findByEmail(normalized) != null) {
            return AuthResult.Failure("Esiste già un account con questa email")
        }

        val salt = PasswordHasher.newSalt()
        val user = UserEntity(
            id = UUID.randomUUID().toString(),
            fullName = name,
            email = normalized,
            passwordHash = PasswordHasher.hash(password, salt, iterations),
            passwordSalt = salt,
            passwordIterations = iterations,
            provider = AuthProvider.EMAIL.name,
            photoPath = "",
            plan = SubscriptionPlan.FREE.name,
            planExpiresAtMillis = 0L,
            createdAtMillis = clock()
        )
        dao.upsert(user)
        rememberSession(user.id, rememberMe = true)
        return AuthResult.Success(user.toProfile())
    }

    suspend fun login(email: String, password: String, rememberMe: Boolean = true): AuthResult {
        val normalized = normalizeEmail(email)
        if (!isValidEmail(normalized)) return AuthResult.Failure("Formato email non valido")
        if (password.isEmpty()) return AuthResult.Failure("Inserisci la password")
        val user = dao.findByEmail(normalized)
            ?: return AuthResult.Failure("Nessun account registrato con questa email")
        if (user.passwordHash.isBlank()) {
            return AuthResult.Failure("Questo account usa l'accesso con Google")
        }
        val valid = PasswordHasher.verify(
            password = password,
            saltHex = user.passwordSalt,
            expectedHashHex = user.passwordHash,
            iterations = user.passwordIterations
        )
        if (!valid) return AuthResult.Failure("Password non corretta")

        rememberSession(user.id, rememberMe)
        return AuthResult.Success(user.toProfile())
    }

    /** Accesso Google: l'identità arriva da Credential Manager, l'account locale viene creato o riusato. */
    suspend fun signInWithGoogle(account: GoogleAccount): AuthResult {
        val normalized = normalizeEmail(account.email)
        if (!isValidEmail(normalized)) {
            return AuthResult.Failure("L'account Google non ha restituito un'email valida")
        }
        val existing = dao.findByEmail(normalized)
        if (existing != null) {
            rememberSession(existing.id, rememberMe = true)
            return AuthResult.Success(existing.toProfile())
        }

        val user = UserEntity(
            id = UUID.randomUUID().toString(),
            fullName = account.displayName.trim().ifBlank { normalized.substringBefore("@") },
            email = normalized,
            passwordHash = "",
            passwordSalt = "",
            passwordIterations = 0,
            provider = AuthProvider.GOOGLE.name,
            photoPath = "",
            plan = SubscriptionPlan.FREE.name,
            planExpiresAtMillis = 0L,
            createdAtMillis = clock()
        )
        dao.upsert(user)
        rememberSession(user.id, rememberMe = true)
        return AuthResult.Success(user.toProfile())
    }

    suspend fun updateProfile(userId: String, fullName: String, photoPath: String): UserProfile? {
        val current = dao.findById(userId) ?: return null
        val name = fullName.trim().ifBlank { current.fullName }
        dao.updateProfile(id = userId, fullName = name, photoPath = photoPath)
        return dao.findById(userId)?.toProfile()
    }

    /** Aggiorna piano e scadenza: qui scriverà l'acquisto (Google Play) o il server. */
    suspend fun setSubscription(
        userId: String,
        plan: SubscriptionPlan,
        expiresAtMillis: Long
    ): UserProfile? {
        if (dao.findById(userId) == null) return null
        dao.updateSubscription(userId, plan.name, expiresAtMillis)
        return dao.findById(userId)?.toProfile()
    }

    fun rememberSession(userId: String, rememberMe: Boolean) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putBoolean(KEY_REMEMBER, rememberMe)
            .apply()
    }

    /** Utente della sessione salvata, se l'accesso era "ricordami". */
    suspend fun restoreSession(): UserProfile? {
        if (!prefs.getBoolean(KEY_REMEMBER, false)) return null
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        return dao.findById(userId)?.toProfile()
    }

    fun signOut() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .putBoolean(KEY_REMEMBER, false)
            .apply()
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 6
        private const val PREFS_NAME = "csc_auth"
        private const val KEY_USER_ID = "active_user_id"
        private const val KEY_REMEMBER = "remember_me"
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun normalizeEmail(email: String): String = email.trim().lowercase()

        fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email)
    }
}
