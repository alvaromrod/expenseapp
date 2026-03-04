package com.example.expenseapp.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import com.example.expenseapp.core.session.SessionManager
import com.example.expenseapp.data.local.dao.UserDao
import com.example.expenseapp.data.local.entity.UserEntity
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthEvent {
    object NavigateToHome : AuthEvent()
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
    private val userDao: UserDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>()
    val events = _events.asSharedFlow()

    // Expose persistent session for AppNavigation to determine start destination
    val currentUserId = sessionManager.currentUserFlow

    fun signUpWithEmail(email: String, password: String) {
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
                _events.emit(AuthEvent.NavigateToHome)
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
                _events.emit(AuthEvent.NavigateToHome)
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Supabase Sign In Error", e)
                _uiState.value = AuthUiState(error = e.message ?: "Unknown error occurred")
                _events.emit(AuthEvent.ShowError(e.message ?: "Unknown error occurred"))
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
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
