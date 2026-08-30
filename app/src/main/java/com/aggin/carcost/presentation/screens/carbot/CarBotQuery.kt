package com.aggin.carcost.presentation.screens.carbot

import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.domain.categorization.ExpenseCategoryClassifier
import java.util.Calendar
import java.util.Locale

/**
 * Разбор вопроса к боту.
 *
 * До этого вопрос сопоставлялся со списком из двенадцати правил по вхождению
 * подстроки, подряд сверху вниз. Такой разбор ошибается тихо и уверенно:
 *
 * - «Сколько я потратил на бензин?» попадало в правило со словом «потратил»
 *   раньше, чем в правило про топливо, и человек получал общую сумму по всем
 *   категориям вместо ответа про бензин;
 * - «Что с ТО?» не совпадало ни с чем, потому что правило искало « то » с
 *   пробелами по краям;
 * - «Сколько на мойку в июле?» отвечало за текущий месяц и по всем категориям
 *   сразу: периодов было ровно три, а категория не учитывалась вовсе.
 *
 * Здесь вопрос разбирается на три независимые части — о чём спрашивают, за
 * какой срок и по какой категории. Одно это покрывает сотни формулировок
 * вместо двенадцати, а главное — перестаёт отвечать не на тот вопрос.
 */
object CarBotQuery {

    /** О чём спрашивают */
    enum class Intent {
        HELP,
        SPENDING,          // деньги
        FUEL_CONSUMPTION,  // литры на сотню
        MAINTENANCE,       // ТО и замены
        INSURANCE,
        TRIPS,
        BUDGET,
        CAR_INFO,
        RECENT,
        PEAK_MONTH,
        TOTAL,
        UNKNOWN
    }

    /** Отрезок времени вместе с подписью для ответа */
    data class Period(val label: String, val start: Long, val end: Long)

    data class Parsed(
        val intent: Intent,
        val period: Period?,
        val category: ExpenseCategory?
    )

    private val MONTHS = listOf(
        "январ" to 0, "феврал" to 1, "март" to 2, "апрел" to 3,
        "мая" to 4, "май" to 4, "июн" to 5, "июл" to 6, "август" to 7,
        "сентябр" to 8, "октябр" to 9, "ноябр" to 10, "декабр" to 11
    )

    private val MONTH_NAMES = listOf(
        "январе", "феврале", "марте", "апреле", "мае", "июне",
        "июле", "августе", "сентябре", "октябре", "ноябре", "декабре"
    )

    fun parse(raw: String): Parsed {
        val text = raw.lowercase(Locale("ru")).replace('ё', 'е')
        val period = detectPeriod(text)
        val category = detectCategory(text)
        var intent = detectIntent(text)

        // «Сколько на мойку в июле?» — вопрос о деньгах, хотя ни одного слова
        // про траты в нём нет. Когда назван срок или категория и спрашивают
        // «сколько», речь почти наверняка о сумме: других величин, которые
        // считают по категории за месяц, в приложении нет.
        if (intent == Intent.UNKNOWN &&
            (category != null || period != null) &&
            text.hasAny("сколько", "почем", "во что обош")
        ) {
            intent = Intent.SPENDING
        }

        return Parsed(intent = intent, period = period, category = category)
    }

    // ── О чём спрашивают ─────────────────────────────────────────────────────

    /**
     * Порядок здесь значим: сначала то, что определяется однозначно.
     *
     * «Расход» — слово с двумя смыслами: расход денег и расход топлива. Поэтому
     * топливный смысл проверяется первым и только вместе с уточнением («расход
     * топлива», «л/100», «сколько ест»), а голое «расход» остаётся деньгами.
     */
    private fun detectIntent(text: String): Intent = when {
        text.hasAny("помощь", "что умеешь", "что ты умеешь", "помоги", "help", "команды") ->
            Intent.HELP

        text.hasAny("расход топлив", "расхода топлив", "л/100", "л на 100",
            "литров на 100", "сколько ест", "средний расход", "потребление топлив",
            "аппетит") -> Intent.FUEL_CONSUMPTION

        text.hasAny("страховк", "страхов", "осаго", "каско", "полис") ->
            Intent.INSURANCE

        text.hasAny("бюджет", "лимит", "превыс") ->
            Intent.BUDGET

        text.hasAny("поездк", "маршрут", "километраж", "дистанц", "gps", "проехал") ->
            Intent.TRIPS

        // «то» как техобслуживание: отдельным словом, а не подстрокой — иначе
        // оно ловилось бы внутри «авто», «этой», «который»
        text.hasAny("масло", "масла", "обслуживан", "регламент", "замен",
            "свеч", "фильтр", "колодк", "ремень", "техосмотр") ||
            text.hasWord("то", "тэо") -> Intent.MAINTENANCE

        text.hasAny("самый дорогой", "дорогой месяц", "пик расход", "больше всего потратил") ->
            Intent.PEAK_MONTH

        text.hasAny("за все время", "за всю", "всего потратил", "общая сумма", "итого", "суммарно") ->
            Intent.TOTAL

        text.hasAny("последние расход", "последних", "история расход", "что добавлял",
            "недавние") -> Intent.RECENT

        text.hasAny("потрат", "трач", "затрат", "расход", "сколько ушло", "сколько стоил",
            "во сколько обош", "сколько денег") -> Intent.SPENDING

        text.hasAny("пробег", "какая машина", "инфо", "информац", "об авто", "о машине",
            "характеристик") -> Intent.CAR_INFO

        else -> Intent.UNKNOWN
    }

