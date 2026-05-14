package com.aggin.carcost.presentation.screens.carbot

import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a concise system prompt for the Gemma model from Room data.
 * Includes: cars, last 15 expenses, upcoming reminders, insurance policies.
 */
class CarContextBuilder {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))
    private val today get() = dateFormat.format(Date())

    suspend fun buildPrompt(carId: String?, db: AppDatabase): String {
        val sb = StringBuilder()
        sb.appendLine("Ты CarBot — умный помощник для владельцев автомобилей.")
        sb.appendLine("Отвечай кратко и по делу на русском языке. Сегодня: $today.")
        sb.appendLine()

        // Cars
        val cars = try { db.carDao().getAllCars().firstOrNull() ?: emptyList() } catch (_: Exception) { emptyList() }
        if (cars.isNotEmpty()) {
            sb.appendLine("=== Автомобили пользователя ===")
            cars.forEach { car ->
                val marker = if (car.id == carId) " [выбранный]" else ""
                sb.appendLine("• ${car.brand} ${car.model} ${car.year}$marker, пробег ${car.currentOdometer} км, гос.номер ${car.licensePlate}")
            }
            sb.appendLine()
        }

        // Expenses for selected car
        val targetCarId = carId ?: cars.firstOrNull()?.id
        if (targetCarId != null) {
            val expenses = try {
                db.expenseDao().getExpensesByCarIdSync(targetCarId).take(15)
            } catch (_: Exception) { emptyList() }

            if (expenses.isNotEmpty()) {
                sb.appendLine("=== Последние расходы (до 15) ===")
                expenses.forEach { e ->
                    val date = dateFormat.format(Date(e.date))
                    val category = categoryName(e.category)
                    sb.appendLine("• $date | $category | ${e.amount.toInt()} ₽" +
                        (if (!e.title.isNullOrBlank()) " | ${e.title}" else ""))
                }
                val total = expenses.sumOf { it.amount }
                sb.appendLine("Сумма последних 15 расходов: ${total.toInt()} ₽")
                sb.appendLine()
            }

            // Maintenance reminders
            val reminders = try {
                db.maintenanceReminderDao().getAllRemindersByCarId(targetCarId).firstOrNull() ?: emptyList()
            } catch (_: Exception) { emptyList() }

            val activeReminders = reminders.filter { it.isActive }
            if (activeReminders.isNotEmpty()) {
                sb.appendLine("=== ТО и напоминания ===")
                val car = cars.find { it.id == targetCarId }
                val currentOdo = car?.currentOdometer ?: 0
                activeReminders.take(5).forEach { r ->
                    val kmLeft = (r.lastChangeOdometer + r.intervalKm) - currentOdo
                    val status = when {
                        kmLeft < 0   -> "ПРОСРОЧЕНО на ${-kmLeft} км"
                        kmLeft < 500 -> "через $kmLeft км (скоро)"
                        else         -> "через $kmLeft км"
                    }
                    sb.appendLine("• ${r.type.displayName}: $status")
                }
                sb.appendLine()
            }

            // Insurance
            val policies = try {
                db.insurancePolicyDao().getPoliciesForCar(targetCarId).firstOrNull() ?: emptyList()
            } catch (_: Exception) { emptyList() }

            val activePolicies = policies.filter { it.endDate > System.currentTimeMillis() }
            if (activePolicies.isNotEmpty()) {
                sb.appendLine("=== Страховки ===")
                activePolicies.take(3).forEach { p ->
                    val daysLeft = ((p.endDate - System.currentTimeMillis()) / 86_400_000).toInt()
                    sb.appendLine("• ${p.type}: истекает через $daysLeft дней")
                }
                sb.appendLine()
            }
        }

        sb.appendLine("Отвечай кратко. Если вопрос не про авто — вежливо переведи тему на автомобильную тематику.")
        return sb.toString()
    }

    private fun categoryName(category: ExpenseCategory) = when (category) {
        ExpenseCategory.FUEL        -> "Топливо"
        ExpenseCategory.MAINTENANCE -> "ТО/Сервис"
        ExpenseCategory.REPAIR      -> "Ремонт"
        ExpenseCategory.INSURANCE   -> "Страховка"
        ExpenseCategory.TAX         -> "Налоги"
        ExpenseCategory.PARKING     -> "Парковка"
        ExpenseCategory.TOLL        -> "Платная дорога"
        ExpenseCategory.WASH        -> "Мойка"
        ExpenseCategory.FINE        -> "Штраф"
        ExpenseCategory.ACCESSORIES -> "Аксессуары"
        ExpenseCategory.OTHER       -> "Другое"
    }
}
