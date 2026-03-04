package com.example.expenseapp.domain.repository

import com.example.expenseapp.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun getAllCategories(): Flow<List<Category>>
    suspend fun getCategoryById(id: String): Category?
    suspend fun upsertCategory(category: Category)
}