    // ── За какой срок ────────────────────────────────────────────────────────

    /**
     * Возвращает null, когда срок не назван.
     *
     * Это важнее, чем кажется: раньше нераспознанный срок молча превращался в
     * текущий месяц, и на вопрос про июль приходил ответ про август — без
     * единого признака, что вопрос поняли иначе. Пусть лучше вызывающий сам
     * решит, что подставить, и скажет об этом в ответе.
     */
    private fun detectPeriod(text: String): Period? {
        val now = System.currentTimeMillis()

        // Явный год: «в 2025», «за 2024». Диапазон сужен до 2010–2039:
        // иначе сумма «2000 ₽» в вопросе читалась бы как год.
        Regex("\\b(20[1-3]\\d)\\b").find(text)?.let { m ->
            val year = m.groupValues[1].toInt()
            return Period("в $year году", startOfYear(year), endOfYear(year))
        }

        // Название месяца: «в июле», «за март»
        MONTHS.firstOrNull { (stem, _) -> text.contains(stem) }?.let { (_, monthIndex) ->
            val cal = Calendar.getInstance()
            var year = cal.get(Calendar.YEAR)
            // Месяц, который в этом году ещё не наступил, — это прошлый год
            if (monthIndex > cal.get(Calendar.MONTH)) year -= 1
            return Period(
                "в ${MONTH_NAMES[monthIndex]} $year",
                startOfMonth(year, monthIndex),
                endOfMonth(year, monthIndex)
            )
        }

        return when {
            text.hasAny("сегодня") -> Period("сегодня", startOfDay(0), now)
            text.hasAny("вчера") -> Period("вчера", startOfDay(-1), startOfDay(0) - 1)
            text.hasAny("за неделю", "неделя", "за 7 дней", "недел") ->
                Period("за неделю", now - 7L * 86_400_000, now)
            text.hasAny("прошлый месяц", "прошлом месяц", "прошедший месяц") -> {
                val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                val y = cal.get(Calendar.YEAR)
                val m = cal.get(Calendar.MONTH)
                Period("в ${MONTH_NAMES[m]}", startOfMonth(y, m), endOfMonth(y, m))
            }
            text.hasAny("прошлый год", "прошлом году") -> {
                val y = Calendar.getInstance().get(Calendar.YEAR) - 1
                Period("в $y году", startOfYear(y), endOfYear(y))
            }
            text.hasAny("за год", "в этом году", "этот год") -> {
                val y = Calendar.getInstance().get(Calendar.YEAR)
                Period("в этом году", startOfYear(y), now)
            }
            text.hasAny("за месяц", "в этом месяце", "этот месяц", "текущий месяц") -> {
                val cal = Calendar.getInstance()
                Period("в этом месяце", startOfMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)), now)
            }
            text.hasAny("за все время", "за всю историю", "всего", "суммарно") ->
                Period("за всё время", 0L, now)
            else -> null
        }
    }

    /** Текущий месяц — то, что подставляется, когда срок не назван */
    fun currentMonth(): Period {
        val cal = Calendar.getInstance()
        return Period(
            "в этом месяце",
            startOfMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)),
            System.currentTimeMillis()
        )
    }

    // ── По какой категории ───────────────────────────────────────────────────

    /**
     * Категория ищется той же таблицей синонимов, по которой она определяется
     * из описания расхода. Своей копии здесь нет намеренно: две таблицы
     * синонимов неизбежно разойдутся, и бот начнёт понимать не то же самое,
     * что понимает форма ввода.
     */
    private fun detectCategory(text: String): ExpenseCategory? =
        ExpenseCategoryClassifier.classify(text)

    // ── Мелочи ───────────────────────────────────────────────────────────────

    private fun String.hasAny(vararg keys: String) = keys.any { contains(it) }

    /** Совпадение целым словом: «то» не должно находиться внутри «авто» */
    private fun String.hasWord(vararg words: String) = words.any { word ->
        Regex("(^|[^а-яa-z0-9])$word([^а-яa-z0-9]|$)").containsMatchIn(this)
    }

    private fun startOfDay(offsetDays: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, offsetDays)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfMonth(year: Int, month: Int): Long = Calendar.getInstance().apply {
        set(year, month, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun endOfMonth(year: Int, month: Int): Long = Calendar.getInstance().apply {
        set(year, month, 1, 23, 59, 59); set(Calendar.MILLISECOND, 999)
        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
    }.timeInMillis

    private fun startOfYear(year: Int): Long = Calendar.getInstance().apply {
        set(year, 0, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun endOfYear(year: Int): Long = Calendar.getInstance().apply {
        set(year, 11, 31, 23, 59, 59); set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}
