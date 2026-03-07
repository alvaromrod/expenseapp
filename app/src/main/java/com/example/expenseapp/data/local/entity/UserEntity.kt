package com.example.expenseapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String, // Maps to Supabase Auth UUID
    val name: String,
    val email: String,
    val avatar_url: String? = null,
    val main_currency: String = "EUR"
)
