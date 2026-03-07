package com.example.expenseapp.domain.model

data class Expense(
    val id: String,
    val groupId: String,
    val paidById: String, // The user who paid the bill
    val description: String,
    val amount: Double, // The total bill amount
    val currency: String,
    val date: Long,
    val categoryId: String,
    val isArchived: Boolean = false,
    val splits: List<Split> = emptyList()
)
