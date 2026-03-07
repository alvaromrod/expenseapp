package com.example.expenseapp.domain.model

data class Category(
    val id: String,
    val name: String,
    val iconName: String,
    val colorHex: String,
    val groupId: String? = null
)
