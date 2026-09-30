package com.example.expenseapp.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class ExpenseWithSplitsEntity(
    @Embedded val expense: ExpenseEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "expense_id"
    )
    val splits: List<SplitEntity>
)
