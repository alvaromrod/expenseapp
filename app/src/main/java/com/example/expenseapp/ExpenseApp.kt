package com.example.expenseapp

import android.app.Application
import com.example.expenseapp.core.network.NetworkMonitor
import com.example.expenseapp.core.sync.SyncManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ExpenseApp : Application() {

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    lateinit var syncManager: SyncManager

    override fun onCreate() {
        super.onCreate()
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Automatically push pending offline expenses/actions when connection is restored
        networkMonitor.startMonitoring(
            onConnectionRestored = {
                syncManager.syncPendingItems()
            },
            scope = appScope
        )

        // Also attempt to push on application launch
        appScope.launch {
            syncManager.syncPendingItems()
        }
    }
}
