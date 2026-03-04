package com.example.expenseapp.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.example.expenseapp.domain.model.User
import com.example.expenseapp.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class ProfileEvent {
    object NavigateToAuth : ProfileEvent()
    data class ShowError(val message: String) : ProfileEvent()
}

data class ProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState(isLoading = true))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ProfileEvent>()
    val events = _events.asSharedFlow()

    init {
        Log.d("ProfileViewModel", "Initializing ProfileViewModel")
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            Log.d("ProfileViewModel", "Starting loadProfile observation")
            userRepository.getCurrentUser().collectLatest { user ->
                Log.d("ProfileViewModel", "Profile updated in VM: ${user?.email ?: "null"}")
                _uiState.update { currentState: ProfileUiState -> 
                    currentState.copy(user = user, isLoading = false) 
                }
            }
        }
    }

    fun updateProfile(name: String) {
        val currentUser = _uiState.value.user ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                userRepository.updateProfile(currentUser.copy(name = name))
                _uiState.value = _uiState.value.copy(isSaving = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message ?: "Failed to update profile"
                )
            }
        }
    }

    fun updateAvatar(imageBytes: ByteArray) {
        val currentUser = _uiState.value.user ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                val newAvatarUrl = userRepository.uploadAvatar(imageBytes)
                userRepository.updateProfile(currentUser.copy(avatarUrl = newAvatarUrl))
                _uiState.value = _uiState.value.copy(isSaving = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message ?: "Failed to upload avatar"
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                userRepository.signOut()
                _events.emit(ProfileEvent.NavigateToAuth)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to sign out"
                )
                _events.emit(ProfileEvent.ShowError(e.message ?: "Failed to sign out"))
            }
        }
    }
}
