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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.first
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Singleton
class GroupRepositoryImpl @Inject constructor(
    private val groupDao: GroupDao,
    private val groupMemberDao: GroupMemberDao,
    private val userRepository: com.example.expenseapp.domain.repository.UserRepository,
    private val supabaseClient: SupabaseClient,
    private val externalScope: CoroutineScope
) : GroupRepository {

    override fun getAllGroups(): Flow<List<Group>> {
        return groupDao.getAllGroups().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getMembersForGroup(groupId: String): Flow<List<User>> {
        return groupMemberDao.getMembersForGroup(groupId)
            .onStart {
                // Launch sync in the background so it doesn't block the initial Flow emission
                externalScope.launch {
                    syncGroupMembersFromSupabase(groupId)
                }
            }
            .map { entities ->
                entities.map { it.toDomain() }
            }
    }

    override fun getGroupFlow(id: String): Flow<Group?> {
        return groupDao.getGroupByIdFlow(id).map { it?.toDomain() }
    }

    @Serializable
    private data class RemoteMember(
        val group_id: String,
        val user_id: String,
        val joined_at: String? = null
    )

    override suspend fun addMemberToGroup(groupId: String, userId: String) {
        // 1. Save locally
        groupMemberDao.insertMember(GroupMemberEntity(groupId, userId))
        
        // 2. Sync to Supabase
        try {
            Log.d("GroupRepository", "Pushing membership for user $userId to group $groupId")
            // Use explicit insert to avoid upsert primary key conflicts if any
            supabaseClient.postgrest["group_members"].insert(
                RemoteMember(groupId, userId)
            )
            Log.d("GroupRepository", "Membership push successful")
        } catch (e: Exception) {
            Log.e("GroupRepository", "Failed to sync membership for user $userId to Supabase (might already exist)", e)
        }
    }

    @Serializable
    private data class RemoteGroup(
        val id: String,
        val name: String,
        val description: String? = null,
        val main_currency: String = "EUR",
        val created_at: String? = null // Change to String to handle Supabase timestamptz
    )

    override suspend fun getGroupById(id: String): Group? {
        val local = groupDao.getGroupById(id)?.toDomain()
        if (local != null) {
            Log.d("GroupRepository", "Found group $id locally")
            return local
        }

        // Fallback to remote fetch for Joining flow
        Log.d("GroupRepository", "Attempting remote fetch for group $id")
        val remoteGroup = try {
            val tables = listOf("groups", "group", "expense_groups")
            var found: RemoteGroup? = null
            for (table in tables) {
                try {
                    found = supabaseClient.postgrest[table]
                        .select() {
                            filter { eq("id", id) }
                        }.decodeSingleOrNull<RemoteGroup>()
                    if (found != null) {
                        Log.d("GroupRepository", "Found group $id in table '$table'")
                        break
                    }
                } catch (e: Exception) {
                    Log.w("GroupRepository", "Table '$table' not found or inaccessible")
                }
            }
            found
        } catch (e: Exception) {
            Log.e("GroupRepository", "Failed to fetch remote group $id", e)
            null
        }

        return remoteGroup?.let {
            val group = Group(
                id = it.id, 
                name = it.name, 
                description = it.description,
                mainCurrency = it.main_currency,
                createdAt = parseTimestamp(it.created_at)
            )
            // Save to local DB so subsequent membership/expense operations don't fail FK constraints
            groupDao.insertGroup(group.toEntity())
            group
        }
    }

    private fun parseTimestamp(isoString: String?): Long {
        if (isoString == null) return System.currentTimeMillis()
        return try {
            // Very simple fallback parser for ISO strings if needed, 
            // but for now current time is a safe default for a joining user
            System.currentTimeMillis() 
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    override suspend fun createGroup(group: Group) {
        // 1. Save locally
        Log.d("GroupRepository", "Creating group locally: ${group.id}")
        groupDao.insertGroup(group.toEntity())
        
        // 2. Explicitly push to remote
        val tables = listOf("groups", "group", "expense_groups")
        val data = mapOf(
            "id" to group.id,
            "name" to group.name,
            "description" to group.description,
            "main_currency" to group.mainCurrency
        )
        
        for (table in tables) {
            try {
                Log.d("GroupRepository", "Pushing to remote table '$table': ${group.id}")
                supabaseClient.postgrest[table].insert(data)
                Log.d("GroupRepository", "Remote push successful to '$table' for ${group.id}")
                // Also immediately add creator to remote AND local memberships
                try {
                    val authUser = supabaseClient.auth.currentUserOrNull()
                    if (authUser != null) {
                        // Remote
                        supabaseClient.postgrest["group_members"].upsert(RemoteMember(group.id, authUser.id))
                        // Local
                        groupMemberDao.insertMember(GroupMemberEntity(group.id, authUser.id))
                        Log.d("GroupRepository", "Creator added to memberships locally and remotely")
                    }
                } catch (e: Exception) {
                    Log.e("GroupRepository", "Failed to add creator membership", e)
                }
                return
            } catch (e: Exception) {
                Log.w("GroupRepository", "Failed to push to table '$table', trying next...")
            }
        }
        Log.e("GroupRepository", "All table push attempts failed for ${group.id}")
    }

    override suspend fun updateGroup(group: Group) {
        groupDao.insertGroup(group.toEntity()) // insertGroup uses REPLACE
    }

    override suspend fun deleteGroup(id: String) {
        // 1. Delete locally
        val group = groupDao.getGroupById(id)
        if (group != null) {
            groupMemberDao.deleteMembersByGroupId(id)
            groupDao.deleteGroup(group)
        }

        // 2. Push to Supabase
        externalScope.launch {
            try {
                // In Supabase, delete from the three possible tables we use (groups, group, expense_groups)
                val tables = listOf("groups", "group", "expense_groups")
                
                // First delete members (if FK cascade not set)
                supabaseClient.postgrest["group_members"].delete {
                    filter { eq("group_id", id) }
                }

                for (table in tables) {
                    try {
                        supabaseClient.postgrest[table].delete {
                            filter { eq("id", id) }
                        }
                    } catch (e: Exception) {
                        Log.w("GroupRepository", "Failed to delete from table '$table' (might not exist)")
                    }
                }
                Log.d("GroupRepository", "Successfully deleted group $id from remote")
            } catch (e: Exception) {
                Log.e("GroupRepository", "Failed to sync group deletion to Supabase", e)
            }
        }
    }

    override suspend fun removeMemberFromGroup(groupId: String, userId: String) {
        groupMemberDao.removeMember(groupId, userId)
    }

    override suspend fun syncGroupMembersFromSupabase(groupId: String) {
        try {
            Log.d("GroupRepository", "Triggering member sync for group $groupId")
            val remoteMembers = supabaseClient.postgrest["group_members"]
                .select(Columns.list("group_id", "user_id", "joined_at")) {
                    filter { eq("group_id", groupId) }
                }.decodeList<RemoteMember>()
            
            Log.d("GroupRepository", "Found ${remoteMembers.size} members in Supabase for group $groupId")
            
            remoteMembers.forEach { remote ->
                Log.d("GroupRepository", "Processing member: ${remote.user_id}")
                // 1. Proactively sync the user profile FIRST (ensures they are in 'local.users' table)
                val user = userRepository.syncUserFromSupabase(remote.user_id)
                Log.d("GroupRepository", "Synced profile for ${remote.user_id}: ${user?.name}")
                
                // 2. Ensure membership exists locally AFTER the user is persisted
                groupMemberDao.insertMember(GroupMemberEntity(remote.group_id, remote.user_id))
            }
        } catch (e: Exception) {
            Log.e("GroupRepository", "Failed to sync members for group $groupId from Supabase", e)
        }
    }
}
