package com.example.expenseapp.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expenseapp.domain.model.Group
import com.example.expenseapp.domain.model.User
import com.example.expenseapp.domain.repository.GroupRepository
import com.example.expenseapp.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class GroupViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    val groups: StateFlow<List<Group>> = groupRepository.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val users: StateFlow<List<User>> = userRepository.getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createGroup(name: String) {
        viewModelScope.launch {
            val group = Group(
                id = UUID.randomUUID().toString(),
                name = name,
                createdAt = System.currentTimeMillis()
            )
            groupRepository.createGroup(group)
            
            // Automatically add the creator (current user) to the group
            val user = userRepository.getCurrentUser().firstOrNull()
            if (user != null) {
                groupRepository.addMemberToGroup(group.id, user.id)
            }
        }
    }

    fun addMemberToGroup(groupId: String, userId: String) {
        viewModelScope.launch {
            groupRepository.addMemberToGroup(groupId, userId)
        }
    }

    fun getMembersForGroup(groupId: String) = groupRepository.getMembersForGroup(groupId)

    fun deleteGroup(id: String) {
        viewModelScope.launch {
            groupRepository.deleteGroup(id)
        }
    }
}
