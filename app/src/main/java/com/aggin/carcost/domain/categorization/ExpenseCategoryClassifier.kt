package com.aggin.carcost.domain.categorization

import com.aggin.carcost.data.local.database.entities.ExpenseCategory

object ExpenseCategoryClassifier {

    // Rules are evaluated in order; the first match wins.
    // More specific/longer keywords should come before generic ones.
    private val rules: List<Pair<ExpenseCategory, List<String>>> = listOf(
        ExpenseCategory.FUEL to listOf(
            "заправ", "топлив", "бензин", "дизель", "солярк", "азс", "азс №",
            "лукойл", "газпромнефть", "газпром", "роснефть", "татнефть", "башнефть",
            "bp ", "shell", "neste", "ека", "g-drive", "птк", "трасс",
            "fuel", "petrol", "diesel", "gasoline",
            "литр топл", "л топл", "литры", "заправил", "залил",
            "аи-95", "аи-92", "аи-98", "аи-100", "аи95", "аи92", "аи98",
            "92-й", "95-й", "98-й",
            "е5", "е10"
        ),
        ExpenseCategory.WASH to listOf(
            "автомойк", "мойк", "мойт", "carwash", "автомойк",
            "полировк", "химчист", "нанокерамик", "покрыт"
        ),
        ExpenseCategory.PARKING to listOf(
            "парков", "паркинг", "стоянк",
            "parking", "эвакуатор", "платная стоянк"
        ),
        ExpenseCategory.FINE to listOf(
            "штраф", "гибдд", "гаи", "fine", "penalty", "штрафстоянк"
        ),
        ExpenseCategory.TOLL to listOf(
            "платная дорог", "платн трасс", "toll", "плата проезд",
            "м11", "м4", "м3", "м2", "м7", "м8", "м9", "м10",
            "автодор", "платный участок"
        ),
        ExpenseCategory.INSURANCE to listOf(
            "страховк", "страхов", "осаго", "каско", "полис",
            "insurance", "страх полис", "продление полис"
        ),
        ExpenseCategory.TAX to listOf(
            "транспортный налог", "налог на авто", "налог", "tax",
            "госпошлин", "пошлин", "утилизацион", "регистрация авто"
        ),
        // MAINTENANCE before REPAIR so "техобслуживание" doesn't fall through to repair
        ExpenseCategory.MAINTENANCE to listOf(
            "техобслуж", "техническ обслуж", "плановое то", "регламентное",
            "замен масл", "замена масл", "замена фильтр",
            "масляный фильтр", "воздушный фильтр", "салонный фильтр", "топливный фильтр",
            "масло мотор", "моторное масло", "масло 5w", "масло 0w", "масло 10w",
            "колодк", "тормозн", "тормозная жидкость", "тормозна жидк",
            "охлаждающ", "антифриз", "тосол",
            "свеч зажигания", "свечи", "свеча",
            "аккумулятор", "акб", "батарея аккум",
            "ремень грм", "грм", "timing belt",
            "шин", "колес", "шиномонтаж", "сезонная замена",
            "развал", "схождение", "балансировк",
            "техосмотр", "то ", " сто ", "автосервис", "service"
        ),
        ExpenseCategory.REPAIR to listOf(
            "ремонт", "repair", "кузов", "покрас",
            "вмятин", "царапин", "стекл", "лобов стекл",
            "фар", "бампер", "крыл", "дверь", "капот",
            "подвеск", "рулев", "трансмисс", "коробк", "двигател",
            "сварк", "рихтовк", "детейлинг"
        ),
        ExpenseCategory.ACCESSORIES to listOf(
            "аксессуар", "коврик", "чехол", "ароматизат",
            "видеорегистратор", "видеорегистр",
            "навигатор", "магнитол", "колонк",
            "accessories", "сигнализац", "охранная система",
            "фаркоп", "автомагазин", "запчаст"
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
