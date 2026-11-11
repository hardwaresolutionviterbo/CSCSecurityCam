package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.AuthProvider
import com.example.model.SubscriptionPlan
import com.example.model.UserProfile

@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true)])
data class UserEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val email: String,
    /** Hash PBKDF2 in hex; vuoto per gli account creati con Google. */
    val passwordHash: String,
    val passwordSalt: String,
    val passwordIterations: Int,
    val provider: String,
    val photoPath: String,
    val plan: String,
    val planExpiresAtMillis: Long,
    val createdAtMillis: Long
)

fun UserEntity.toProfile(): UserProfile = UserProfile(
    userId = id,
    email = email,
    fullName = fullName,
    isGuest = false,
    isLoggedIn = true,
    provider = runCatching { AuthProvider.valueOf(provider) }.getOrDefault(AuthProvider.EMAIL),
    photoPath = photoPath,
    plan = runCatching { SubscriptionPlan.valueOf(plan) }.getOrDefault(SubscriptionPlan.FREE),
    planExpiresAtMillis = planExpiresAtMillis,
    createdAtMillis = createdAtMillis
)
