package com.example.expenseapp.domain.repository

import com.example.expenseapp.domain.model.Expense
import com.example.expenseapp.domain.model.Split
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    fun getAllExpenses(): Flow<List<Expense>>
    fun getExpensesByGroup(groupId: String): Flow<List<Expense>>
    suspend fun getExpenseById(id: String): Expense?
    suspend fun upsertExpense(expense: Expense)
    suspend fun deleteExpense(expense: Expense)
    suspend fun getSplitsForExpense(expenseId: String): Flow<List<Split>>
    suspend fun archiveExpenses(groupId: String)
    suspend fun syncExpensesFromSupabase(groupId: String)
}
