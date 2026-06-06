package com.ariok12.virtualmic

import android.content.Context
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val micGain: Float = 1.0f,
    val isStereo: Boolean = true,
    val sampleRate: Int = 48000,
    val isDarkMode: Boolean = false
)

class SettingsManager(private val context: Context) {
    companion object {
        private val MIC_GAIN = floatPreferencesKey("mic_gain")
        private val IS_STEREO = booleanPreferencesKey("is_stereo")
        private val SAMPLE_RATE = intPreferencesKey("sample_rate")
        private val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
    }

    val appSettingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            micGain = preferences[MIC_GAIN] ?: 1.0f,
            isStereo = preferences[IS_STEREO] ?: true,
            sampleRate = preferences[SAMPLE_RATE] ?: 48000,
            isDarkMode = preferences[IS_DARK_MODE] ?: false
        )
    }

    suspend fun updateMicGain(gain: Float) {
        context.dataStore.edit { it[MIC_GAIN] = gain }
    }

    suspend fun updateIsStereo(isStereo: Boolean) {
        context.dataStore.edit { it[IS_STEREO] = isStereo }
    }

    suspend fun updateSampleRate(sampleRate: Int) {
        context.dataStore.edit { it[SAMPLE_RATE] = sampleRate }
    }

    suspend fun updateIsDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[IS_DARK_MODE] = enabled }
    }
}
