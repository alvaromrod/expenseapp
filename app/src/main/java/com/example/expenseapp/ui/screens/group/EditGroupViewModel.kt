package com.example.expenseapp.ui.screens.group

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expenseapp.domain.model.Group
import com.example.expenseapp.domain.model.User
import com.example.expenseapp.domain.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditGroupUiState(
    val group: Group? = null,
    val members: List<User> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

sealed class EditGroupEvent {
    object NavigateBack : EditGroupEvent()
    data class ShowError(val message: String) : EditGroupEvent()
}

@HiltViewModel
class EditGroupViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle["groupId"])

    private val _uiState = MutableStateFlow(EditGroupUiState())
    val uiState: StateFlow<EditGroupUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<EditGroupEvent>()
    val events = _events.asSharedFlow()

    init {
        loadGroupData()
    }

    private fun loadGroupData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val group = groupRepository.getGroupById(groupId)
                if (group != null) {
                    _uiState.update { it.copy(group = group) }
                    groupRepository.syncGroupMembersFromSupabase(groupId)
                    groupRepository.getMembersForGroup(groupId).collect { members ->
                        _uiState.update { it.copy(members = members, isLoading = false) }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Group not found") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun updateGroup(name: String, description: String, currency: String) {
        val currentGroup = _uiState.value.group ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                groupRepository.updateGroup(
                    currentGroup.copy(
                        name = name,
                        description = description.ifBlank { null },
                        mainCurrency = currency
                    )
                )
                _events.emit(EditGroupEvent.NavigateBack)
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(EditGroupEvent.ShowError(e.message ?: "Failed to update group"))
            }
        }
    }

    fun removeMember(userId: String) {
        viewModelScope.launch {
            try {
                groupRepository.removeMemberFromGroup(groupId, userId)
            } catch (e: Exception) {
                _events.emit(EditGroupEvent.ShowError(e.message ?: "Failed to remove member"))
            }
        }
    }
}
