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

    suspend fun saveSession(userId: String) {
        preferenceManager.saveUserId(userId)
    }

    /**
     * Always reads directly from DataStore to avoid stale in-memory state,
     * which could cause the wrong user to be shown after re-login or sign-out.
     */
    suspend fun getUserId(): String? {
        return currentUserFlow.firstOrNull()
    }

    suspend fun clearSession() {
        preferenceManager.clear()
    }
}
