package com.example.expenseapp.data.local.dao

import androidx.room.*
import com.example.expenseapp.data.local.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun getGroupById(id: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE id = :id")
    fun getGroupByIdFlow(id: String): Flow<GroupEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Delete
    suspend fun deleteGroup(group: GroupEntity)

    @Query("DELETE FROM groups WHERE id NOT IN (:validIds)")
    suspend fun deleteGroupsNotIn(validIds: List<String>)
}
