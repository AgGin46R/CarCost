package com.aggin.carcost.presentation.screens.carbot

import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

/**
 * Rules-based CarBot engine. No external API — fully offline.
 * Matches Russian keywords and queries Room DB for answers.
 */
class CarBotEngine(private val db: AppDatabase) {

    private val ruFmt = NumberFormat.getNumberInstance(Locale("ru", "RU")).apply {
        maximumFractionDigits = 0
    }

    suspend fun processQuery(text: String, carId: String?): String {
        val lower = text.lowercase(Locale("ru"))
        val car = if (carId != null) db.carDao().getCarById(carId) else null

        return when {
            lower.containsAny("привет", "помощь", "что умеешь", "помоги", "help") ->
                buildHelpText()

            lower.containsAny("масло", "oil", "замена масла") ->
                answerOilChange(car)

            lower.containsAny("страховк", "осаго", "каско", "полис") ->
                answerInsurance(car)

            lower.containsAny("трачу", "расходы", "потратил", "расход за", "сколько стоит") ->
                answerSpending(car, lower)

            lower.containsAny("средний расход", "расход топлив", "l/100", "л/100", "бензин", "заправ", "топлив") ->
                answerFuel(car)

            lower.containsAny("поездк", "маршрут", "км за", "километр", "дистанц") ->
                answerTrips(car)

            lower.containsAny("бюджет", "лимит", "превысил") ->
                answerBudget(car)

            lower.containsAny("ремонт", "техосмотр", " то ", "обслуживание", "следующее то") ->
                answerMaintenance(car)

            lower.containsAny("машин", "авто", "автомобил", "пробег") ->
                answerCarInfo(car)

            lower.containsAny("последние расходы", "последних", "история расходов") ->
                answerRecentExpenses(car)

            lower.containsAny("всего потратил", "общая сумма", "всего расходов", "итого") ->
                answerTotal(car)

            lower.containsAny("дорогой месяц", "самый дорогой", "пик расходов") ->
                answerPeakMonth(car)

            else ->
                "🤔 Я не понял вопрос. Попробуйте спросить иначе или нажмите **Помощь**, чтобы увидеть что я умею."
        }
    }

    // ── Конкретные ответы ──────────────────────────────────────────────────

    private fun buildHelpText(): String = """
🤖 **Я могу ответить на:**

• 🛢 Когда менять масло?
• 💰 Сколько я потратил в этом месяце?
• ⛽ Средний расход топлива
• 🔧 Следующее ТО
• 📋 Последние расходы
• 📊 Самый дорогой месяц
• 🏎 Информация об автомобиле
• 🛡 Статус страховки
• 📍 Статистика поездок
• 💳 Бюджет по категориям
• 💵 Итоговые расходы за всё время
    """.trimIndent()

    private suspend fun answerOilChange(car: Car?): String {
        if (car == null) return noCar()
        val reminder = db.maintenanceReminderDao()
            .getReminderByType(car.id, com.aggin.carcost.data.local.database.entities.MaintenanceType.OIL_CHANGE)
            ?: return "🛢 Напоминание о замене масла не настроено для ${car.brand} ${car.model}."

        val kmLeft = reminder.nextChangeOdometer - car.currentOdometer
        return when {
            kmLeft <= 0 -> "⚠️ **Замена масла просрочена** на ${ruFmt.format(-kmLeft)} км!\n" +
                "Текущий пробег: ${ruFmt.format(car.currentOdometer)} км\n" +
                "Плановое ТО: ${ruFmt.format(reminder.nextChangeOdometer)} км"
            kmLeft <= 500 -> "🔶 Замена масла скоро: осталось **${ruFmt.format(kmLeft)} км**\n" +
                "Плановое ТО при ${ruFmt.format(reminder.nextChangeOdometer)} км"
            else -> "✅ Следующая замена масла через **${ruFmt.format(kmLeft)} км**\n" +
                "Плановое ТО при ${ruFmt.format(reminder.nextChangeOdometer)} км"
        }
    }

