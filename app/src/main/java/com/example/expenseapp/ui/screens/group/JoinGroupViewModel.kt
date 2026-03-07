package com.example.expenseapp.ui.screens.group

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expenseapp.domain.model.Group
import com.example.expenseapp.domain.repository.GroupRepository
import com.example.expenseapp.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class JoinGroupEvent {
    object NavigateToHome : JoinGroupEvent()
    data class ShowError(val message: String) : JoinGroupEvent()
}

data class JoinGroupUiState(
    val group: Group? = null,
    val isLoading: Boolean = false,
    val isJoining: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class JoinGroupViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle["groupId"])

    private val _uiState = MutableStateFlow(JoinGroupUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<JoinGroupEvent>()
    val events = _events.asSharedFlow()

    init {
        loadGroupDetails()
    }

    private fun loadGroupDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val group = groupRepository.getGroupById(groupId)
                if (group != null) {
                    _uiState.update { it.copy(group = group, isLoading = false) }
                } else {
                    // Don't set hard error yet, allow "Blind Join"
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                android.util.Log.e("JoinGroupViewModel", "Error loading group $groupId", e)
                _uiState.update { it.copy(isLoading = false) } // Silent failure to allow blind join
            }
        }
    }

    fun joinGroup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isJoining = true) }
            try {
                // 1. Guaranteed profile sync (ensures we are in 'public.users' and 'local.users')
                val currentUser = userRepository.syncUserFromSupabase(null)
                if (currentUser != null) {
                    // 2. Add to group memberships
                    groupRepository.addMemberToGroup(groupId, currentUser.id)
                    // 3. Immediately pull ALL other group members (like the creator)
                    groupRepository.syncGroupMembersFromSupabase(groupId)
                    _events.emit(JoinGroupEvent.NavigateToHome)
                } else {
                    _events.emit(JoinGroupEvent.ShowError("You must be logged in to join a group"))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isJoining = false) }
                _events.emit(JoinGroupEvent.ShowError(e.message ?: "Failed to join group"))
            }
        }
    }
}
