package com.example.expenseapp.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.example.expenseapp.data.local.AppDatabase
import com.example.expenseapp.data.local.appSchema
import com.example.expenseapp.data.local.dao.*
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
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

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

        return Room.databaseBuilder(context, AppDatabase::class.java, "expense.db")
            .setDriver(driver)
            .fallbackToDestructiveMigration() // For development simplicity
            .build()
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
