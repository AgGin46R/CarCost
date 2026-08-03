package com.aggin.carcost.domain.contribution

import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContributionCalculatorTest {

    private var seq = 0

    private fun expense(amount: Double, author: String?) = Expense(
        id = "e${seq++}",
        carId = "car",
        userId = author,
        category = ExpenseCategory.FUEL,
        amount = amount,
        date = 0L,
        odometer = 1000
    )

    @Test
    fun `доли считаются по авторам, сумма долей равна единице`() {
        val result = ContributionCalculator.calculate(
            listOf(
                expense(3000.0, "anna"),
                expense(1000.0, "boris")
            )
        )

        assertEquals(4000.0, result.total, 0.001)
        assertEquals(2, result.knownContributors)
        assertEquals(1.0, result.contributions.sumOf { it.share }, 0.001)
        assertEquals(0.75, result.contributions.first { it.userId == "anna" }.share, 0.001)
    }

    @Test
    fun `отклонение от равного деления показывает перекос`() {
        val result = ContributionCalculator.calculate(
            listOf(
                expense(3000.0, "anna"),
                expense(1000.0, "boris")
            )
        )

        // Поровну было бы по 2000 с каждого
        assertEquals(1000.0, result.contributions.first { it.userId == "anna" }.deviationFromEqual!!, 0.001)
        assertEquals(-1000.0, result.contributions.first { it.userId == "boris" }.deviationFromEqual!!, 0.001)
    }

    @Test
    fun `при равном вкладе перекоса нет`() {
        val result = ContributionCalculator.calculate(
            listOf(
                expense(2500.0, "anna"),
                expense(2500.0, "boris")
            )
        )

        assertTrue(result.contributions.all { it.deviationFromEqual!! == 0.0 })
    }

    @Test
    fun `записи без автора не растворяются в чужих долях`() {
        val result = ContributionCalculator.calculate(
            listOf(
                expense(1000.0, "anna"),
                expense(1000.0, "boris"),
                expense(500.0, null)      // создано до появления поля автора
            )
        )

        val unknown = result.contributions.single { it.userId == null }
        assertEquals(500.0, unknown.amount, 0.001)
        // Сравнивать эту строку не с чем — отклонения быть не должно
        assertNull(unknown.deviationFromEqual)

        // Равное деление считается по известным авторам: 2000 / 2 = 1000,
        // то есть у обоих перекоса нет, несмотря на «ничьи» 500
        assertEquals(0.0, result.contributions.first { it.userId == "anna" }.deviationFromEqual!!, 0.001)
        assertEquals(2, result.knownContributors)
    }

    @Test
    fun `строка без автора всегда последняя`() {
        val result = ContributionCalculator.calculate(
            listOf(
                expense(10_000.0, null),   // самая большая сумма
                expense(100.0, "anna"),
                expense(200.0, "boris")
            )
        )

        assertNull(result.contributions.last().userId)
    }

    @Test
    fun `одному участнику разбивка не показывается`() {
        val result = ContributionCalculator.calculate(
            listOf(
                expense(1000.0, "anna"),
                expense(2000.0, "anna")
            )
        )

        assertEquals(1, result.knownContributors)
        assertFalse(result.isWorthShowing)
    }

    @Test
    fun `пустой список не ломает расчёт`() {
        val result = ContributionCalculator.calculate(emptyList())

        assertEquals(0.0, result.total, 0.001)
        assertFalse(result.isWorthShowing)
        assertTrue(result.contributions.isEmpty())
    }

    @Test
    fun `когда авторы неизвестны у всех — показывать нечего`() {
        val result = ContributionCalculator.calculate(
            listOf(expense(1000.0, null), expense(2000.0, null))
        )

        assertEquals(0, result.knownContributors)
        assertFalse(result.isWorthShowing)
    }
}
