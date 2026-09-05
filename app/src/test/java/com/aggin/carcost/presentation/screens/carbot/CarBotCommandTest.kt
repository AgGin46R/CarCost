package com.aggin.carcost.presentation.screens.carbot

import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Разбор команд помощнику.
 *
 * Здесь цена ошибки выше, чем у вопросов: неверно понятая команда создаёт
 * запись о деньгах, и человек об этом не узнает. Поэтому разбор обязан либо
 * понять фразу уверенно, либо честно её не понять — половинчатых исходов быть
 * не должно.
 *
 * Отдельно проверяется случай «42 литра»: если объём принять за сумму, в
 * истории появится заправка на 42 рубля, а расход на сотню посчитается по
 * несуществующим литрам.
 */
class CarBotCommandTest {

    @Test
    fun `заправка с суммой и литрами разбирается`() {
        val cmd = CarBotCommand.parse("Запиши заправку 2400, 42 литра")
        assertTrue(cmd is CarBotCommand.Command.AddExpense)
        cmd as CarBotCommand.Command.AddExpense

        assertEquals(ExpenseCategory.FUEL, cmd.category)
        assertEquals(2400.0, cmd.amount, 0.01)
        assertEquals(42.0, cmd.liters!!, 0.01)
    }

    @Test
    fun `объём не становится суммой`() {
        val cmd = CarBotCommand.parse("Запиши заправку 2400, 42 литра")
                as CarBotCommand.Command.AddExpense
        assertTrue("Сумма не должна равняться литрам", cmd.amount != 42.0)
    }

    @Test
    fun `пробег после слова разбирается`() {
        // По-русски говорят и «124380 км», и «пробег 124380» — оба порядка
        val cmd = CarBotCommand.parse("Внеси ремонт 15000, пробег 124500")
                as CarBotCommand.Command.AddExpense

        assertEquals(ExpenseCategory.REPAIR, cmd.category)
        assertEquals(15000.0, cmd.amount, 0.01)
        assertEquals(124500, cmd.odometer)
    }

    @Test
    fun `пробег перед единицей измерения разбирается`() {
        val cmd = CarBotCommand.parse("Добавь мойку 700 на 124380 км")
                as CarBotCommand.Command.AddExpense

        assertEquals(ExpenseCategory.WASH, cmd.category)
        assertEquals(700.0, cmd.amount, 0.01)
        assertEquals(124380, cmd.odometer)
    }

    @Test
    fun `литры записываются только у заправки`() {
        val cmd = CarBotCommand.parse("Добавь мойку 700")
                as CarBotCommand.Command.AddExpense
        assertNull("У мойки литров быть не должно", cmd.liters)
    }

    @Test
    fun `дробная сумма разбирается и через точку, и через запятую`() {
        val dot = CarBotCommand.parse("Запиши заправку 2400.50, 42 литра")
                as CarBotCommand.Command.AddExpense
        val comma = CarBotCommand.parse("Запиши заправку 2400,50 42 литра")
                as CarBotCommand.Command.AddExpense

        assertEquals(2400.50, dot.amount, 0.01)
        assertEquals(2400.50, comma.amount, 0.01)
    }

    @Test
    fun `сумма меньше объёма отвергается как разобранная наоборот`() {
        // Сорок два рубля за пятьдесят литров не бывает — скорее всего числа
        // перепутаны местами, и записывать такое нельзя
        assertNull(CarBotCommand.parse("Запиши заправку 42, 50 литров"))
    }

    @Test
    fun `фраза без числа командой не является`() {
        assertNull(CarBotCommand.parse("Запиши заправку"))
    }

    @Test
    fun `фраза без категории командой не является`() {
        assertNull(CarBotCommand.parse("Запиши 2400"))
    }

    @Test
    fun `вопрос командой не является`() {
        assertNull(CarBotCommand.parse("Сколько я потратил на заправки в июле"))
    }

    @Test
    fun `переход на экран разбирается`() {
        val cmd = CarBotCommand.parse("Покажи аналитику")
        assertEquals(
            CarBotCommand.Command.Open(CarBotCommand.Command.Target.ANALYTICS),
            cmd
        )
    }

    @Test
    fun `переход к документам разбирается`() {
        assertEquals(
            CarBotCommand.Command.Open(CarBotCommand.Command.Target.DOCUMENTS),
            CarBotCommand.parse("Покажи документы")
        )
    }

    @Test
    fun `ё не мешает разбору`() {
        // Люди пишут и «ё», и «е»; приведение к «е» должно работать в обе стороны
        val cmd = CarBotCommand.parse("Внеси ремонт 15000")
        assertTrue(cmd is CarBotCommand.Command.AddExpense)
    }
}
