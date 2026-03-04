package com.example.expenseapp.domain.model

data class Group(
    val id: String,
    val name: String,
    val createdAt: Long // Store as Unix timestamp
)
