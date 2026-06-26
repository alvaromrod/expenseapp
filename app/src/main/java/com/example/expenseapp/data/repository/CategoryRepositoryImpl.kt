package com.example.expenseapp.data.repository

import com.example.expenseapp.data.local.dao.CategoryDao
import com.example.expenseapp.data.repository.mapper.toDomain
import com.example.expenseapp.data.repository.mapper.toEntity
import com.example.expenseapp.domain.model.Category
import com.example.expenseapp.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import android.util.Log

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
    private val supabaseClient: SupabaseClient,
    private val externalScope: CoroutineScope
) : CategoryRepository {

    @Serializable
    private data class RemoteCategory(
        val id: String,
        val name: String,
        val icon_name: String,
        val color_hex: String,
        val group_id: String? = null
    )

    override fun getAllCategories(): Flow<List<Category>> {
        return categoryDao.getAllCategories().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getCategoryById(id: String): Category? {
        return categoryDao.getCategoryById(id)?.toDomain()
    }

    override suspend fun upsertCategory(category: Category) {
        // 1. Save locally
        categoryDao.insertCategory(category.toEntity())

        // 2. Push to Supabase if it's a custom group category
        if (category.groupId != null) {
            externalScope.launch {
                try {
                    supabaseClient.postgrest["categories"].upsert(
                        RemoteCategory(
                            id = category.id,
                            name = category.name,
                            icon_name = category.iconName,
                            color_hex = category.colorHex,
                            group_id = category.groupId
                        )
                    )
                    Log.d("CategoryRepository", "Successfully synced category ${category.id} to Supabase")
                } catch (e: Exception) {
                    Log.e("CategoryRepository", "Failed to sync category ${category.id} to Supabase", e)
                }
            }
        }
    }

    override suspend fun deleteCategory(id: String) {
        val category = categoryDao.getCategoryById(id)
        categoryDao.deleteCategoryById(id)

        if (category?.group_id != null) {
            externalScope.launch {
                try {
                    supabaseClient.postgrest["categories"].delete {
                        filter { eq("id", id) }
                    }
                    Log.d("CategoryRepository", "Deleted category $id from Supabase")
                } catch (e: Exception) {
                    Log.e("CategoryRepository", "Failed to delete category $id from Supabase", e)
                }
            }
        }
    }

    private val syncThrottler = mutableMapOf<String, Long>()

    override suspend fun syncCategoriesForGroup(groupId: String) {
        val now = System.currentTimeMillis()
        val lastSync = syncThrottler[groupId] ?: 0L
        if (now - lastSync < 60 * 1000) { // 1 min throttle
            Log.d("CategoryRepository", "Throttling category sync for group $groupId")
            return
        }
        syncThrottler[groupId] = now

        try {
            Log.d("CategoryRepository", "Triggering category sync for group $groupId")
            val remoteCategories = supabaseClient.postgrest["categories"]
                .select(Columns.list("id", "name", "icon_name", "color_hex", "group_id")) {
                    filter { eq("group_id", groupId) }
                }.decodeList<RemoteCategory>()

            Log.d("CategoryRepository", "Found ${remoteCategories.size} remote categories for group $groupId")

            remoteCategories.forEach { remote ->
                val entity = com.example.expenseapp.data.local.entity.CategoryEntity(
                    id = remote.id,
                    name = remote.name,
                    icon_name = remote.icon_name,
                    color_hex = remote.color_hex,
                    group_id = remote.group_id
                )
                categoryDao.insertCategory(entity)
            }
        } catch (e: Exception) {
            Log.e("CategoryRepository", "Category sync failed for group $groupId", e)
        }
    }
}
