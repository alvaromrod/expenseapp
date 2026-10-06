package com.example.expenseapp.core.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.expenseapp.data.local.dao.ExpenseDao
import com.example.expenseapp.data.local.dao.SplitDao
import com.example.expenseapp.data.local.dao.SyncQueueDao
import com.example.expenseapp.data.local.entity.SyncQueueEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val expenseDao: ExpenseDao,
    private val splitDao: SplitDao,
    private val supabaseClient: SupabaseClient,
    @ApplicationContext private val context: Context
) {
    private val syncMutex = Mutex()

    @Serializable
    private data class RemoteExpense(
        val id: String,
        val group_id: String,
        val name: String,
        val amount: Double,
        val currency: String,
        val date: String,
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

    companion object {
        private const val TAG = "SyncManager"
        private const val SYNC_WORK_NAME = "expense_offline_sync_work"
    }

    /**
     * Attempts to push all pending offline operations to Supabase.
     * Returns true if all operations completed successfully (or queue is empty).
     */
    suspend fun syncPendingItems(): Boolean = syncMutex.withLock {
        val pendingOps = syncQueueDao.getAllPending()
        if (pendingOps.isEmpty()) {
            return true
        }

        Log.d(TAG, "Starting sync for ${pendingOps.size} pending offline operations...")
        var allSucceeded = true

        for (op in pendingOps) {
            try {
                when (op.entity_type) {
                    "EXPENSE" -> {
                        when (op.action) {
                            "UPSERT" -> syncExpenseUpsert(op.entity_id)
                            "DELETE" -> syncExpenseDelete(op.entity_id)
                        }
                    }
                }
                // Operation succeeded: delete from queue
                syncQueueDao.deleteById(op.id)
                Log.d(TAG, "Successfully pushed pending ${op.action} for ${op.entity_type}:${op.entity_id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push operation ${op.id} for ${op.entity_type}:${op.entity_id}", e)
                syncQueueDao.recordFailure(op.id, e.message ?: "Unknown network error")
                allSucceeded = false
            }
        }

        if (!allSucceeded) {
            // Schedule background retry with WorkManager
            scheduleBackgroundSync()
        }

        return allSucceeded
    }

    private suspend fun syncExpenseUpsert(expenseId: String) {
        val entityWithSplits = expenseDao.getExpenseWithSplitsById(expenseId)
        if (entityWithSplits == null) {
            // Expense no longer exists locally (e.g. deleted while offline)
            Log.d(TAG, "Expense $expenseId was deleted locally before sync; skipping upsert.")
            return
        }

        val expense = entityWithSplits.expense
        val splits = entityWithSplits.splits

        // 1. Push Expense to Supabase
        supabaseClient.postgrest["expenses"].upsert(
            RemoteExpense(
                id = expense.id,
                group_id = expense.group_id,
                name = expense.description,
                amount = expense.amount,
                currency = expense.currency,
                date = java.time.Instant.ofEpochMilli(expense.date).toString(),
                paid_by = expense.paid_by_id,
                category_id = expense.category_id,
                is_archived = expense.is_archived
            )
        )

        // 2. Push Splits to Supabase
        supabaseClient.postgrest["splits"].delete {
            filter { eq("expense_id", expense.id) }
        }

        if (splits.isNotEmpty()) {
            supabaseClient.postgrest["splits"].insert(
                splits.map { split ->
                    RemoteSplit(
                        id = split.id,
                        expense_id = split.expense_id,
                        user_id = split.owed_by_id,
                        amount = split.amount_owed
                    )
                }
            )
        }
    }

    private suspend fun syncExpenseDelete(expenseId: String) {
        try {
            supabaseClient.postgrest["splits"].delete {
                filter { eq("expense_id", expenseId) }
            }
        } catch (e: Exception) {
            // Ignore split delete error if already removed
        }
        supabaseClient.postgrest["expenses"].delete {
            filter { eq("id", expenseId) }
        }
    }

    /**
     * Schedules a WorkManager job that automatically triggers when network is available.
     */
    fun scheduleBackgroundSync() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncWork = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                SYNC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                syncWork
            )
            Log.d(TAG, "Enqueued background SyncWorker with CONNECTED network constraint")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule background sync work", e)
        }
    }
}