    private suspend fun answerInsurance(car: Car?): String {
        if (car == null) return noCar()
        val policies = db.insurancePolicyDao().getPoliciesForCarSync(car.id)
        if (policies.isEmpty()) return "🛡 Страховые полисы для ${car.brand} ${car.model} не добавлены."

        val now = System.currentTimeMillis()
        val sb = StringBuilder("🛡 **Страховки ${car.brand} ${car.model}:**\n\n")
        val dateFmt = java.text.SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        for (policy in policies) {
            val daysLeft = ((policy.endDate - now) / 86_400_000L).toInt()
            val statusEmoji = when {
                daysLeft < 0 -> "❌"
                daysLeft < 30 -> "⚠️"
                else -> "✅"
            }
            sb.appendLine("$statusEmoji **${policy.type}** — ${policy.company}")
            sb.appendLine("   До: ${dateFmt.format(java.util.Date(policy.endDate))} " +
                if (daysLeft >= 0) "(через $daysLeft дн.)" else "(просрочена на ${-daysLeft} дн.)")
        }
        return sb.toString().trimEnd()
    }

    private suspend fun answerSpending(car: Car?, queryLower: String): String {
        if (car == null) return noCar()

        val cal = Calendar.getInstance()
        val (label, startDate, endDate) = when {
            queryLower.containsAny("год", "year") -> {
                val y = cal.get(Calendar.YEAR)
                val start = Calendar.getInstance().apply { set(y, 0, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                val end = Calendar.getInstance().apply { set(y, 11, 31, 23, 59, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
                Triple("в $y году", start, end)
            }
            queryLower.containsAny("прошлый месяц", "в прошлом", "прошлом месяц") -> {
                cal.add(Calendar.MONTH, -1)
                val start = Calendar.getInstance().apply { time = cal.time; set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                val end = Calendar.getInstance().apply { time = cal.time; set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH)); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
                Triple("в прошлом месяце", start, end)
            }
            else -> {
                val start = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                Triple("в этом месяце", start, System.currentTimeMillis())
            }
        }

        val total = db.expenseDao().getTotalExpensesInDateRange(car.id, startDate, endDate).first() ?: 0.0
        val expenses = db.expenseDao().getExpensesInDateRangeSync(car.id, startDate, endDate)
        val byCategory = expenses.groupBy { it.category }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }

        val sb = StringBuilder("💰 **Расходы $label на ${car.brand} ${car.model}:**\n\n")
        sb.appendLine("Итого: **${ruFmt.format(total)} ₽** (${expenses.size} записей)")
        if (byCategory.isNotEmpty()) {
            sb.appendLine("\nПо категориям:")
            byCategory.take(5).forEach { (cat, sum) ->
                sb.appendLine("  ${cat.emoji} ${cat.displayName}: ${ruFmt.format(sum)} ₽")
            }
        }
        return sb.toString().trimEnd()
    }

    private suspend fun answerFuel(car: Car?): String {
        if (car == null) return noCar()
        val fuelExpenses = db.expenseDao().getExpensesByCarIdSync(car.id)
            .filter { it.category == ExpenseCategory.FUEL && it.fuelLiters != null && it.fuelLiters > 0 }
            .sortedBy { it.date }

        if (fuelExpenses.size < 2) return "⛽ Недостаточно данных о заправках (нужно минимум 2)."

        val consumptions = mutableListOf<Double>()
        for (i in 1 until fuelExpenses.size) {
            val prev = fuelExpenses[i - 1]
            val curr = fuelExpenses[i]
            val dist = curr.odometer - prev.odometer
            if (dist > 50 && curr.fuelLiters != null) {
                consumptions += (curr.fuelLiters / dist) * 100.0
            }
        }

        if (consumptions.isEmpty()) return "⛽ Не удалось рассчитать расход (нет данных о пробеге между заправками)."

        val avg = consumptions.average()
        val last3Avg = consumptions.takeLast(3).average()
        val totalLiters = fuelExpenses.sumOf { it.fuelLiters ?: 0.0 }
        val totalFuelCost = fuelExpenses.sumOf { it.amount }
        val pricePerLiter = if (totalLiters > 0) totalFuelCost / totalLiters else 0.0

        return """
⛽ **Топливо ${car.brand} ${car.model}:**

Средний расход: **${"%.1f".format(avg)} л/100 км**
Последние 3 заправки: **${"%.1f".format(last3Avg)} л/100 км**
Всего залито: ${ruFmt.format(totalLiters)} л
Средняя цена: ${"%.2f".format(pricePerLiter)} ₽/л
Потрачено на топливо: ${ruFmt.format(totalFuelCost)} ₽
        """.trimIndent()
    }

    private suspend fun answerTrips(car: Car?): String {
        if (car == null) return noCar()
        val trips = db.gpsTripDao().getTripsByCarIdSync(car.id)
        if (trips.isEmpty()) return "📍 Поездки по GPS для ${car.brand} ${car.model} не записаны."

        val totalDist = trips.sumOf { it.distanceKm }
        val avgDist = if (trips.isNotEmpty()) totalDist / trips.size else 0.0
        val longestTrip = trips.maxByOrNull { it.distanceKm }
        val avgSpeed = trips.mapNotNull { it.avgSpeedKmh }.average().let {
            if (it.isNaN()) null else it
        }

        return """
📍 **GPS-поездки ${car.brand} ${car.model}:**

Всего поездок: **${trips.size}**
Общий пробег: **${"%.1f".format(totalDist)} км**
Средняя дистанция: ${"%.1f".format(avgDist)} км
Самая длинная: ${"%.1f".format(longestTrip?.distanceKm ?: 0.0)} км
${if (avgSpeed != null) "Средняя скорость: ${"%.0f".format(avgSpeed)} км/ч" else ""}
        """.trimIndent().trimEnd()
    }

    private suspend fun answerBudget(car: Car?): String {
        if (car == null) return noCar()
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        val budgets = db.categoryBudgetDao().getBudgetsSync(car.id, month, year)
        if (budgets.isEmpty()) return "💳 Бюджеты для ${car.brand} ${car.model} на этот месяц не настроены."

        val startDate = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val endDate = System.currentTimeMillis()

        val sb = StringBuilder("💳 **Бюджет ${car.brand} ${car.model} на этот месяц:**\n\n")
        for (budget in budgets) {
            val spent = db.expenseDao().getTotalByCategoryAndPeriod(car.id, budget.category, startDate, endDate) ?: 0.0
            val pct = (spent / budget.monthlyLimit * 100).toInt()
            val bar = buildProgressBar(pct)
            val status = when {
                pct >= 100 -> "❌"
                pct >= 80 -> "⚠️"
                else -> "✅"
            }
            sb.appendLine("$status **${budget.category.displayName}**: ${ruFmt.format(spent)} / ${ruFmt.format(budget.monthlyLimit)} ₽ ($pct%)")
            sb.appendLine("   $bar")
        }
        return sb.toString().trimEnd()
    }

    private suspend fun answerMaintenance(car: Car?): String {
        if (car == null) return noCar()
        val reminders = db.maintenanceReminderDao().getRemindersByCarIdSync(car.id)
        if (reminders.isEmpty()) return "🔧 Напоминания о ТО для ${car.brand} ${car.model} не настроены."

        val sb = StringBuilder("🔧 **ТО ${car.brand} ${car.model} (пробег: ${ruFmt.format(car.currentOdometer)} км):**\n\n")
        reminders.sortedBy { it.nextChangeOdometer - car.currentOdometer }.forEach { r ->
            val kmLeft = r.nextChangeOdometer - car.currentOdometer
            val status = when {
                kmLeft <= 0 -> "❌ Просрочено (${ruFmt.format(-kmLeft)} км назад)"
                kmLeft <= 500 -> "⚠️ Скоро (через ${ruFmt.format(kmLeft)} км)"
                else -> "✅ Через ${ruFmt.format(kmLeft)} км"
            }
            sb.appendLine("$status — **${r.type.displayName}**")
        }
        return sb.toString().trimEnd()
    }

    private fun answerCarInfo(car: Car?): String {
        if (car == null) return noCar()
        return """
🚗 **${car.brand} ${car.model} (${car.year})**

Гос. номер: ${car.licensePlate}
${if (car.vin != null) "VIN: ${car.vin}" else ""}
Пробег: **${ruFmt.format(car.currentOdometer)} км**
Топливо: ${car.fuelType.name}
${if (car.tankCapacity != null) "Бак: ${car.tankCapacity} л" else ""}
${if (car.color != null) "Цвет: ${car.color}" else ""}
        """.trimIndent().lines().filter { it.isNotBlank() }.joinToString("\n")
    }

    private suspend fun answerRecentExpenses(car: Car?): String {
        if (car == null) return noCar()
        val expenses = db.expenseDao().getExpensesByCarIdSync(car.id)
            .sortedByDescending { it.date }
            .take(5)

        if (expenses.isEmpty()) return "📋 Расходов для ${car.brand} ${car.model} ещё нет."

        val dateFmt = java.text.SimpleDateFormat("dd.MM", Locale.getDefault())
        val sb = StringBuilder("📋 **Последние расходы ${car.brand} ${car.model}:**\n\n")
        expenses.forEach { e ->
            val title = e.title?.take(30) ?: e.category.displayName
            sb.appendLine("• ${dateFmt.format(java.util.Date(e.date))} — **${ruFmt.format(e.amount)} ₽** $title")
        }
        return sb.toString().trimEnd()
    }

    private suspend fun answerTotal(car: Car?): String {
        if (car == null) return noCar()
        val total = db.expenseDao().getTotalExpenses(car.id).first() ?: 0.0
        val count = db.expenseDao().getExpenseCount(car.id).first()
        val byCategory = db.expenseDao().getExpensesByCarIdSync(car.id)
            .groupBy { it.category }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }

        val sb = StringBuilder("💵 **Всего расходов на ${car.brand} ${car.model}:**\n\n")
        sb.appendLine("Итого: **${ruFmt.format(total)} ₽** ($count записей)\n")
        sb.appendLine("По категориям:")
        byCategory.forEach { (cat, sum) ->
            val pct = if (total > 0) (sum / total * 100).toInt() else 0
            sb.appendLine("  ${cat.emoji} ${cat.displayName}: ${ruFmt.format(sum)} ₽ ($pct%)")
        }
        return sb.toString().trimEnd()
    }

    private suspend fun answerPeakMonth(car: Car?): String {
        if (car == null) return noCar()
        val all = db.expenseDao().getExpensesByCarIdSync(car.id)
        if (all.isEmpty()) return "📊 Расходов нет."

        val monthFmt = java.text.SimpleDateFormat("MMMM yyyy", Locale("ru"))
        val byMonth = all.groupBy { e ->
            val c = Calendar.getInstance().apply { timeInMillis = e.date }
            Pair(c.get(Calendar.YEAR), c.get(Calendar.MONTH))
        }.mapValues { (_, v) -> v.sumOf { it.amount } }

        val peak = byMonth.maxByOrNull { it.value } ?: return "📊 Данных для анализа нет."
        val (year, month) = peak.key
        val cal = Calendar.getInstance().apply { set(year, month, 1) }
        val monthName = monthFmt.format(cal.time)
        val avg = byMonth.values.average()

        return """
📊 **Пиковый месяц расходов ${car.brand} ${car.model}:**

Самый дорогой: **$monthName** — ${ruFmt.format(peak.value)} ₽
Средний месяц: ${ruFmt.format(avg)} ₽
Превышение среднего: +${ruFmt.format(peak.value - avg)} ₽
        """.trimIndent()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun noCar(): String = "🚗 Выберите автомобиль, чтобы получить ответ."

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { this.contains(it) }

    private fun buildProgressBar(pct: Int, length: Int = 10): String {
        val filled = (pct.coerceIn(0, 100) * length / 100)
        return "█".repeat(filled) + "░".repeat(length - filled) + " $pct%"
    }
}

// Extension for display name
private val ExpenseCategory.displayName: String
    get() = when (this) {
        ExpenseCategory.FUEL -> "Топливо"
        ExpenseCategory.MAINTENANCE -> "ТО"
        ExpenseCategory.REPAIR -> "Ремонт"
        ExpenseCategory.INSURANCE -> "Страховка"
        ExpenseCategory.TAX -> "Налоги"
        ExpenseCategory.PARKING -> "Парковка"
        ExpenseCategory.TOLL -> "Платная дорога"
        ExpenseCategory.WASH -> "Мойка"
        ExpenseCategory.FINE -> "Штрафы"
        ExpenseCategory.ACCESSORIES -> "Аксессуары"
        ExpenseCategory.OTHER -> "Прочее"
    }

private val ExpenseCategory.emoji: String
    get() = when (this) {
        ExpenseCategory.FUEL -> "⛽"
        ExpenseCategory.MAINTENANCE -> "🔧"
        ExpenseCategory.REPAIR -> "🛠"
        ExpenseCategory.INSURANCE -> "🛡"
        ExpenseCategory.TAX -> "📑"
        ExpenseCategory.PARKING -> "🅿️"
        ExpenseCategory.TOLL -> "🛣"
        ExpenseCategory.WASH -> "🚿"
        ExpenseCategory.FINE -> "🚨"
        ExpenseCategory.ACCESSORIES -> "🔩"
        ExpenseCategory.OTHER -> "📦"
    }
