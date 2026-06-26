package com.example.expenseapp.domain.repository

import com.example.expenseapp.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getCurrentUser(): Flow<User?>
    fun getAllUsers(): Flow<List<User>>
    suspend fun getUserById(id: String): User?
    suspend fun updateProfile(user: User)
    suspend fun uploadAvatar(imageBytes: ByteArray): String
    suspend fun syncUserFromSupabase(userId: String? = null, force: Boolean = false): User?
    suspend fun updateFcmToken(token: String?)
    suspend fun signOut()
}
