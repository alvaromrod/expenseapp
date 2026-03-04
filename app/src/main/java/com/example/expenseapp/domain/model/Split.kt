package com.example.expenseapp.domain.model

data class Split(
    val id: String,
    val expenseId: String,
    val owedById: String, // The user who owes money
    val amountOwed: Double
)
