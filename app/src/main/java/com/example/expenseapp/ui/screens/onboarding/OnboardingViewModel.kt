package com.example.expenseapp.ui.screens.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expenseapp.domain.model.User
import com.example.expenseapp.domain.repository.UserRepository
import com.example.expenseapp.domain.repository.currency.CurrencyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class OnboardingEvent {
    object NavigateToHome : OnboardingEvent()
    data class ShowError(val message: String) : OnboardingEvent()
}

data class OnboardingUiState(
    val name: String = "",
    val selectedCurrency: String = "EUR",
    val currencies: List<String> = emptyList(),
    val avatarUrl: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val currencyRepository: CurrencyRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val userId: String? = savedStateHandle["userId"]

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<OnboardingEvent>()
    val events = _events.asSharedFlow()

    init {
        loadCurrencies()
        loadInitialUser()
    }

    private fun loadCurrencies() {
        val currencies = currencyRepository.getSupportedCurrencies()
        _uiState.update { it.copy(currencies = currencies) }
    }

    private fun loadInitialUser() {
        viewModelScope.launch {
            val user = userRepository.syncUserFromSupabase(userId)
            if (user != null) {
                _uiState.update { it.copy(name = user.name, avatarUrl = user.avatarUrl) }
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun onCurrencyChange(currency: String) {
        _uiState.update { it.copy(selectedCurrency = currency) }
    }

    fun updateAvatar(imageBytes: ByteArray) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val url = userRepository.uploadAvatar(imageBytes)
                _uiState.update { it.copy(avatarUrl = url, isSaving = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(OnboardingEvent.ShowError(e.message ?: "Failed to upload avatar"))
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState.name.isBlank()) return@launch

            _uiState.update { it.copy(isSaving = true) }
            try {
                // Try to get the user, potentially syncing if they disappeared from local DB
                var currentUser = userRepository.syncUserFromSupabase(userId)
                
                // If still null, try one last time with whatever session we have
                if (currentUser == null) {
                    currentUser = userRepository.syncUserFromSupabase(null)
                }

                if (currentUser == null) {
                    throw Exception("User profile not found. Please try logging in again.")
                }

                val updatedUser = currentUser.copy(
                    name = currentState.name,
                    mainCurrency = currentState.selectedCurrency,
                    avatarUrl = currentState.avatarUrl
                )
                userRepository.updateProfile(updatedUser)
                _events.emit(OnboardingEvent.NavigateToHome)
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(OnboardingEvent.ShowError(e.message ?: "Failed to save profile"))
            }
        }
    }
}
