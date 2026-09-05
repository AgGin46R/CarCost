package com.aggin.carcost.presentation.screens.carbot

import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Разбор вопросов к помощнику.
 *
 * До переработки бот сопоставлял вопрос со списком правил по вхождению
 * подстроки, подряд сверху вниз, и ошибался тихо: «сколько потратил на бензин»
 * попадало в правило со словом «потратил» раньше правила про топливо и
 * получало сумму по всем категориям. Ответ выглядел уверенно и был не на тот
 * вопрос — худший вид ошибки.
 *
 * Здесь закреплены случаи, которые ломались, и те, что легко сломать снова.
 */
class CarBotQueryTest {

    private fun parse(text: String, previous: CarBotQuery.Parsed? = null) =
        CarBotQuery.parse(text, previous)

    // ── Что спрашивают ──────────────────────────────────────────────────────

    @Test
    fun `вопрос про траты на бензин относится к топливу, а не ко всем тратам`() {
        val q = parse("Сколько я потратил на бензин?")
        assertEquals(CarBotQuery.Intent.SPENDING, q.intent)
        assertEquals(ExpenseCategory.FUEL, q.category)
    }

    @Test
    fun `что с ТО распознаётся`() {
        // Правило искало « то » с пробелами по краям, и этот вопрос не совпадал
        // ни с чем
        assertEquals(CarBotQuery.Intent.MAINTENANCE, parse("Что с ТО?").intent)
    }

    @Test
    fun `слово то внутри других слов не принимается за техобслуживание`() {
        // «авто», «этой», «который» содержат «то» подстрокой
        assertEquals(CarBotQuery.Intent.CAR_INFO, parse("Информация об авто").intent)
        assertEquals(CarBotQuery.Intent.SPENDING, parse("Сколько потратил").intent)
    }

    @Test
    fun `расход топлива и расход денег различаются`() {
        assertEquals(CarBotQuery.Intent.FUEL_CONSUMPTION, parse("Средний расход топлива").intent)
        assertEquals(CarBotQuery.Intent.FUEL_CONSUMPTION, parse("Сколько ест машина").intent)
        // «Расходы» без уточнения — это деньги
        assertEquals(CarBotQuery.Intent.SPENDING, parse("Расходы за месяц").intent)
        // «Расходы на топливо» — тоже деньги, просто по категории
        val money = parse("Расходы на топливо в прошлом месяце")
        assertEquals(CarBotQuery.Intent.SPENDING, money.intent)
        assertEquals(ExpenseCategory.FUEL, money.category)
    }

    @Test
    fun `вопрос без глагола, но с категорией и сроком, считается вопросом о деньгах`() {
        val q = parse("Сколько на мойку в июле?")
        assertEquals(CarBotQuery.Intent.SPENDING, q.intent)
        assertEquals(ExpenseCategory.WASH, q.category)
        assertNotNull(q.period)
    }

    // ── За какой срок ───────────────────────────────────────────────────────

    @Test
    fun `название месяца разбирается`() {
        val q = parse("Сколько потратил в июле?")
        assertNotNull(q.period)
        val cal = Calendar.getInstance().apply { timeInMillis = q.period!!.start }
        assertEquals(Calendar.JULY, cal.get(Calendar.MONTH))
    }

    @Test
    fun `месяц, который в этом году ещё не наступил, относится к прошлому году`() {
        val now = Calendar.getInstance()
        val futureMonth = (now.get(Calendar.MONTH) + 2) % 12
        val names = listOf("январе", "феврале", "марте", "апреле", "мае", "июне",
            "июле", "августе", "сентябре", "октябре", "ноябре", "декабре")

        val q = parse("Сколько потратил в ${names[futureMonth]}?")
        assertNotNull(q.period)
        val cal = Calendar.getInstance().apply { timeInMillis = q.period!!.start }
        // Если месяц впереди текущего, год должен быть прошлым
        if (futureMonth > now.get(Calendar.MONTH)) {
            assertEquals(now.get(Calendar.YEAR) - 1, cal.get(Calendar.YEAR))
        }
    }

    @Test
    fun `сумма в вопросе не принимается за год`() {
        // Диапазон годов сужен: иначе «2000 ₽» читалось бы как 2000 год
        val q = parse("Сколько потратил 2000")
        assertNull("2000 не должно разбираться как год", q.period)
    }

    @Test
    fun `явный год разбирается`() {
        val q = parse("Расходы в 2024")
        assertNotNull(q.period)
        val cal = Calendar.getInstance().apply { timeInMillis = q.period!!.start }
        assertEquals(2024, cal.get(Calendar.YEAR))
    }

    @Test
    fun `неназванный срок остаётся пустым, а не подменяется молча`() {
        // Раньше нераспознанный срок превращался в текущий месяц без единого
        // признака, что вопрос поняли иначе
        assertNull(parse("Сколько потратил на мойку?").period)
    }

    // ── Продолжение разговора ───────────────────────────────────────────────

    @Test
    fun `уточнение по сроку наследует вопрос и категорию`() {
        val first = parse("Сколько ушло на мойку в июле?")
        val second = parse("А в августе?", first)

        assertEquals(CarBotQuery.Intent.SPENDING, second.intent)
        assertEquals(ExpenseCategory.WASH, second.category)
        val cal = Calendar.getInstance().apply { timeInMillis = second.period!!.start }
        assertEquals(Calendar.AUGUST, cal.get(Calendar.MONTH))
    }

    @Test
    fun `уточнение по категории наследует вопрос и срок`() {
        val first = parse("Сколько ушло на мойку в июле?")
        val second = parse("А на топливо?", first)

        assertEquals(CarBotQuery.Intent.SPENDING, second.intent)
        assertEquals(ExpenseCategory.FUEL, second.category)
        assertEquals(first.period!!.start, second.period!!.start)
    }

    @Test
    fun `полноценный вопрос не наследует ничего от предыдущего`() {
        val first = parse("Сколько ушло на мойку в июле?")
        val second = parse("Что с ТО?", first)

        assertEquals(CarBotQuery.Intent.MAINTENANCE, second.intent)
        // Категория у нового вопроса своя — «ТО» распознаётся как обслуживание.
        // Проверяем не отсутствие категории, а именно то, что не протекла
        // прежняя: иначе ответ был бы про мойку.
        assertTrue(
            "Категория предыдущего вопроса не должна протекать",
            second.category != ExpenseCategory.WASH
        )
    }

    @Test
    fun `непонятная фраза остаётся непонятой`() {
        assertEquals(CarBotQuery.Intent.UNKNOWN, parse("Как дела?").intent)
    }
}
