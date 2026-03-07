package com.example.expenseapp.core.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val preferenceManager: PreferenceManager
) {
    val currentUserFlow: Flow<String?> = preferenceManager.userId
    
    var currentUserId: String? = null // Temporary keep for compatibility if needed, but ideally move to Flow

    suspend fun saveSession(userId: String) {
        currentUserId = userId
        preferenceManager.saveUserId(userId)
    }

    suspend fun getUserId(): String? {
        if (currentUserId != null) return currentUserId
        val userId = currentUserFlow.firstOrNull()
        currentUserId = userId
        return userId
    }

    suspend fun clearSession() {
        currentUserId = null
        preferenceManager.clear()
    }
}
