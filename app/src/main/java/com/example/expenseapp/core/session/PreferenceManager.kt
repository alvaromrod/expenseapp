package com.example.expenseapp.core.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_LAST_GROUP_ID = stringPreferencesKey("last_group_id")
        val KEY_LAST_CURRENCY = stringPreferencesKey("last_currency")
    }

    val userId: Flow<String?> = dataStore.data.map { it[KEY_USER_ID] }
    val lastGroupId: Flow<String?> = dataStore.data.map { it[KEY_LAST_GROUP_ID] }
    val lastCurrency: Flow<String?> = dataStore.data.map { it[KEY_LAST_CURRENCY] ?: "EUR" }

    suspend fun saveUserId(userId: String?) {
        dataStore.edit { prefs ->
            if (userId != null) prefs[KEY_USER_ID] = userId
            else prefs.remove(KEY_USER_ID)
        }
    }

    suspend fun saveLastGroup(groupId: String) {
        dataStore.edit { it[KEY_LAST_GROUP_ID] = groupId }
    }

    suspend fun saveLastCurrency(currency: String) {
        dataStore.edit { it[KEY_LAST_CURRENCY] = currency }
    }
    
    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
