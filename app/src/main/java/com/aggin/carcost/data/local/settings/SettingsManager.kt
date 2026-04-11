package com.aggin.carcost.data.local.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Объявляем DataStore на уровне файла
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        val THEME_KEY = stringPreferencesKey("app_theme")
        val ONBOARDING_DONE_KEY = booleanPreferencesKey("onboarding_done")
        val NOTIF_MAINTENANCE_KEY = booleanPreferencesKey("notif_maintenance")
        val NOTIF_INSURANCE_KEY = booleanPreferencesKey("notif_insurance")
        val NOTIF_DIGEST_KEY = booleanPreferencesKey("notif_digest")
        val NOTIF_FUEL_KEY = booleanPreferencesKey("notif_fuel")
    }

    val themeFlow: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[THEME_KEY] ?: "System" }

    val onboardingDoneFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[ONBOARDING_DONE_KEY] ?: false }

    val notifMaintenanceFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[NOTIF_MAINTENANCE_KEY] ?: true }

    val notifInsuranceFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[NOTIF_INSURANCE_KEY] ?: true }

    val notifDigestFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[NOTIF_DIGEST_KEY] ?: true }

    val notifFuelFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[NOTIF_FUEL_KEY] ?: true }

    suspend fun saveTheme(theme: String) {
        context.dataStore.edit { settings -> settings[THEME_KEY] = theme }
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { settings -> settings[ONBOARDING_DONE_KEY] = true }
    }

    suspend fun setNotifMaintenance(enabled: Boolean) {
        context.dataStore.edit { it[NOTIF_MAINTENANCE_KEY] = enabled }
    }

    suspend fun setNotifInsurance(enabled: Boolean) {
        context.dataStore.edit { it[NOTIF_INSURANCE_KEY] = enabled }
    }

    suspend fun setNotifDigest(enabled: Boolean) {
        context.dataStore.edit { it[NOTIF_DIGEST_KEY] = enabled }
    }

    suspend fun setNotifFuel(enabled: Boolean) {
        context.dataStore.edit { it[NOTIF_FUEL_KEY] = enabled }
    }

    fun lastChatSeenFlow(carId: String): Flow<Long> {
        val key = longPreferencesKey("last_chat_seen_$carId")
        return context.dataStore.data.map { it[key] ?: 0L }
    }

    suspend fun setLastChatSeen(carId: String, timestamp: Long = System.currentTimeMillis()) {
        val key = longPreferencesKey("last_chat_seen_$carId")
        context.dataStore.edit { it[key] = timestamp }
    }
}