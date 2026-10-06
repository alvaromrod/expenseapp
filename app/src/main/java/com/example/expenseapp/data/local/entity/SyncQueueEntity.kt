package com.example.expenseapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["entity_type", "entity_id"]),
        Index(value = ["created_at"])
    ]
)
data class SyncQueueEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entity_type: String, // "EXPENSE", "GROUP", "CATEGORY"
    val entity_id: String,
    val action: String,      // "UPSERT", "DELETE"
    val created_at: Long = System.currentTimeMillis(),
    val retry_count: Int = 0,
    val last_error: String? = null
)
