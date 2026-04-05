package com.aggin.carcost.domain.ai

import com.aggin.carcost.data.local.database.entities.AiInsight
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.database.entities.InsightSeverity
import com.aggin.carcost.data.local.database.entities.InsightType
import java.util.UUID

/**
 * Generates saving tips and optimization advice based on expense patterns.
 */
object SmartTipsEngine {

    fun generateTips(carId: String, expenses: List<Expense>): List<AiInsight> {
        if (expenses.isEmpty()) return emptyList()

        val tips = mutableListOf<AiInsight>()
        tips += checkFuelPriceOptimization(carId, expenses)
        tips += checkWashFrequency(carId, expenses)
        tips += checkInsuranceReminder(carId, expenses)
        tips += checkParkingCosts(carId, expenses)
        return tips
    }

    private fun checkFuelPriceOptimization(carId: String, expenses: List<Expense>): List<AiInsight> {
        val fuelExpenses = expenses.filter {
            it.category == ExpenseCategory.FUEL && it.fuelLiters != null && it.fuelLiters > 0
        }
        if (fuelExpenses.size < 5) return emptyList()

        val pricesPerLiter = fuelExpenses.mapNotNull { e ->
            if (e.fuelLiters != null && e.fuelLiters > 0) e.amount / e.fuelLiters else null
        }
        if (pricesPerLiter.size < 3) return emptyList()

        val avg = pricesPerLiter.average()
        val min = pricesPerLiter.min()
        val potentialSavingPct = ((avg - min) / avg * 100).toInt()

        if (potentialSavingPct >= 5) {
            return listOf(
                AiInsight(
                    id = UUID.randomUUID().toString(),
                    carId = carId,
                    type = InsightType.SAVINGS_OPPORTUNITY,
                    title = "Экономия на топливе",
                    body = "Разница между лучшей и средней ценой топлива составляет $potentialSavingPct%. Заправляясь на более выгодных АЗС, вы можете сэкономить ${((avg - min) * 40).toInt()} руб. в месяц (при баке 40 л).",
                    severity = InsightSeverity.INFO
                )
            )
        }
        return emptyList()
    }

    private fun checkWashFrequency(carId: String, expenses: List<Expense>): List<AiInsight> {
        val washExpenses = expenses.filter { it.category == ExpenseCategory.WASH }
        if (washExpenses.size < 3) return emptyList()

        val totalWashCost = washExpenses.sumOf { it.amount }
        val monthsSpan = ((System.currentTimeMillis() - (expenses.minOfOrNull { it.date } ?: 0L)) / (30L * 86_400_000L)).toInt().coerceAtLeast(1)
        val monthlyWashCost = totalWashCost / monthsSpan

        if (monthlyWashCost > 2000) {
            return listOf(
                AiInsight(
                    id = UUID.randomUUID().toString(),
                    carId = carId,
                    type = InsightType.SAVINGS_OPPORTUNITY,
                    title = "Расходы на мойку",
                    body = "Вы тратите около ${monthlyWashCost.toInt()} руб./мес. на мойку. Рассмотрите абонемент или самомойку — это может сократить расходы в 2 раза.",
                    severity = InsightSeverity.INFO
                )
            )
        }
        return emptyList()
    }

    private fun checkInsuranceReminder(carId: String, expenses: List<Expense>): List<AiInsight> {
        val insuranceExpenses = expenses
            .filter { it.category == ExpenseCategory.INSURANCE }
            .sortedByDescending { it.date }

        val lastInsurance = insuranceExpenses.firstOrNull() ?: return emptyList()
        val daysSince = (System.currentTimeMillis() - lastInsurance.date) / 86_400_000L

        // ОСАГО usually expires annually
        if (daysSince in 330L..365L) {
            return listOf(
                AiInsight(
                    id = UUID.randomUUID().toString(),
                    carId = carId,
                    type = InsightType.GENERAL,
                    title = "Скоро истекает страховка",
                    body = "Последняя страховка была оформлена ${daysSince} дней назад. Скоро потребуется продление — сравните предложения заранее для лучшей цены.",
                    severity = InsightSeverity.WARNING
                )
            )
        }
        return emptyList()
    }

    private fun checkParkingCosts(carId: String, expenses: List<Expense>): List<AiInsight> {
        val parkingExpenses = expenses.filter { it.category == ExpenseCategory.PARKING }
        if (parkingExpenses.size < 5) return emptyList()

        val totalParking = parkingExpenses.sumOf { it.amount }
        val allTotal = expenses.sumOf { it.amount }
        if (allTotal == 0.0) return emptyList()

        val parkingShare = (totalParking / allTotal * 100).toInt()
        if (parkingShare > 15) {
            return listOf(
                AiInsight(
                    id = UUID.randomUUID().toString(),
                    carId = carId,
                    type = InsightType.SAVINGS_OPPORTUNITY,
                    title = "Высокие расходы на парковку",
                    body = "Парковка составляет $parkingShare% всех расходов. Рассмотрите сезонный абонемент или альтернативные варианты.",
                    severity = InsightSeverity.INFO
                )
            )
        }
        return emptyList()
    }
}
