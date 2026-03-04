package com.example.expenseapp.data.repository

import com.example.expenseapp.data.local.dao.ExpenseDao
import com.example.expenseapp.data.local.dao.SplitDao
import com.example.expenseapp.data.repository.mapper.toDomain
import com.example.expenseapp.data.repository.mapper.toEntity
import com.example.expenseapp.domain.model.Expense
import com.example.expenseapp.domain.model.Split
import com.example.expenseapp.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val splitDao: SplitDao
) : ExpenseRepository {

    override fun getAllExpenses(): Flow<List<Expense>> {
        return expenseDao.getAllExpenses().flatMapLatest { entities ->
            val expenseFlows = entities.map { entity ->
                splitDao.getSplitsByExpense(entity.id).map { splitEntities: List<com.example.expenseapp.data.local.entity.SplitEntity> ->
                    entity.toDomain(splitEntities.map { it.toDomain() })
                }
            }
            if (expenseFlows.isEmpty()) flowOf(emptyList())
            else combine(expenseFlows) { it.toList() }
        }
    }

    override fun getExpensesByGroup(groupId: String): Flow<List<Expense>> {
        return expenseDao.getExpensesByGroup(groupId).flatMapLatest { entities ->
            val expenseFlows = entities.map { entity ->
                splitDao.getSplitsByExpense(entity.id).map { splitEntities: List<com.example.expenseapp.data.local.entity.SplitEntity> ->
                    entity.toDomain(splitEntities.map { it.toDomain() })
                }
            }
            if (expenseFlows.isEmpty()) flowOf(emptyList())
            else combine(expenseFlows) { it.toList() }
        }
    }

    override suspend fun getExpenseById(id: String): Expense? {
        return expenseDao.getExpenseById(id)?.toDomain()
    }

    override suspend fun upsertExpense(expense: Expense) {
        expenseDao.insertExpense(expense.toEntity())
        splitDao.deleteSplitsForExpense(expense.id)
        splitDao.insertSplits(expense.splits.map { it.toEntity() })
    }

    override suspend fun deleteExpense(expense: Expense) {
        splitDao.deleteSplitsForExpense(expense.id)
        expenseDao.deleteExpense(expense.toEntity())
    }

    override suspend fun getSplitsForExpense(expenseId: String): Flow<List<Split>> {
        return splitDao.getSplitsByExpense(expenseId).map { entities: List<com.example.expenseapp.data.local.entity.SplitEntity> ->
            entities.map { it.toDomain() }
        }
    }
}
