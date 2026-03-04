package com.example.expenseapp.domain.model

data class User(
    val id: String, // Maps to Supabase Auth UUID
    val name: String,
    val email: String,
    val avatarUrl: String? = null
)
