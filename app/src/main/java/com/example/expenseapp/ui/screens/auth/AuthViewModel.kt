package com.example.expenseapp.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import com.example.expenseapp.core.session.SessionManager
import com.example.expenseapp.domain.repository.UserRepository
import com.example.expenseapp.domain.repository.GroupRepository
import com.example.expenseapp.domain.repository.ExpenseRepository
import com.example.expenseapp.data.local.AppDatabase
import com.example.expenseapp.data.local.dao.UserDao
import com.example.expenseapp.data.local.entity.UserEntity
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthEvent {
    object NavigateToHome : AuthEvent()
    data class NavigateToOnboarding(val userId: String?) : AuthEvent()
    data class ShowError(val message: String) : AuthEvent()
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val expenseRepository: ExpenseRepository,
    private val appDatabase: AppDatabase,
    private val userDao: UserDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>()
    val events = _events.asSharedFlow()

    // Expose persistent session for AppNavigation to determine start destination
    val currentUserId = sessionManager.currentUserFlow

    fun signUpWithEmail(name: String, email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            
            val trimmedEmail = email.trim().lowercase()
            Log.d("AuthViewModel", "Sign up attempt for: $trimmedEmail")
            
            if (trimmedEmail == "test@test.com" || trimmedEmail == "test2@test.com") {
                Log.d("AuthViewModel", "Bypassing Supabase for test account: $trimmedEmail")
                handleTestLogin(trimmedEmail)
                return@launch
            }

            try {
                Log.d("AuthViewModel", "Attempting real Supabase sign up...")
                supabaseClient.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
                
                // Sync user data to local DB. Use the ID from the current session.
                val authUser = supabaseClient.auth.currentUserOrNull()
                val finalUserId = authUser?.id ?: supabaseClient.auth.currentSessionOrNull()?.user?.id
                
                if (finalUserId != null) {
                    sessionManager.saveSession(finalUserId)
                    syncAllUserDataAfterLogin(finalUserId)
                    
                    val user = userRepository.getUserById(finalUserId)
                    
                    // Immediately update with the name provided during signup
                    if (user != null) {
                        userRepository.updateProfile(user.copy(name = name.trim()))
                    }
                    
                    // Navigate to onboarding if profile is incomplete (currency etc still needed)
                    if (user == null || user.name.isBlank()) {
                        _events.emit(AuthEvent.NavigateToOnboarding(finalUserId))
                    } else {
                        _events.emit(AuthEvent.NavigateToHome)
                    }
                } else {
                    // Not logged in after sign up (e.g., requires email confirmation)
                    _events.emit(AuthEvent.ShowError("Sign up successful! Please check your email to verify your account."))
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Supabase Sign Up Error", e)
                _uiState.value = AuthUiState(error = e.message ?: "Unknown error occurred")
                _events.emit(AuthEvent.ShowError(e.message ?: "Unknown error occurred"))
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            
            val trimmedEmail = email.trim().lowercase()
            Log.d("AuthViewModel", "Sign in attempt for: $trimmedEmail")
            
            // Check for test accounts - accept ANY password for these bypass accounts
            if (trimmedEmail == "test@test.com" || trimmedEmail == "test2@test.com") {
                Log.d("AuthViewModel", "Bypassing Supabase for test account: $trimmedEmail")
                handleTestLogin(trimmedEmail)
                return@launch
            }

            try {
                Log.d("AuthViewModel", "Attempting real Supabase sign in...")
                supabaseClient.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                
                // Sync user data to local DB
                val authUser = supabaseClient.auth.currentUserOrNull()
                val finalUserId = authUser?.id ?: supabaseClient.auth.currentSessionOrNull()?.user?.id
                if (finalUserId != null) {
                    sessionManager.saveSession(finalUserId)
                    syncAllUserDataAfterLogin(finalUserId)
                }
                
                val user = userRepository.getUserById(finalUserId ?: "")
                
                // Navigate to onboarding if profile is incomplete
                if (user == null || user.name.isBlank()) {
                    _events.emit(AuthEvent.NavigateToOnboarding(finalUserId))
                } else {
                    _events.emit(AuthEvent.NavigateToHome)
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Supabase Sign In Error", e)
                _uiState.value = AuthUiState(error = e.message ?: "Unknown error occurred")
                _events.emit(AuthEvent.ShowError(e.message ?: "Unknown error occurred"))
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private suspend fun syncAllUserDataAfterLogin(userId: String) {
        try {
            Log.d("AuthViewModel", "Starting proactive full sync for user $userId...")
            // Clear any local databases before syncing to prevent cross-user data persistence
            try {
                appDatabase.clearAllTables()
                Log.d("AuthViewModel", "Cleared all tables in Room before proactive sync")
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to clear Room tables", e)
            }

            // 1. Sync user profile
            userRepository.syncUserFromSupabase(userId, force = true)
            
            // 2. Sync groups
            groupRepository.syncGroupsFromSupabase(force = true)
            
            // 3. Get the synced groups from local database
            val groups = groupRepository.getAllGroups().first()
            Log.d("AuthViewModel", "Proactively synced ${groups.size} groups. Syncing members & expenses...")
            
            // 4. Sync members and expenses for each group
            groups.forEach { group ->
                groupRepository.syncGroupMembersFromSupabase(group.id)
                expenseRepository.syncExpensesFromSupabase(group.id, force = true)
            }
            Log.d("AuthViewModel", "Proactive sync completed successfully!")
        } catch (e: Exception) {
            Log.e("AuthViewModel", "Proactive sync failed after login", e)
        }
    }

    private suspend fun handleTestLogin(email: String) {
        val userId = if (email == "test@test.com") "test-user-1" else "test-user-2"
        
        // Ensure both test users exist in local DB for easy testing
        userDao.insertUser(UserEntity(id = "test-user-1", email = "test@test.com", name = "Test User 1"))
        userDao.insertUser(UserEntity(id = "test-user-2", email = "test2@test.com", name = "Test User 2"))
        
        // Track session locally
        sessionManager.saveSession(userId)
        
        kotlinx.coroutines.delay(500)
        _events.emit(AuthEvent.NavigateToHome)
        _uiState.value = AuthUiState(isLoading = false)
    }
}
