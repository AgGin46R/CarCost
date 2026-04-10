package com.aggin.carcost.domain.categorization

import com.aggin.carcost.data.local.database.entities.ExpenseCategory

object ExpenseCategoryClassifier {

    private val rules: List<Pair<ExpenseCategory, List<String>>> = listOf(
        ExpenseCategory.FUEL to listOf(
            "заправ", "топлив", "бензин", "дизель", "азс", "газ", "лукойл", "газпром",
            "роснефть", "bp ", "shell", "esso", "neste", "fuel", "petrol", "litr",
            "литр", "92", "95", "98", "е5", "е10"
        ),
        ExpenseCategory.WASH to listOf(
            "мойк", "автомойк", "мойт", "carwash", "wash", "полировк", "химчист"
        ),
        ExpenseCategory.PARKING to listOf(
            "парков", "parking", "паркинг", "стоянк", "платная дорог", "эвакуатор"
        ),
        ExpenseCategory.FINE to listOf(
            "штраф", "гибдд", "fine", "penalty"
        ),
        ExpenseCategory.TOLL to listOf(
            "платн", "toll", "трасс", "м11", "м4", "м3", "м2", "м7", "м8", "м9", "м10"
        ),
        ExpenseCategory.INSURANCE to listOf(
            "страховк", "страхов", "осаго", "каско", "полис", "insurance"
        ),
        ExpenseCategory.TAX to listOf(
            "налог", "транспортн", "tax", "госпошлин", "пошлин"
        ),
        ExpenseCategory.MAINTENANCE to listOf(
            "то ", "техобслуж", "техническ обслуж", "замен масл", "масло", "фильтр",
            "колес", "шин", "тормоз", "сто ", "автосервис", "ремонт", "замен",
            "аккумулятор", "акб", "свеч", "timing", "грм", "service"
        ),
        ExpenseCategory.REPAIR to listOf(
            "ремонт", "repair", "кузов", "покрас", "вмятин", "стекл", "фар"
        ),
        ExpenseCategory.ACCESSORIES to listOf(
            "аксессуар", "коврик", "чехол", "ароматизат", "видеорегистратор",
            "навигатор", "магнитол", "accessories"
        )
    )

    /**
     * Returns a suggested category based on the description text.
     * Returns null if no confident match is found.
     */
    fun classify(description: String): ExpenseCategory? {
        if (description.isBlank()) return null
        val lower = description.trim().lowercase()
        for ((category, keywords) in rules) {
            if (keywords.any { lower.contains(it) }) return category
        }
        return null
    }
}
