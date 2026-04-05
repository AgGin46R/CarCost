package com.aggin.carcost.data.local.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Объявляем DataStore на уровне файла
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    // Ключ для хранения нашей настройки
    companion object {
        val THEME_KEY = stringPreferencesKey("app_theme")
        val ONBOARDING_DONE_KEY = booleanPreferencesKey("onboarding_done")
    }

    val themeFlow: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[THEME_KEY] ?: "System" }

    val onboardingDoneFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[ONBOARDING_DONE_KEY] ?: false }

    suspend fun saveTheme(theme: String) {
        context.dataStore.edit { settings -> settings[THEME_KEY] = theme }
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { settings -> settings[ONBOARDING_DONE_KEY] = true }
    }
}