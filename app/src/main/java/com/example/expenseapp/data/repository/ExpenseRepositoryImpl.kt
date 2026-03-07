package com.example.expenseapp.data.repository

import com.example.expenseapp.data.local.dao.ExpenseDao
import com.example.expenseapp.data.local.dao.SplitDao
import com.example.expenseapp.data.repository.mapper.toDomain
import com.example.expenseapp.data.repository.mapper.toEntity
import com.example.expenseapp.domain.model.Expense
import com.example.expenseapp.domain.model.Split
import com.example.expenseapp.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import android.util.Log
import com.example.expenseapp.data.local.entity.ExpenseEntity
import com.example.expenseapp.data.local.entity.SplitEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val splitDao: SplitDao,
    private val supabaseClient: SupabaseClient,
    private val externalScope: CoroutineScope
) : ExpenseRepository {

    @Serializable
    private data class RemoteExpense(
        val id: String,
        val group_id: String,
        val name: String,
        val amount: Double,
        val currency: String,
        val date: String, // TIMESTAMPTZ as ISO String
        val paid_by: String,
        val category_id: String?,
        val is_archived: Boolean = false
    )

    @Serializable
    private data class RemoteSplit(
        val id: String,
        val expense_id: String,
        val user_id: String,
        val amount: Double
    )

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
        return expenseDao.getExpensesByGroup(groupId)
            .onStart {
                // Background sync
                externalScope.launch {
                    syncExpensesFromSupabase(groupId)
                }
            }
            .flatMapLatest { entities ->
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
        // 1. Save locally
        expenseDao.insertExpense(expense.toEntity())
        splitDao.deleteSplitsForExpense(expense.id)
        splitDao.insertSplits(expense.splits.map { it.toEntity() })

        // 2. Push to Supabase
        externalScope.launch {
            try {
                // Push Expense
                supabaseClient.postgrest["expenses"].upsert(
                    RemoteExpense(
                        id = expense.id,
                        group_id = expense.groupId,
                        name = expense.description,
                        amount = expense.amount,
                        currency = expense.currency,
                        date = java.time.Instant.ofEpochMilli(expense.date).toString(),
                        paid_by = expense.paidById,
                        category_id = expense.categoryId,
                        is_archived = expense.isArchived
                    )
                )

                // Push Splits (Delete old, Insert new)
                // Note: Better to use a batch UPSERT if possible, but for simplicity we do DELETE then UPSERT
                supabaseClient.postgrest["splits"].delete {
                    filter { eq("expense_id", expense.id) }
                }
                
                if (expense.splits.isNotEmpty()) {
                    supabaseClient.postgrest["splits"].insert(
                        expense.splits.map { split ->
                            RemoteSplit(
                                id = split.id,
                                expense_id = split.expenseId,
                                user_id = split.owedById,
                                amount = split.amountOwed
                            )
                        }
                    )
                }
                Log.d("ExpenseRepository", "Successfully synced expense ${expense.id} to Supabase")
            } catch (e: Exception) {
                Log.e("ExpenseRepository", "Failed to sync expense ${expense.id} to Supabase", e)
            }
        }
    }

    override suspend fun deleteExpense(expense: Expense) {
        // 1. Delete locally
        splitDao.deleteSplitsForExpense(expense.id)
        expenseDao.deleteExpense(expense.toEntity())

        // 2. Delete from Supabase
        externalScope.launch {
            try {
                // Explicitly delete splits first (in case FK cascade is not set)
                supabaseClient.postgrest["splits"].delete {
                    filter { eq("expense_id", expense.id) }
                }
                
                // Then delete the expense
                supabaseClient.postgrest["expenses"].delete {
                    filter { eq("id", expense.id) }
                }
                Log.d("ExpenseRepository", "Deleted expense ${expense.id} and its splits from Supabase")
            } catch (e: Exception) {
                Log.e("ExpenseRepository", "Failed to delete expense from Supabase", e)
            }
        }
    }

    override suspend fun getSplitsForExpense(expenseId: String): Flow<List<Split>> {
        return splitDao.getSplitsByExpense(expenseId).map { entities: List<com.example.expenseapp.data.local.entity.SplitEntity> ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun archiveExpenses(groupId: String) {
        expenseDao.archiveExpensesForGroup(groupId)
        // TODO: Push archive status to Supabase if needed
    }

    override suspend fun syncExpensesFromSupabase(groupId: String) {
        try {
            Log.d("ExpenseRepository", "Triggering expense sync for group $groupId")
            val remoteExpenses = supabaseClient.postgrest["expenses"]
                .select(Columns.list("id", "group_id", "name", "amount", "currency", "date", "paid_by", "category_id", "is_archived")) {
                    filter { eq("group_id", groupId) }
                }.decodeList<RemoteExpense>()

            Log.d("ExpenseRepository", "Found ${remoteExpenses.size} remote expenses")

            remoteExpenses.forEach { remote ->
                val dateLong = try {
                    java.time.Instant.parse(remote.date).toEpochMilli()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }

                val entity = ExpenseEntity(
                    id = remote.id,
                    group_id = remote.group_id,
                    paid_by_id = remote.paid_by,
                    description = remote.name,
                    amount = remote.amount,
                    currency = remote.currency,
                    date = dateLong,
                    category_id = remote.category_id ?: "general",
                    is_archived = remote.is_archived
                )
                expenseDao.insertExpense(entity)

                // Sync splits for each expense
                syncSplitsForExpense(remote.id)
            }
        } catch (e: Exception) {
            Log.e("ExpenseRepository", "Expense sync failed", e)
        }
    }

    private suspend fun syncSplitsForExpense(expenseId: String) {
        try {
            val remoteSplits = supabaseClient.postgrest["splits"]
                .select(Columns.list("id", "expense_id", "user_id", "amount")) {
                    filter { eq("expense_id", expenseId) }
                }.decodeList<RemoteSplit>()

            splitDao.deleteSplitsForExpense(expenseId)
            if (remoteSplits.isNotEmpty()) {
                splitDao.insertSplits(remoteSplits.map { remote ->
                    SplitEntity(
                        id = remote.id,
                        expense_id = remote.expense_id,
                        owed_by_id = remote.user_id,
                        amount_owed = remote.amount
                    )
                })
            }
        } catch (e: Exception) {
            Log.e("ExpenseRepository", "Splits sync failed for $expenseId", e)
        }
    }
}
