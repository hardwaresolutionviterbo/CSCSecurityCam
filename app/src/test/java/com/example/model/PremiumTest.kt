package com.example.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    @Test
    fun `free plan allows a single camera`() {
        assertEquals(1, Premium.cameraLimit(isPremium = false))
        assertTrue(Premium.canAddCamera(currentCount = 0, isPremium = false))
        assertFalse(Premium.canAddCamera(currentCount = 1, isPremium = false))
    }

    @Test
    fun `premium plan removes the camera limit`() {
        assertTrue(Premium.canAddCamera(currentCount = 1, isPremium = true))
        assertTrue(Premium.canAddCamera(currentCount = 40, isPremium = true))
        assertEquals("3,99 €/mese", Premium.PREMIUM_PRICE_LABEL)
    }

    @Test
    fun `days remaining counts the subscription expiry`() {
        assertEquals(25, Premium.daysRemaining(now + 25 * day, now))
        assertEquals(1, Premium.daysRemaining(now + day / 2, now))
        assertEquals(0, Premium.daysRemaining(now - day, now))
    }

    @Test
    fun `expiry label shows days left only while premium is active`() {
        val active = UserProfile(
            isGuest = false,
            isLoggedIn = true,
            plan = SubscriptionPlan.PREMIUM,
            planExpiresAtMillis = now + 25 * day
        )
        assertTrue(active.isPremium(now))
        assertEquals("Mancano 25 giorni", Premium.expiryLabel(active, now))
        assertEquals("Premium", Premium.planLabel(active, now))

        val expired = active.copy(planExpiresAtMillis = now - day)
        assertFalse(expired.isPremium(now))
        assertEquals("Premium scaduto", Premium.expiryLabel(expired, now))
        assertEquals("Free", Premium.planLabel(expired, now))

        val free = active.copy(plan = SubscriptionPlan.FREE, planExpiresAtMillis = 0L)
        assertEquals("Piano Free", Premium.expiryLabel(free, now))
    }
}
