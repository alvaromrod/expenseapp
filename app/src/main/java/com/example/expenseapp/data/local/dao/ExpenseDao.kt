package com.example.expenseapp.data.local.dao

import androidx.room.*
import com.example.expenseapp.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Transaction
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpensesWithSplits(): Flow<List<com.example.expenseapp.data.local.entity.ExpenseWithSplitsEntity>>

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Transaction
    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseWithSplitsById(id: String): com.example.expenseapp.data.local.entity.ExpenseWithSplitsEntity?

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: String): ExpenseEntity?

    @Transaction
    @Query("SELECT * FROM expenses WHERE group_id = :groupId ORDER BY date DESC")
    fun getExpensesWithSplitsByGroup(groupId: String): Flow<List<com.example.expenseapp.data.local.entity.ExpenseWithSplitsEntity>>

    @Query("SELECT * FROM expenses WHERE group_id = :groupId ORDER BY date DESC")
    fun getExpensesByGroup(groupId: String): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("UPDATE expenses SET is_archived = 1 WHERE group_id = :groupId AND is_archived = 0")
    suspend fun archiveExpensesForGroup(groupId: String)

    @Query("DELETE FROM expenses WHERE group_id = :groupId AND id NOT IN (:validIds)")
    suspend fun deleteExpensesNotIn(groupId: String, validIds: List<String>)

    @Query("DELETE FROM expenses WHERE group_id = :groupId")
    suspend fun deleteExpensesByGroupId(groupId: String)

    @Transaction
    suspend fun syncBatchExpensesAndSplits(
        expenses: List<ExpenseEntity>,
        splits: List<com.example.expenseapp.data.local.entity.SplitEntity>,
        groupId: String,
        remoteExpenseIds: List<String>,
        splitDao: SplitDao
    ) {
        insertExpenses(expenses)
        if (remoteExpenseIds.isNotEmpty()) {
            remoteExpenseIds.chunked(100).forEach { chunk ->
                splitDao.deleteSplitsForExpenseIds(chunk)
            }
        }
        splitDao.insertSplits(splits)
        deleteExpensesNotIn(groupId, remoteExpenseIds)
        splitDao.deleteOrphanedSplits()
    }
}
