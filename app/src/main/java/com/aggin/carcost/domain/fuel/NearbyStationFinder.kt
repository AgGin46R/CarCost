package com.aggin.carcost.domain.fuel

import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.reference.FuelStations
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Заправка, на которой человек уже бывал, рядом с заданной точкой.
 *
 * Никакого справочника АЗС здесь нет и не нужно: приложение узнаёт только те
 * места, куда владелец уже заезжал и сам их назвал. Это и точнее внешнего
 * каталога, и не требует ни сети, ни новых разрешений — координаты у заправок
 * уже записаны.
 */
object NearbyStationFinder {

    /** Заправка считается «той самой» в этом радиусе */
    const val MATCH_RADIUS_M = 200.0

    /**
     * Сколько времени после записанной заправки не предлагать новую.
     *
     * Человек мог записать её сам сразу на месте — и получить следом
     * уведомление «не забыли записать заправку?» было бы обидно.
     */
    const val RECENT_FILL_UP_WINDOW_MS = 2 * 60 * 60 * 1000L

    data class Match(
        /** Название, приведённое к виду сети: «Лукойл», а не «Лукойл на Ленина» */
        val name: String,
        val distanceMeters: Double,
        /** Сколько раз здесь уже заправлялись */
        val visits: Int
    )

    /**
     * Ищет знакомую заправку рядом с точкой.
     *
     * @param expenses все расходы машины
     * @param lat широта точки, где закончилась поездка
     * @param lon долгота
     * @param now текущее время — для проверки недавней заправки
     *
     * @return ближайшая известная заправка в радиусе [MATCH_RADIUS_M], либо
     *   null: точки рядом нет, координат у заправок нет, или заправку только
     *   что записали вручную
     */
    fun find(
        expenses: List<Expense>,
        lat: Double,
        lon: Double,
        now: Long = System.currentTimeMillis()
    ): Match? {
        val fuel = expenses.filter { it.category == ExpenseCategory.FUEL }

        // Заправку уже записали — предлагать нечего
        if (recentlyRecorded(expenses, now)) return null

        val candidates = fuel.mapNotNull { expense ->
            val eLat = expense.latitude ?: return@mapNotNull null
            val eLon = expense.longitude ?: return@mapNotNull null
            val name = FuelStations.normalize(expense.location) ?: return@mapNotNull null
            Triple(name, distanceMeters(lat, lon, eLat, eLon), expense)
        }.filter { it.second <= MATCH_RADIUS_M }

        if (candidates.isEmpty()) return null

        // Группируем по названию: у одной заправки в истории несколько записей,
        // и без группировки «сколько раз бывали» всегда равнялось бы единице
        return candidates.groupBy { it.first }
            .map { (name, list) ->
                Match(
                    name = name,
                    distanceMeters = list.minOf { it.second },
                    visits = list.size
                )
            }
            .minByOrNull { it.distanceMeters }
    }

    /**
     * Записывал ли человек заправку только что.
     *
     * Отдельной функцией, потому что нужна в двух местах: и при подсказке по
     * концу поездки, и при срабатывании геозоны. В геозоне проверять через
     * [find] было бы неверно — тот возвращает null ещё и когда рядом нет
     * знакомой заправки, и два разных случая слились бы в один.
     */
    fun recentlyRecorded(expenses: List<Expense>, now: Long = System.currentTimeMillis()): Boolean =
        expenses.any {
            it.category == ExpenseCategory.FUEL && now - it.date in 0..RECENT_FILL_UP_WINDOW_MS
        }

    /**
     * Расстояние по большому кругу, в метрах.
     *
     * Своя реализация, а не `Location.distanceBetween`: та живёт в Android SDK
     * и в юнит-тестах возвращает нули без объяснений. На расстояниях в сотни
     * метров формула гаверсинуса точна с запасом.
     */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        // min нужен на случай, когда накопленная погрешность даёт a чуть больше
        // единицы: sqrt отрицательного числа вернул бы NaN
        return 2 * earthRadius * asin(min(1.0, sqrt(a)))
    }
}
