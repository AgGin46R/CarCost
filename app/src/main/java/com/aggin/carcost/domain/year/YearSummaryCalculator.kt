package com.aggin.carcost.domain.year

import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.database.entities.GpsTrip
import com.aggin.carcost.data.reference.FuelStations
import java.util.Calendar

/**
 * Итоги года по одной машине.
 *
 * Все числа тут либо есть в данных, либо их нет. Ни одно не достраивается
 * оценкой: страница итогов — то, что человек показывает другим, и выдуманная
 * цифра на ней живёт дольше и расходится дальше, чем ошибка на обычном экране.
 * Поэтому почти всё поле — nullable, а экран просто не рисует то, чего нет.
 */
object YearSummaryCalculator {

    data class MonthTotal(val month: Int, val amount: Double)

    data class YearSummary(
        val year: Int,
        val recordCount: Int,

        val totalSpent: Double,
        /** Пробег за год: разница между наибольшим и наименьшим одометром записей */
        val kmDriven: Int?,
        val costPerKm: Double?,

        val busiestMonth: MonthTotal?,
        val quietestMonth: MonthTotal?,

        val fillUps: Int,
        val liters: Double,
        val fuelSpent: Double,
        val averagePricePerLiter: Double?,
        /** Заправка, на которую пришлось больше всего литров */
        val favouriteStation: String?,

        val byCategory: List<Pair<ExpenseCategory, Double>>,

        val longestTripKm: Double?,
        val tripsRecorded: Int,

        /** Потрачено за прошлый год. null — прошлого года в данных нет */
        val previousYearSpent: Double?
    ) {
        /**
         * Есть ли о чём рассказывать.
         *
         * Год с двумя записями — не итоги, а недоразумение: страница выйдет
         * пустой, и показывать её человеку хуже, чем честно сказать, что
         * данных мало.
         */
        val hasEnoughData: Boolean get() = recordCount >= MIN_RECORDS

        /** Насколько больше или меньше прошлого года, долей. null — сравнить не с чем */
        val changeVsPrevious: Double?
            get() {
                val previous = previousYearSpent ?: return null
                if (previous <= 0) return null
                return (totalSpent - previous) / previous
            }
    }

    private const val MIN_RECORDS = 5

    /**
     * @param expenses все расходы машины, за любые годы
     * @param trips поездки по GPS, за любые годы
     * @param year год, за который считаем
     */
    fun calculate(
        expenses: List<Expense>,
        trips: List<GpsTrip> = emptyList(),
        year: Int
    ): YearSummary {
        val ofYear = expenses.filter { yearOf(it.date) == year }
        val ofPrevious = expenses.filter { yearOf(it.date) == year - 1 }

        val total = ofYear.sumOf { it.amount }

        // Пробег за год — по одометру записей. Записи без одометра сюда не
        // попадают: ноль в этом поле означает «не указан», и он превратил бы
        // годовой пробег в весь пробег машины
        val odometers = ofYear.map { it.odometer }.filter { it > 0 }
        val km = if (odometers.size >= 2) {
            (odometers.max() - odometers.min()).takeIf { it > 0 }
        } else null

        val byMonth = ofYear.groupBy { monthOf(it.date) }
            .map { (month, list) -> MonthTotal(month, list.sumOf { it.amount }) }

        val fuel = ofYear.filter { it.category == ExpenseCategory.FUEL }
        val liters = fuel.mapNotNull { it.fuelLiters }.sum()
        val fuelSpent = fuel.sumOf { it.amount }

        return YearSummary(
            year = year,
            recordCount = ofYear.size,
            totalSpent = total,
            kmDriven = km,
            costPerKm = km?.takeIf { it > 0 && total > 0 }?.let { total / it },
            busiestMonth = byMonth.maxByOrNull { it.amount },
            // Самый тихий месяц имеет смысл, только когда месяцев было
            // несколько: иначе он совпадёт с самым дорогим
            quietestMonth = byMonth.takeIf { it.size > 1 }?.minByOrNull { it.amount },
            fillUps = fuel.size,
            liters = liters,
            fuelSpent = fuelSpent,
            // Средняя цена литра — по объёму, а не среднее из цен: заправка на
            // 60 литров весит больше, чем на 10
            averagePricePerLiter = liters.takeIf { it > 0 }?.let { fuelSpent / it },
            favouriteStation = favouriteStation(fuel),
            byCategory = ofYear.groupBy { it.category }
                .map { (cat, list) -> cat to list.sumOf { it.amount } }
                .sortedByDescending { it.second },
            longestTripKm = trips.filter { yearOf(it.startTime) == year }
                .maxOfOrNull { it.distanceKm }
                ?.takeIf { it > 0 },
            tripsRecorded = trips.count { yearOf(it.startTime) == year },
            previousYearSpent = ofPrevious.takeIf { it.isNotEmpty() }?.sumOf { it.amount }
        )
    }

    /**
     * Заправка, на которую ушло больше всего литров.
     *
     * Считается по литрам, а не по числу визитов: две большие заправки на сети
     * значат больше, чем пять маленьких по дороге. Названия сводятся так же,
     * как в разборе цен по АЗС, иначе «Лукойл на Ленина» и «Лукойл» разойдутся.
     */
    private fun favouriteStation(fuel: List<Expense>): String? =
        fuel.mapNotNull { expense ->
            val place = expense.location?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val liters = expense.fuelLiters ?: return@mapNotNull null
            (FuelStations.normalize(place) ?: return@mapNotNull null) to liters
        }
            .groupBy({ it.first }, { it.second })
            .maxByOrNull { it.value.sum() }
            ?.key

    private fun yearOf(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR)

    private fun monthOf(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.MONTH)
}
