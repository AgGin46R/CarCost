package com.aggin.carcost.domain.fuel

import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.reference.FuelStations

/**
 * Во сколько обходится литр на каждой заправке.
 *
 * Всё нужное уже пишется в каждой записи о заправке: место, литры и сумма.
 * Цена литра — деление одного на другое, и никаких новых данных собирать не
 * требуется. При этом человек, заправляющийся на трёх соседних АЗС, до сих пор
 * не мог узнать, какая из них дешевле, — цифры лежали рядом, но не сравнивались.
 *
 * Считается по фактически уплаченному, а не по ценникам: скидочные карты,
 * акции и разница между колонками входят в цену сами собой.
 */
object StationPriceAnalyzer {

    /**
     * @param overpayPerLiter насколько литр здесь дороже, чем на самой дешёвой
     *   из ваших заправок. Ноль у самой дешёвой.
     * @param overpayTotal во что обошлась эта разница на всех заправках здесь —
     *   именно она превращает «дороже на два рубля» в понятную сумму
     */
    data class Station(
        val name: String,
        val averagePerLiter: Double,
        val lastPerLiter: Double,
        val lastDate: Long,
        val fillUps: Int,
        val totalLiters: Double,
        val totalSpent: Double,
        val overpayPerLiter: Double,
        val overpayTotal: Double
    )

    /**
     * Сколько заправок нужно, чтобы попасть в сравнение.
     *
     * Одна заправка — это одна цена в один день, а не цена заправки. Сравнивать
     * по ней значит объявлять «здесь дороже» после единственного визита, когда
     * там могла быть просто другая неделя.
     */
    private const val MIN_FILL_UPS = 2

    /**
     * @param expenses любые расходы автомобиля — лишние отсеиваются здесь
     * @return заправки от самой дешёвой к самой дорогой; пустой список, когда
     *   сравнивать не с чем
     */
    fun analyze(expenses: List<Expense>): List<Station> {
        val byStation = expenses
            .asSequence()
            .filter { it.category == ExpenseCategory.FUEL }
            // Литры нужны обязательно: без них цену литра не вычислить, а
            // записи без объёма у людей встречаются часто
            .filter { (it.fuelLiters ?: 0.0) > 0.0 && it.amount > 0.0 }
            .mapNotNull { expense ->
                FuelStations.normalize(expense.location)?.let { it to expense }
            }
            .groupBy({ it.first }, { it.second })

        val stations = byStation.mapNotNull { (name, list) ->
            if (list.size < MIN_FILL_UPS) return@mapNotNull null

            val liters = list.sumOf { it.fuelLiters ?: 0.0 }
            val spent = list.sumOf { it.amount }
            if (liters <= 0.0) return@mapNotNull null

            // Средняя цена — по всему объёму, а не среднее из цен заправок.
            // Иначе одна маленькая дорогая заправка весит столько же, сколько
            // полный бак, и средняя перестаёт отражать потраченное.
            val average = spent / liters
            val last = list.maxBy { it.date }

            Station(
                name = name,
                averagePerLiter = average,
                lastPerLiter = last.amount / (last.fuelLiters ?: 1.0),
                lastDate = last.date,
                fillUps = list.size,
                totalLiters = liters,
                totalSpent = spent,
                overpayPerLiter = 0.0,
                overpayTotal = 0.0
            )
        }

        if (stations.size < 2) return stations.sortedBy { it.averagePerLiter }

        val cheapest = stations.minOf { it.averagePerLiter }
        return stations
            .map { station ->
                val diff = station.averagePerLiter - cheapest
                station.copy(
                    overpayPerLiter = diff,
                    overpayTotal = diff * station.totalLiters
                )
            }
            .sortedBy { it.averagePerLiter }
    }

    /**
     * Сколько всего переплачено против самой дешёвой заправки.
     *
     * Это не упрёк и не призыв ездить через весь город за рублём: цифра просто
     * показывает цену привычки, а решение остаётся за человеком.
     */
    fun totalOverpay(stations: List<Station>): Double =
        stations.sumOf { it.overpayTotal }
}
