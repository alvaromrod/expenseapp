package com.example.expenseapp.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.example.expenseapp.data.local.AppDatabase
import com.example.expenseapp.data.local.appSchema
import com.example.expenseapp.data.local.dao.*
import com.example.expenseapp.data.local.entity.CategoryEntity
import com.example.expenseapp.data.remote.powersync.SupabaseCredentialsProvider
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val defaultCategories = listOf(
        CategoryEntity(id = "eating_out", name = "Eating out", icon_name = "restaurant", color_hex = "#FF6B35"),
        CategoryEntity(id = "transport", name = "Transport", icon_name = "directions_car", color_hex = "#4ECDC4"),
        CategoryEntity(id = "rent_housing", name = "Rent & Housing", icon_name = "home", color_hex = "#45B7D1"),
        CategoryEntity(id = "supermarket", name = "Supermarket", icon_name = "shopping_cart", color_hex = "#96CEB4"),
        CategoryEntity(id = "traveling", name = "Traveling", icon_name = "flight", color_hex = "#DDA0DD"),
        CategoryEntity(id = "entertainment", name = "Entertainment", icon_name = "movie", color_hex = "#FFD93D"),
        CategoryEntity(id = "health", name = "Health", icon_name = "local_hospital", color_hex = "#FF6B6B"),
        CategoryEntity(id = "education", name = "Education", icon_name = "school", color_hex = "#A8D8EA"),
        CategoryEntity(id = "shopping", name = "Shopping", icon_name = "shopping_bag", color_hex = "#F38181"),
        CategoryEntity(id = "utilities", name = "Utilities", icon_name = "lightbulb", color_hex = "#6C5CE7"),
        CategoryEntity(id = "other", name = "Other", icon_name = "more_horiz", color_hex = "#95A5A6")
    )

    @Provides
    @Singleton
    fun providePowerSyncDatabase(
        @ApplicationContext context: Context,
        credentialsProvider: SupabaseCredentialsProvider
    ): PowerSyncDatabase {
        val db = PowerSyncDatabase(
            factory = DatabaseDriverFactory(context),
            schema = appSchema,
            dbFilename = "powersync.db"
        )

        // Start synchronization asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            db.connect(credentialsProvider)
        }

        return db
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        val driver = BundledSQLiteDriver()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, "expense.db")
            .setDriver(driver)
            .fallbackToDestructiveMigration() // For development simplicity
            .build()

        // Seed default categories asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            val categoryDao = db.categoryDao()
            val existing = categoryDao.getAllCategories().first()
            if (existing.isEmpty()) {
                categoryDao.insertCategories(defaultCategories)
            }
        }

        return db
    }

    @Provides
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()

    @Provides
    fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()

    @Provides
    fun provideGroupDao(db: AppDatabase): GroupDao = db.groupDao()

    @Provides
    fun provideSplitDao(db: AppDatabase): SplitDao = db.splitDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideGroupMemberDao(db: AppDatabase): GroupMemberDao = db.groupMemberDao()
}
