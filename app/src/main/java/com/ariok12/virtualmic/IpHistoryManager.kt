package com.ariok12.virtualmic

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class IpHistoryManager(private val context: Context) {
    companion object {
        private val IP_HISTORY_KEY = stringPreferencesKey("ip_history")
    }

    val ipHistory: Flow<List<String>> = context.dataStore.data.map { preferences ->
        val historyStr = preferences[IP_HISTORY_KEY] ?: ""
        if (historyStr.isBlank()) emptyList() else historyStr.split(",")
    }

    suspend fun addIpToHistory(ip: String) {
        if (ip.isBlank()) return
        context.dataStore.edit { preferences ->
            val currentHistory = preferences[IP_HISTORY_KEY] ?: ""
            val ips = currentHistory.split(",").filter { it.isNotBlank() }.toMutableList()
            ips.remove(ip)
            ips.add(0, ip) // Add to top
            if (ips.size > 5) {
                ips.subList(5, ips.size).clear()
            }
            preferences[IP_HISTORY_KEY] = ips.joinToString(",")
        }
    }
}
