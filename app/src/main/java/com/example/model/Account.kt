package com.example.model

/** Come è nato l'account: email + password oppure accesso Google verificato. */
enum class AuthProvider { EMAIL, GOOGLE }

enum class SubscriptionPlan { FREE, PREMIUM }

data class UserProfile(
    val userId: String = "",
    val email: String = "",
    val fullName: String = "Ospite",
    val isGuest: Boolean = true,
    val isLoggedIn: Boolean = false,
    val provider: AuthProvider = AuthProvider.EMAIL,
    /** Percorso del file della foto profilo copiato nello spazio privato dell'app. */
    val photoPath: String = "",
    val plan: SubscriptionPlan = SubscriptionPlan.FREE,
    val planExpiresAtMillis: Long = 0L,
    val createdAtMillis: Long = 0L
) {
    /** Premium attivo solo se il piano è Premium e la scadenza non è passata. */
    fun isPremium(nowMillis: Long = System.currentTimeMillis()): Boolean =
        plan == SubscriptionPlan.PREMIUM && planExpiresAtMillis > nowMillis
}

/** Regole del piano gratuito e del premium: un'unica fonte per UI e gate. */
object Premium {
    const val FREE_CAMERA_LIMIT = 1
    const val PREMIUM_PERIOD_DAYS = 30
    const val PREMIUM_PRICE_LABEL = "3,99 €/mese"
    const val PREMIUM_PRICE_CENTS = 399
    const val PLAY_STORE_URL =
        "https://play.google.com/store/apps/details?id=com.aistudio.cscsecuritycam.app"

    private const val MILLIS_PER_DAY = 86_400_000L

    /** Numero massimo di telecamere salvabili con il piano corrente. */
    fun cameraLimit(isPremium: Boolean): Int =
        if (isPremium) Int.MAX_VALUE else FREE_CAMERA_LIMIT

    fun canAddCamera(currentCount: Int, isPremium: Boolean): Boolean =
        currentCount < cameraLimit(isPremium)

    /** Giorni interi che mancano alla scadenza: 25 giorni restanti → 25. */
    fun daysRemaining(expiresAtMillis: Long, nowMillis: Long = System.currentTimeMillis()): Int {
        if (expiresAtMillis <= nowMillis) return 0
        return ((expiresAtMillis - nowMillis + MILLIS_PER_DAY - 1) / MILLIS_PER_DAY).toInt()
    }

    /** Testo mostrato nell'area personale, es. "Mancano 25 giorni". */
    fun expiryLabel(profile: UserProfile, nowMillis: Long = System.currentTimeMillis()): String {
        if (profile.plan != SubscriptionPlan.PREMIUM) return "Piano Free"
        if (profile.planExpiresAtMillis <= nowMillis) return "Premium scaduto"
        val days = daysRemaining(profile.planExpiresAtMillis, nowMillis)
        return when (days) {
            0 -> "Scade oggi"
            1 -> "Manca 1 giorno"
            else -> "Mancano $days giorni"
        }
    }

    fun planLabel(profile: UserProfile, nowMillis: Long = System.currentTimeMillis()): String =
        if (profile.isPremium(nowMillis)) "Premium" else "Free"
}
