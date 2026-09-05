package com.aggin.carcost.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

/**
 * Разбор даты, набранной цифрами.
 *
 * Разбор обязан быть строгим. Раньше неразобранная дата полиса молча
 * заменялась сегодняшней: человек вводил «31.02», сохранял и получал страховку,
 * начинающуюся сегодня, — узнать об этом было неоткуда, кроме как через год.
 *
 * Поэтому несуществующая дата возвращает null, а вызывающий обязан показать
 * ошибку вместо того, чтобы подставлять что-то своё.
 */
class DateFieldTest {

    private fun parts(millis: Long): Triple<Int, Int, Int> {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return Triple(
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.YEAR)
        )
    }

    @Test
    fun `восемь цифр разбираются в дату`() {
        val millis = dateDigitsToMillis("31122026")
        assertNotNull(millis)
        assertEquals(Triple(31, 12, 2026), parts(millis!!))
    }

    @Test
    fun `несуществующая дата не разбирается`() {
        // Тридцать первое февраля при нестрогом разборе превращается в третье
        // марта — и запись уходит с датой, которую человек не вводил
        assertNull(dateDigitsToMillis("31022026"))
    }

    @Test
    fun `тринадцатый месяц не разбирается`() {
        assertNull(dateDigitsToMillis("01132026"))
    }

    @Test
    fun `нулевой день не разбирается`() {
        assertNull(dateDigitsToMillis("00012026"))
    }

    @Test
    fun `неполный ввод не разбирается`() {
        assertNull(dateDigitsToMillis(""))
        assertNull(dateDigitsToMillis("3112"))
        assertNull(dateDigitsToMillis("3112202"))
    }

    @Test
    fun `високосный год учитывается`() {
        assertNotNull("29 февраля 2024 существует", dateDigitsToMillis("29022024"))
        assertNull("29 февраля 2025 не существует", dateDigitsToMillis("29022025"))
    }

    @Test
    fun `время ставится на полдень`() {
        // У полуночи при переводе часов дата иногда съезжает на сутки назад
        val millis = dateDigitsToMillis("15062026")!!
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        assertEquals(12, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `обратное преобразование возвращает те же цифры`() {
        val millis = dateDigitsToMillis("01012025")!!
        assertEquals("01012025", millisToDateDigits(millis))
    }
}
