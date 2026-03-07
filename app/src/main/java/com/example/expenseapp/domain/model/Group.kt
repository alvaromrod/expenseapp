package com.example.expenseapp.domain.model

data class Group(
    val id: String,
    val name: String,
    val description: String? = null,
    val mainCurrency: String = "EUR",
    val createdAt: Long // Store as Unix timestamp
)
