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
        SELECT users.* FROM users 
        JOIN group_members ON users.id = group_members.user_id 
        WHERE group_members.group_id = :groupId
    """)
    fun getMembersForGroup(groupId: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM group_members WHERE group_id = :groupId")
    suspend fun getGroupMemberEntities(groupId: String): List<GroupMemberEntity>
}
