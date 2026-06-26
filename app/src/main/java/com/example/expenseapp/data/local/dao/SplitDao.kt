package com.example.expenseapp.data.local.dao

import androidx.room.*
import com.example.expenseapp.data.local.entity.SplitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SplitDao {
    @Query("SELECT * FROM splits WHERE expense_id = :expenseId")
    fun getSplitsByExpense(expenseId: String): Flow<List<SplitEntity>>

    @Query("SELECT DISTINCT expense_id FROM splits WHERE owed_by_id = :userId AND amount_owed > 0")
    fun getExpenseIdsForUser(userId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSplits(splits: List<SplitEntity>)

    @Query("DELETE FROM splits WHERE expense_id = :expenseId")
    suspend fun deleteSplitsForExpense(expenseId: String)

    @Query("DELETE FROM splits WHERE expense_id NOT IN (SELECT id FROM expenses)")
    suspend fun deleteOrphanedSplits()
}

