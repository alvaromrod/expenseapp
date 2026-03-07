package com.example.expenseapp.data.local.dao

import androidx.room.*
import com.example.expenseapp.data.local.entity.GroupMemberEntity
import com.example.expenseapp.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupMemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: GroupMemberEntity)

    @Delete
    suspend fun deleteMember(member: GroupMemberEntity)

    @Query("""
        SELECT 
            group_members.user_id as id, 
            COALESCE(users.name, '') as name, 
            COALESCE(users.email, '') as email, 
            users.avatar_url, 
            COALESCE(users.main_currency, 'EUR') as main_currency 
        FROM group_members 
        LEFT JOIN users ON group_members.user_id = users.id 
        WHERE group_members.group_id = :groupId
    """)
    fun getMembersForGroup(groupId: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM group_members WHERE group_id = :groupId")
    suspend fun getGroupMemberEntities(groupId: String): List<GroupMemberEntity>

    @Query("DELETE FROM group_members WHERE group_id = :groupId AND user_id = :userId")
    suspend fun removeMember(groupId: String, userId: String)

    @Query("DELETE FROM group_members WHERE group_id = :groupId")
    suspend fun deleteMembersByGroupId(groupId: String)
}
