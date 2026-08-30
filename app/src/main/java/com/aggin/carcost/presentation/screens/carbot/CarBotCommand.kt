package com.aggin.carcost.presentation.screens.carbot

import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.domain.categorization.ExpenseCategoryClassifier
import java.util.Locale

/**
 * Команды боту: то, что он делает, а не рассказывает.
 *
 * До этого CarBot умел исключительно читать базу. Между «спросить, сколько
 * ушло на заправки» и «записать заправку» лежала форма из семи полей, которую
 * за рулём не заполняют — а именно за рулём заправка и случается.
 *
 * Разбор намеренно скупой: несколько глаголов, число и категория. Он должен
 * либо уверенно понять фразу, либо честно её не понять — команда, понятая
 * наполовину, создаст неверную запись, и человек об этом не узнает.
 */
object CarBotCommand {

    sealed interface Command {
        /** Записать расход. Числа уже разобраны, но запись ещё не сделана */
        data class AddExpense(
            val category: ExpenseCategory,
            val amount: Double,
            val liters: Double?,
            val odometer: Int?
        ) : Command

        /** Открыть экран приложения */
        data class Open(val target: Target) : Command

        /** Куда бот умеет переходить */
        enum class Target { ANALYTICS, EXPENSES, MAINTENANCE, DOCUMENTS, NAVIGATOR, BUDGET, TIMELINE }
    }

    private val ADD_VERBS = listOf("запиши", "запишите", "добавь", "добавьте", "внеси",
        "внесите", "зафиксируй", "отметь")
    private val OPEN_VERBS = listOf("открой", "покажи", "перейди", "открыть", "показать")

    /**
     * @return команда либо null, если фраза командой не является
     */
    fun parse(raw: String): Command? {
        val text = raw.lowercase(Locale("ru")).replace('ё', 'е').trim()

        if (OPEN_VERBS.any { text.startsWith(it) || text.contains(it) }) {
            openTarget(text)?.let { return Command.Open(it) }
        }

        if (ADD_VERBS.none { text.contains(it) }) return null

        val numbers = numbersIn(text)
        if (numbers.isEmpty()) return null

        val category = ExpenseCategoryClassifier.classify(text) ?: return null

        // Литры и пробег узнаются по единице измерения рядом с числом, а сумма —
        // это то, что осталось. Иначе «42 литра» легко становится суммой в 42 ₽.
        val liters = valueBefore(text, "литр", "л ", " л") ?: valueBefore(text, "л.")
        // «124 380 км» — число перед единицей, «пробег 124380» — после слова.
        // По-русски говорят и так, и так, поэтому проверяем оба порядка.
        val odometer = (valueBefore(text, "км") ?: valueAfter(text, "пробег", "одометр"))?.toInt()

        val amount = numbers.firstOrNull { n ->
            n != liters && (odometer == null || n.toInt() != odometer)
        } ?: return null

        // Сумма меньше объёма — почти наверняка разобрали наоборот
        if (liters != null && amount < liters) return null

        return Command.AddExpense(
            category = category,
            amount = amount,
            liters = liters?.takeIf { category == ExpenseCategory.FUEL },
            odometer = odometer
        )
    }

    private fun openTarget(text: String): Command.Target? = when {
        text.contains("аналитик") || text.contains("статистик") || text.contains("график") ->
            Command.Target.ANALYTICS
        text.contains("расход") && !text.contains("топлив") -> Command.Target.EXPENSES
        text.contains("то") && text.contains("напомин") -> Command.Target.MAINTENANCE
        text.contains("обслуживан") || text.contains("напомин") -> Command.Target.MAINTENANCE
        text.contains("документ") || text.contains("страховк") -> Command.Target.DOCUMENTS
        text.contains("навигат") || text.contains("карт") || text.contains("маршрут") ->
            Command.Target.NAVIGATOR
        text.contains("бюджет") -> Command.Target.BUDGET
        text.contains("таймлайн") || text.contains("историю то") -> Command.Target.TIMELINE
        else -> null
    }

    /** Все числа во фразе, включая дробные через точку и запятую */
    private fun numbersIn(text: String): List<Double> =
        Regex("\\d+(?:[.,]\\d+)?").findAll(text)
            .mapNotNull { it.value.replace(',', '.').toDoubleOrNull() }
            .toList()

    /** Число, стоящее сразу после слова: «пробег 124380» */
    private fun valueAfter(text: String, vararg words: String): Double? {
        for (word in words) {
            val idx = text.indexOf(word)
            if (idx < 0) continue
            val after = text.substring(idx + word.length)
            val m = Regex("^\\s*(\\d+(?:[.,]\\d+)?)").find(after) ?: continue
            return m.groupValues[1].replace(',', '.').toDoubleOrNull()
        }
        return null
    }

    /**
     * Число, стоящее перед единицей измерения: «42 литра», «124380 км».
     */
    private fun valueBefore(text: String, vararg units: String): Double? {
        for (unit in units) {
            val idx = text.indexOf(unit)
            if (idx <= 0) continue
            val before = text.substring(0, idx).trimEnd()
            val m = Regex("(\\d+(?:[.,]\\d+)?)\\s*$").find(before) ?: continue
            return m.groupValues[1].replace(',', '.').toDoubleOrNull()
        }
        return null
    }
}
