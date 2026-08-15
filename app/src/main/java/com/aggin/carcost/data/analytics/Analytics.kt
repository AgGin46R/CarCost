package com.aggin.carcost.data.analytics

import android.util.Log
import com.aggin.carcost.BuildConfig
import com.my.tracker.MyTracker

/**
 * Единая точка отправки событий в аналитику.
 *
 * Зачем обёртка, а не прямые вызовы MyTracker по экранам:
 *
 * 1. Имена событий собраны в одном файле. Отчёты в кабинете строятся по строкам,
 *    и опечатка в имени создаёт новое событие вместо того, чтобы дополнить
 *    существующее — заметить это можно только через неделю по пустому графику.
 * 2. Отключается целиком одной правкой. Если завтра аналитику решат убрать,
 *    не придётся вычищать вызовы из двадцати экранов.
 * 3. Здесь же стоит запрет на отправку личных данных — см. ниже.
 *
 * **Что сюда нельзя передавать.** Ни сумм расходов, ни пробегов, ни марок и
 * номеров автомобилей, ни адресов, ни текста сообщений, ни имён файлов, ни
 * почты. Аналитика отвечает на вопрос «каким разделом пользуются», а не «что
 * человек записал». Параметры допустимы только из заранее известного набора
 * значений: тип расхода, способ входа, вид вложения.
 */
object Analytics {

    private const val TAG = "Analytics"

    private val isEnabled: Boolean
        get() = BuildConfig.MYTRACKER_SDK_KEY.isNotBlank()

    // ── Запуск и вход ────────────────────────────────────────────────────────

    /** [method] — "email" или "vk" */
    fun login(method: String) = event("login", mapOf("method" to method))

    /** [method] — "email" или "vk" */
    fun registration(method: String) = event("registration", mapOf("method" to method))

    fun logout() = event("logout")

    fun accountDeleted() = event("account_deleted")

    // ── Автомобили ───────────────────────────────────────────────────────────

    fun carAdded() = event("car_added")

    fun carDeleted() = event("car_deleted")

    // ── Расходы ──────────────────────────────────────────────────────────────

    /**
     * [category] — ключ категории (FUEL, SERVICE и прочие), а не название,
     * введённое человеком: названия бывают произвольными и попадать в аналитику
     * не должны.
     */
    fun expenseAdded(category: String) = event("expense_added", mapOf("category" to category))

    fun expenseEdited() = event("expense_edited")

    fun expenseDeleted() = event("expense_deleted")

    /** Расход добавлен через виджет на рабочем столе, а не из приложения */
    fun expenseFromWidget() = event("expense_from_widget")

    /** Чек распознан камерой */
    fun receiptScanned() = event("receipt_scanned")

    // ── Разделы ──────────────────────────────────────────────────────────────

    fun analyticsOpened() = event("analytics_opened")

    fun navigatorOpened() = event("navigator_opened")

    fun routeBuilt() = event("route_built")

    fun navigationStarted() = event("navigation_started")

    fun searchUsed() = event("search_used")

    // ── Обслуживание и документы ─────────────────────────────────────────────

    fun reminderCreated() = event("reminder_created")

    fun maintenanceLogged() = event("maintenance_logged")

    fun documentAdded() = event("document_added")

    // ── Совместное владение и чат ────────────────────────────────────────────

    fun inviteCreated() = event("invite_created")

    fun inviteAccepted() = event("invite_accepted")

    /** [kind] — "text", "photo", "video", "file", "voice" */
    fun chatMessageSent(kind: String) = event("chat_message_sent", mapOf("kind" to kind))

    // ── Данные ───────────────────────────────────────────────────────────────

    fun backupExported() = event("backup_exported")

    fun backupRestored() = event("backup_restored")

    fun reportExported() = event("report_exported")

    // ── Внутреннее ───────────────────────────────────────────────────────────

    /**
     * Отправка события. Отказ аналитики не должен ничем отзываться в приложении,
     * поэтому исключения гасятся здесь и дальше не идут.
     */
    private fun event(name: String, params: Map<String, String> = emptyMap()) {
        if (!isEnabled) return
        try {
            if (params.isEmpty()) MyTracker.trackEvent(name)
            else MyTracker.trackEvent(name, params)
            if (BuildConfig.DEBUG) Log.d(TAG, "событие: $name $params")
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось отправить событие $name: ${e.message}")
        }
    }
}
