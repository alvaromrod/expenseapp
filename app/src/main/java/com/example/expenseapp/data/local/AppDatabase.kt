package com.example.expenseapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.expenseapp.data.local.entity.CategoryEntity
import com.example.expenseapp.data.local.entity.ExpenseEntity
import com.example.expenseapp.data.local.entity.GroupEntity
import com.example.expenseapp.data.local.entity.SplitEntity
import com.example.expenseapp.data.local.entity.UserEntity
import com.example.expenseapp.data.local.entity.GroupMemberEntity
import com.example.expenseapp.data.local.dao.*

@Database(
    entities = [
        UserEntity::class,
        ExpenseEntity::class,
        GroupEntity::class,
        SplitEntity::class,
        CategoryEntity::class,
        GroupMemberEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun groupDao(): GroupDao
    abstract fun splitDao(): SplitDao
    abstract fun categoryDao(): CategoryDao
    abstract fun groupMemberDao(): GroupMemberDao
}
