package com.example.expenseapp.data.repository

import com.example.expenseapp.data.local.dao.GroupDao
import com.example.expenseapp.data.local.dao.GroupMemberDao
import com.example.expenseapp.data.local.entity.GroupMemberEntity
import com.example.expenseapp.data.repository.mapper.toDomain
import com.example.expenseapp.data.repository.mapper.toEntity
import com.example.expenseapp.domain.model.Group
import com.example.expenseapp.domain.model.User
import com.example.expenseapp.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepositoryImpl @Inject constructor(
    private val groupDao: GroupDao,
    private val groupMemberDao: GroupMemberDao
) : GroupRepository {

    override fun getAllGroups(): Flow<List<Group>> {
        return groupDao.getAllGroups().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getMembersForGroup(groupId: String): Flow<List<User>> {
        return groupMemberDao.getMembersForGroup(groupId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun addMemberToGroup(groupId: String, userId: String) {
        groupMemberDao.insertMember(GroupMemberEntity(groupId, userId))
    }

    override suspend fun getGroupById(id: String): Group? {
        return groupDao.getGroupById(id)?.toDomain()
    }

    override suspend fun createGroup(group: Group) {
        groupDao.insertGroup(group.toEntity())
    }

    override suspend fun deleteGroup(id: String) {
        val group = groupDao.getGroupById(id)
        if (group != null) {
            groupDao.deleteGroup(group)
        }
    }
}
