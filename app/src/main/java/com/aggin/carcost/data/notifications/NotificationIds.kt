package com.aggin.carcost.data.notifications

import kotlin.math.abs

/**
 * Номера уведомлений — все в одном месте.
 *
 * Система различает уведомления только по номеру: `notify` с уже занятым
 * номером не добавляет второе, а **заменяет** первое. Пока номера раздавались
 * по месту, диапазоны наложились друг на друга, и приложение молча съедало
 * собственные уведомления:
 *
 * - второе предупреждение о бюджете получало номер 2001 — тот же, под которым
 *   работает уведомление записи поездки. Уведомление службы переднего плана
 *   подменялось текстом про бюджет, и человек терял кнопку остановки записи;
 * - «ТО по сроку» и таймер парковки оба стояли на 7000;
 * - жидкости занимали 5000–5899 и наезжали на страховки (5010, 5070, 5140, 5300);
 * - в фоновой синхронизации чат (50 000–57 999), расходы (55 000–62 999) и
 *   напоминания (60 000–67 999) перекрывали друг друга: базы разнесены на
 *   5 000, а разброс по машине был 8 000;
 * - приглашения из realtime (50 000–58 999) наезжали на чат и расходы оттуда же.
 *
 * Правило: **новый вид уведомления получает свой блок здесь**, а не число по
 * месту. Блоки по 10 000 и разнесены на 10 000 — запаса хватит навсегда,
 * а пересечься они уже не могут.
 */
object NotificationIds {

    /**
     * Ширина блока.
     *
     * Смещение внутри блока — индекс записи или хеш машины. Десять тысяч
     * корзин выбраны не «на глаз»: при меньшем блоке два разных сообщения с
     * похожим хешем начали бы вытеснять друг друга внутри своего же вида.
     */
    private const val BLOCK = 10_000

    // ── Занято, здесь не выдаём ─────────────────────────────────────────────
    //
    // Эти номера объявлены в других местах и НЕ переносятся: менять
    // работающее ради порядка — лишний риск. Записаны, чтобы следующий вид
    // уведомлений в них не въехал.
    //
    //   2001  — GpsTripService, служба переднего плана; живёт всю поездку
    //   3001  — NavigationService, служба переднего плана; живёт всё ведение
    //   99000 — NotificationHelper.NOTIF_ID_UPDATE, обновление приложения

    // ── Разовые: номер один, второго такого уведомления не бывает ───────────

    const val PARKING_TIMER = 10_001
    const val FIRST_RECORD_NUDGE = 10_002
    const val WEEKLY_SUMMARY = 10_003
    const val STATION_HINT = 10_004
    const val GEOFENCE_FILL_UP = 10_005

    // ── По машине или записи: база блока + смещение ─────────────────────────

    private const val FUEL_LOW = 100_000
    private const val MAINTENANCE_KM = 110_000
    private const val MAINTENANCE_DATE = 120_000
    private const val INSURANCE = 130_000
    private const val DOCUMENT = 140_000
    private const val FLUID = 150_000
    private const val BUDGET = 160_000
    private const val VEHICLE_TAX = 170_000
    private const val YEAR_REVIEW = 180_000

    // ── Из подписки на изменения, по идентификатору записи ──────────────────

    private const val RT_CHAT = 200_000
    private const val RT_EXPENSE = 210_000
    private const val RT_REMINDER = 220_000
    private const val RT_INVITATION = 230_000

    // ── Из фоновой синхронизации, по машине ─────────────────────────────────
    //
    // Отдельно от подписки намеренно: фоновая задача работает, когда
    // приложение убито и подписка не подключена, поэтому один и тот же повод
    // приходит ровно одним путём. Разные блоки — чтобы редкое наложение путей
    // давало два уведомления, а не съедало одно.

    private const val BG_CHAT = 300_000
    private const val BG_EXPENSE = 310_000
    private const val BG_REMINDER = 320_000

    fun fuelLow(index: Int) = FUEL_LOW + within(index)
    fun maintenanceByKm(index: Int) = MAINTENANCE_KM + within(index)
    fun maintenanceByDate(index: Int) = MAINTENANCE_DATE + within(index)

    /**
     * Уведомление о сроке.
     *
     * Номер зависит и от записи, и от порога в днях: у одного полиса
     * напоминания за 30 и за 7 дней должны сосуществовать, а не заменять друг
     * друга. Порог занимает старшие разряды внутри блока, запись — младшие,
     * поэтому до сотни записей на порог они не смешиваются.
     */
    fun insurance(index: Int, daysBefore: Int) = INSURANCE + within(daysBefore * 100 + index)
    fun document(index: Int, daysBefore: Int) = DOCUMENT + within(daysBefore * 100 + index)

    fun fluid(carId: String) = FLUID + byId(carId)
    fun budget(index: Int) = BUDGET + within(index)
    fun vehicleTax(index: Int) = VEHICLE_TAX + within(index)
    fun yearReview(index: Int) = YEAR_REVIEW + within(index)

    fun realtimeChat(messageId: String) = RT_CHAT + byId(messageId)
    fun realtimeExpense(expenseId: String) = RT_EXPENSE + byId(expenseId)
    fun realtimeReminder(reminderId: String) = RT_REMINDER + byId(reminderId)
    fun realtimeInvitation(inviteId: String) = RT_INVITATION + byId(inviteId)

    fun backgroundChat(carId: String) = BG_CHAT + byId(carId)
    fun backgroundExpense(carId: String) = BG_EXPENSE + byId(carId)
    fun backgroundReminder(carId: String) = BG_REMINDER + byId(carId)

    /** Удерживает смещение внутри блока, чтобы соседний вид не пострадал */
    private fun within(offset: Int): Int = abs(offset % BLOCK)

    /**
     * Смещение по строковому идентификатору.
     *
     * Остаток берётся ДО `abs`, а не после. `hashCode` может вернуть
     * `Int.MIN_VALUE`, у которого нет положительного значения: `abs` от него
     * возвращает его же, и номер уехал бы в минус вместе со всем блоком.
     */
    private fun byId(value: String): Int = abs(value.hashCode() % BLOCK)
}
