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

        /**
         * Приглашение внести первую запись уже отправляли.
         *
         * Отправляется ровно один раз. Человек, не откликнувшийся на одно
         * приглашение, не откликнется и на пятое — а настойчивость превращает
         * приложение в источник раздражения.
         */
        val FIRST_RECORD_NUDGE_SENT_KEY = booleanPreferencesKey("first_record_nudge_sent")
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

        /**
         * Геозоны вокруг заправок.
         *
         * По умолчанию выключено, и это не осторожность ради осторожности:
         * функция требует фонового местоположения, а его нельзя включать
         * молча за человека.
         */
        val GEOFENCE_FUEL_KEY = booleanPreferencesKey("geofence_fuel")

        /**
         * Попадает ли час в окно тишины.
         *
         * Отдельной чистой функцией ради одного случая: окно почти всегда
         * переходит через полночь (22:00–8:00), и наивное `hour in start..end`
         * тогда не срабатывает никогда. Ошибка тихая — уведомления просто
         * продолжают приходить ночью, и по коду настроек не видно почему.
         */
        fun isWithinQuietWindow(hour: Int, start: Int, end: Int): Boolean = when {
            // Окно нулевой длины — тишины нет
            start == end -> false
            start < end -> hour in start until end      // напр. 1:00–7:00
            else -> hour >= start || hour < end         // напр. 22:00–8:00
        }
    }

    val themeFlow: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[THEME_KEY] ?: "System" }

    val accentFlow: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[ACCENT_KEY] ?: "Blue" }

    val onboardingDoneFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[ONBOARDING_DONE_KEY] ?: false }

    val firstRecordNudgeSentFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[FIRST_RECORD_NUDGE_SENT_KEY] ?: false }

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

    val geofenceFuelFlow: Flow<Boolean> = context.dataStore.data
        .map { it[GEOFENCE_FUEL_KEY] ?: false }

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

    suspend fun setFirstRecordNudgeSent() {
        context.dataStore.edit { it[FIRST_RECORD_NUDGE_SENT_KEY] = true }
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

    suspend fun setGeofenceFuel(enabled: Boolean) {
        context.dataStore.edit { it[GEOFENCE_FUEL_KEY] = enabled }
    }

    /**
     * Вид уведомления — то, чем управляет переключатель в профиле.
     *
     * Видов меньше, чем поводов написать: несколько поводов делят один
     * переключатель, если человек воспринимает их как одно. Жидкости — то же
     * обслуживание, налог и документы — те же сроки, что и страховка.
     * Разводить каждый повод в свой переключатель значит превратить настройки
     * в список, который никто не читает.
     */
    enum class NotifKind {
        MAINTENANCE,

        /** Страховки, документы, транспортный налог — всё, у чего есть срок */
        PAPERWORK,

        /** Еженедельная сводка и итоги года */
        DIGEST,

        FUEL,
        BUDGET,

        /**
         * Переключателя нет и быть не должно.
         *
         * Сообщения совладельцев, приглашения, обновления приложения и таймер
         * парковки, который человек завёл сам. Спрятать их под настройку
         * «напоминания» значило бы, что выключивший напоминания перестаёт
         * получать сообщения.
         */
        ALWAYS
    }

    /**
     * Включён ли этот вид уведомлений.
     *
     * Чтение синхронное — как и у тихих часов: вызывается из воркеров и
     * приёмников, где корутину уже не запустить.
     */
    fun isNotifEnabled(kind: NotifKind): Boolean {
        if (kind == NotifKind.ALWAYS) return true

        val prefs = runCatching {
            kotlinx.coroutines.runBlocking { context.dataStore.data.first() }
        }.getOrNull() ?: return true  // не смогли прочитать настройки — не молчим

        val key = when (kind) {
            NotifKind.MAINTENANCE -> NOTIF_MAINTENANCE_KEY
            NotifKind.PAPERWORK -> NOTIF_INSURANCE_KEY
            NotifKind.DIGEST -> NOTIF_DIGEST_KEY
            NotifKind.FUEL -> NOTIF_FUEL_KEY
            NotifKind.BUDGET -> NOTIF_BUDGET_ALERT_KEY
            NotifKind.ALWAYS -> return true
        }
        // Пустая настройка означает «не трогали», а не «выключено»
        return prefs[key] ?: true
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
        return isWithinQuietWindow(now, start, end)
    }

    fun lastChatSeenFlow(carId: String): Flow<Long> {
        val key = longPreferencesKey("last_chat_seen_$carId")
        return context.dataStore.data.map { it[key] ?: 0L }
    }

    suspend fun setLastChatSeen(carId: String, timestamp: Long = System.currentTimeMillis()) {
        val key = longPreferencesKey("last_chat_seen_$carId")
        context.dataStore.edit { it[key] = timestamp }
    }

    // ── Синхронизация при выходе приложения на передний план ─────────────────
    // Раньше отправка на сервер шла только при входе и выходе из аккаунта, и у
    // тех, кто не выходит, локальные записи копились месяцами.

    private val lastForegroundSyncKey = longPreferencesKey("last_foreground_sync")

    suspend fun getLastForegroundSync(): Long =
        context.dataStore.data.map { it[lastForegroundSyncKey] ?: 0L }.first()

    suspend fun setLastForegroundSync(timestamp: Long = System.currentTimeMillis()) {
        context.dataStore.edit { it[lastForegroundSyncKey] = timestamp }
    }
}