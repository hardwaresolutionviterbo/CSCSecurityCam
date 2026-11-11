package com.example.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): UserEntity?

    @Upsert
    suspend fun upsert(user: UserEntity)

    @Query("UPDATE users SET fullName = :fullName, photoPath = :photoPath WHERE id = :id")
    suspend fun updateProfile(id: String, fullName: String, photoPath: String)

    @Query("UPDATE users SET plan = :plan, planExpiresAtMillis = :expiresAt WHERE id = :id")
    suspend fun updateSubscription(id: String, plan: String, expiresAt: Long)
}
