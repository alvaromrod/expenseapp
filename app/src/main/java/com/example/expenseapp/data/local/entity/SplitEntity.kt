package com.example.expenseapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "splits")
data class SplitEntity(
    @PrimaryKey val id: String,
    val expense_id: String,
    val owed_by_id: String,
    val amount_owed: Double
)
