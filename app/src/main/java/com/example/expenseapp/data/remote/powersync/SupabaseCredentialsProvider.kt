package com.example.expenseapp.data.remote.powersync

import com.powersync.PowerSyncDatabase
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.connectors.PowerSyncCredentials
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import javax.inject.Inject

/**
 * Provides PowerSync credentials by retrieving a token from Supabase.
 * PowerSync requires a JWT from your backend to authorize sync.
 */
class SupabaseCredentialsProvider @Inject constructor(
    private val supabaseClient: SupabaseClient
) : PowerSyncBackendConnector() {

    override suspend fun fetchCredentials(): PowerSyncCredentials? {
        val session = supabaseClient.auth.currentSessionOrNull() ?: return null
        return PowerSyncCredentials(
            endpoint = "https://your-powersync-instance.powersync.com", // Replace with actual URL
            token = session.accessToken
        )
    }

    override suspend fun uploadData(database: PowerSyncDatabase) {
        // Implement to upload local changes to Supabase.
        // This is a placeholder for development.
    }
}
