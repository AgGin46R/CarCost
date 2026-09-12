package com.aggin.carcost.domain.maintenance

import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.GpsTrip
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Когда подойдёт следующее ТО.
 *
 * Прогноз строится по темпу набега пробега. Главный его источник — **одометр
 * из записей о расходах**, а не GPS-поездки: поездки записывает меньшинство, и
 * пока прогноз опирался только на них, у большинства он не появлялся вообще.
 * Пробег же указывают почти при каждой заправке — эти данные есть у всех, кто
 * вообще пользуется приложением.
 *
 * Темп считается по фактически наблюдаемому периоду, а не по круглым тридцати
 * дням. Прежняя версия делила пробег за месяц на 30 независимо от того, сколько
 * человек пользуется приложением: поставил три дня назад, проехал 300 км —
 * получал 10 км в день вместо ста, и срок уезжал вдесятеро.
 */
object MaintenancePredictionEngine {

    /** За какой период смотрим темп */
    private const val WINDOW_DAYS = 180L

    /**
     * Короче этого периода темп не считаем.
     *
     * Две заправки за три дня говорят о трёх днях, а не о привычке ездить.
     * Лучше не показать прогноз вовсе, чем показать вымышленный.
     */
    private const val MIN_OBSERVED_DAYS = 14L

    /**
     * Дальше этого срока прогноз бессмысленен.
     *
     * При крошечном темпе — один короткий выезд за месяц — деление давало
     * десятки тысяч дней, и экран показывал дату в следующем веке. Формально
     * верно, практически — мусор, и лучше промолчать.
     */
    private const val MAX_HORIZON_DAYS = 3L * 365

    /** Откуда взят темп — показывать необязательно, но для отладки полезно */
    enum class Source { ODOMETER, GPS }

    data class Pace(
        val kmPerDay: Double,
        /** По скольким дням посчитано */
        val observedDays: Long,
        val source: Source
    )

    /**
     * Средний набег километров в день.
     *
     * @return null, если данных не хватает: меньше двух точек, слишком
     *   короткий период наблюдения или машина стояла
     */
    fun averagePace(
        expenses: List<Expense>,
        trips: List<GpsTrip> = emptyList(),
        now: Long = System.currentTimeMillis()
    ): Pace? {
        val since = now - TimeUnit.DAYS.toMillis(WINDOW_DAYS)

        paceFromOdometer(expenses, since, now)?.let { return it }
        return paceFromTrips(trips, since, now)
    }

    /**
     * Темп по одометру записей.
     *
     * Записи без одометра пропускаются: ноль в этом поле означает «не указан»,
     * и он растянул бы период от нуля до текущего пробега.
     */
    private fun paceFromOdometer(expenses: List<Expense>, since: Long, now: Long): Pace? {
        val points = expenses
            .filter { it.date in since..now && it.odometer > 0 }
            .sortedBy { it.date }
        if (points.size < 2) return null

        val days = TimeUnit.MILLISECONDS.toDays(points.last().date - points.first().date)
        if (days < MIN_OBSERVED_DAYS) return null

        // Крайние значения, а не разность последней и первой: одометр вводят
        // руками и ошибаются, а опечатка в меньшую сторону дала бы
        // отрицательный пробег
        val km = points.maxOf { it.odometer } - points.minOf { it.odometer }
        if (km <= 0) return null

        return Pace(km.toDouble() / days, days, Source.ODOMETER)
    }

    private fun paceFromTrips(trips: List<GpsTrip>, since: Long, now: Long): Pace? {
        val recent = trips.filter { it.startTime in since..now }.sortedBy { it.startTime }
        if (recent.size < 2) return null

        val days = TimeUnit.MILLISECONDS.toDays(recent.last().startTime - recent.first().startTime)
        if (days < MIN_OBSERVED_DAYS) return null

        val km = recent.sumOf { it.distanceKm }
        if (km <= 0) return null

        return Pace(km / days, days, Source.GPS)
    }

    /**
     * Ожидаемая дата следующего ТО.
     *
     * @param currentOdometer нынешний пробег машины
     * @return дата, либо null — данных не хватает или срок так далёк, что
     *   называть его нечестно
     */
    fun predictNextServiceDate(
        currentOdometer: Int,
        reminder: MaintenanceReminder,
        expenses: List<Expense>,
        trips: List<GpsTrip> = emptyList(),
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): LocalDate? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val byDate = reminder.nextChangeDate?.let {
            Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
        }

        val kmLeft = reminder.nextChangeOdometer - currentOdometer
        if (kmLeft <= 0) return today  // уже просрочено по пробегу

        val pace = averagePace(expenses, trips, now)
            // Темпа нет — но если у напоминания задан срок, он известен и без
            // всякого прогноза
            ?: return byDate

        val daysLeft = (kmLeft / pace.kmPerDay).toLong()
        if (daysLeft > MAX_HORIZON_DAYS) return byDate

        val byOdometer = today.plusDays(daysLeft)

        // Что наступит раньше, то и есть следующее ТО: регламент задаётся и
        // пробегом, и сроком, и выполняется по первому из них
        return when {
            byDate == null -> byOdometer
            byDate.isBefore(byOdometer) -> byDate
            else -> byOdometer
        }
    }
}
