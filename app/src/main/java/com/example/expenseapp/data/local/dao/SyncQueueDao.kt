package com.example.expenseapp.data.local.dao

import androidx.room.*
import com.example.expenseapp.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(operation: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM sync_queue WHERE entity_type = :entityType AND entity_id = :entityId")
    suspend fun deleteByEntity(entityType: String, entityId: String)

    @Query("SELECT * FROM sync_queue ORDER BY created_at ASC")
    suspend fun getAllPending(): List<SyncQueueEntity>

    @Query("SELECT entity_id FROM sync_queue WHERE entity_type = :entityType AND action = :action")
    suspend fun getPendingEntityIds(entityType: String, action: String): List<String>

    @Query("SELECT entity_id FROM sync_queue WHERE entity_type = 'EXPENSE' AND action = 'UPSERT'")
    fun getPendingExpenseUpsertIdsFlow(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun getPendingCountFlow(): Flow<Int>

    @Query("UPDATE sync_queue SET retry_count = retry_count + 1, last_error = :error WHERE id = :id")
    suspend fun recordFailure(id: String, error: String)
}
