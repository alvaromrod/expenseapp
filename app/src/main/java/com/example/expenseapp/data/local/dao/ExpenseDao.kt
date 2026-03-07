package com.example.expenseapp.data.local.dao

import androidx.room.*
import com.example.expenseapp.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: String): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE group_id = :groupId ORDER BY date DESC")
    fun getExpensesByGroup(groupId: String): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("UPDATE expenses SET is_archived = 1 WHERE group_id = :groupId AND is_archived = 0")
    suspend fun archiveExpensesForGroup(groupId: String)
}
