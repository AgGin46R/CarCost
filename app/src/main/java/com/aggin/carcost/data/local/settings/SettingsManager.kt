package com.aggin.carcost.data.local.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
    }

    // Поток (Flow), который будет сообщать нам о текущей теме.
    // Если ничего не сохранено, по умолчанию будет "System".
    val themeFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[THEME_KEY] ?: "System"
        }

    // Функция для сохранения новой темы
    suspend fun saveTheme(theme: String) {
        context.dataStore.edit { settings ->
            settings[THEME_KEY] = theme
        }
    }
}