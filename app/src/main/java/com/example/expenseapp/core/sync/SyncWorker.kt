package com.example.expenseapp.core.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncWorkerEntryPoint {
        fun syncManager(): SyncManager
    }

    override suspend fun doWork(): Result {
        Log.d("SyncWorker", "Executing background offline sync...")
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                SyncWorkerEntryPoint::class.java
            )
            val syncManager = entryPoint.syncManager()
            val success = syncManager.syncPendingItems()
            if (success) {
                Log.d("SyncWorker", "Background offline sync finished successfully.")
                Result.success()
            } else {
                Log.w("SyncWorker", "Some items failed to sync, scheduling retry.")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "Background offline sync encountered an unexpected error", e)
            Result.retry()
        }
    }
}
