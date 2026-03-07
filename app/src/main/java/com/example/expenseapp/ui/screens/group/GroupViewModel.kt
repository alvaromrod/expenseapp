package com.example.expenseapp.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.example.expenseapp.ui.navigation.Screen
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

    fun createGroup(name: String, description: String? = null, mainCurrency: String = "EUR") {
        viewModelScope.launch {
            val group = Group(
                id = UUID.randomUUID().toString(),
                name = name,
                description = description,
                mainCurrency = mainCurrency,
                createdAt = System.currentTimeMillis()
            )
            groupRepository.createGroup(group)
            
            // Automatically add the creator (current user) to the group
            // Ensure they are synced first so they exist in the local DB for the JOIN query
            // and their profile is pushed to the remote 'users' table if it was missing
            val user = userRepository.syncUserFromSupabase(null)
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

    fun getMembersForGroup(groupId: String): kotlinx.coroutines.flow.Flow<List<User>> {
        viewModelScope.launch {
            groupRepository.syncGroupMembersFromSupabase(groupId)
        }
        return groupRepository.getMembersForGroup(groupId)
    }

    fun deleteGroup(id: String) {
        viewModelScope.launch {
            groupRepository.deleteGroup(id)
        }
    }

    fun joinGroupByLink(link: String, navController: NavController) {
        // Parse groupId from link like expenseapp://join?groupId=... or just the ID
        val groupId = if (link.startsWith("expenseapp://") || link.contains("groupId=")) {
            link.substringAfter("groupId=").substringBefore("&").trim()
        } else {
            link.trim()
        }

        if (groupId.isNotBlank()) {
            navController.navigate(Screen.JoinGroup.createRoute(groupId))
        }
    }
}
