package com.example.expenseapp.domain.repository

import com.example.expenseapp.domain.model.Group
import com.example.expenseapp.domain.model.User
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    fun getAllGroups(): Flow<List<Group>>
    fun getMembersForGroup(groupId: String): Flow<List<User>>
    fun getGroupFlow(id: String): Flow<Group?>
    suspend fun addMemberToGroup(groupId: String, userId: String)
    suspend fun getGroupById(id: String): Group?
    suspend fun createGroup(group: Group)
    suspend fun updateGroup(group: Group)
    suspend fun deleteGroup(id: String)
    suspend fun removeMemberFromGroup(groupId: String, userId: String)
    suspend fun syncGroupMembersFromSupabase(groupId: String)
}
