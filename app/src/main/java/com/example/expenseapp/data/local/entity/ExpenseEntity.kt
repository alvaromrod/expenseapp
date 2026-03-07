package com.example.expenseapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String, // PowerSync requires textual UUIDs
    val group_id: String,
    val paid_by_id: String,
    val description: String,
    val amount: Double,
    val currency: String,
    val date: Long,
    val category_id: String,
    val is_archived: Boolean = false
)
