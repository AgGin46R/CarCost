package com.aggin.carcost.data.local.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Объявляем DataStore на уровне файла
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        val THEME_KEY = stringPreferencesKey("app_theme")
        val ACCENT_KEY = stringPreferencesKey("accent_color")
        val ONBOARDING_DONE_KEY = booleanPreferencesKey("onboarding_done")
        val NOTIF_MAINTENANCE_KEY = booleanPreferencesKey("notif_maintenance")
        val NOTIF_INSURANCE_KEY = booleanPreferencesKey("notif_insurance")
        val NOTIF_DIGEST_KEY = booleanPreferencesKey("notif_digest")
        val NOTIF_FUEL_KEY = booleanPreferencesKey("notif_fuel")
        // Тихие часы
        val QUIET_HOURS_ENABLED_KEY = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_HOURS_START_KEY = intPreferencesKey("quiet_hours_start") // час 0–23
        val QUIET_HOURS_END_KEY = intPreferencesKey("quiet_hours_end")     // час 0–23
        // Бюджетный алерт
        val NOTIF_BUDGET_ALERT_KEY = booleanPreferencesKey("notif_budget_alert")
    }

    val themeFlow: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[THEME_KEY] ?: "System" }

    val accentFlow: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[ACCENT_KEY] ?: "Blue" }

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

    val quietHoursEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { it[QUIET_HOURS_ENABLED_KEY] ?: false }

    val quietHoursStartFlow: Flow<Int> = context.dataStore.data
        .map { it[QUIET_HOURS_START_KEY] ?: 22 } // по умолчанию с 22:00

    val quietHoursEndFlow: Flow<Int> = context.dataStore.data
        .map { it[QUIET_HOURS_END_KEY] ?: 8 }   // по умолчанию до 8:00

    val notifBudgetAlertFlow: Flow<Boolean> = context.dataStore.data
        .map { it[NOTIF_BUDGET_ALERT_KEY] ?: true }

    suspend fun saveTheme(theme: String) {
        context.dataStore.edit { settings -> settings[THEME_KEY] = theme }
    }

    suspend fun saveAccent(accent: String) {
        context.dataStore.edit { settings -> settings[ACCENT_KEY] = accent }
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

    suspend fun setQuietHoursEnabled(enabled: Boolean) {
        context.dataStore.edit { it[QUIET_HOURS_ENABLED_KEY] = enabled }
    }

    suspend fun setQuietHoursStart(hour: Int) {
        context.dataStore.edit { it[QUIET_HOURS_START_KEY] = hour.coerceIn(0, 23) }
    }

    suspend fun setQuietHoursEnd(hour: Int) {
        context.dataStore.edit { it[QUIET_HOURS_END_KEY] = hour.coerceIn(0, 23) }
    }

    suspend fun setNotifBudgetAlert(enabled: Boolean) {
        context.dataStore.edit { it[NOTIF_BUDGET_ALERT_KEY] = enabled }
    }

    /** Проверяет, попадает ли текущее время в тихие часы. */
    fun isCurrentlyQuietHours(): Boolean {
        val prefs = runCatching {
            // синхронное чтение через blocking call — вызывать только из Worker/background
            kotlinx.coroutines.runBlocking {
                context.dataStore.data.first()
            }
        }.getOrNull() ?: return false
        if (prefs[QUIET_HOURS_ENABLED_KEY] != true) return false
        val start = prefs[QUIET_HOURS_START_KEY] ?: 22
        val end = prefs[QUIET_HOURS_END_KEY] ?: 8
        val now = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return if (start <= end) now in start until end  // напр. 1:00–7:00
        else now >= start || now < end                   // напр. 22:00–8:00 (через полночь)
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