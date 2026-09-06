package com.aggin.carcost.data.local.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Окно тихих часов.
 *
 * Почти у всех оно переходит через полночь — 22:00–8:00, — и это ровно тот
 * случай, который ломается при «упрощении» до `hour in start..end`. Ошибка
 * ничем себя не выдаёт: уведомления просто продолжают будить ночью, а по коду
 * настроек видно, что тихие часы «включены».
 */
class QuietHoursTest {

    private fun quiet(hour: Int, start: Int = 22, end: Int = 8) =
        SettingsManager.isWithinQuietWindow(hour, start, end)

    // ── Окно через полночь: 22:00–8:00 ──────────────────────────────────────

    @Test
    fun `ночь внутри окна`() {
        assertTrue(quiet(22))
        assertTrue(quiet(23))
        assertTrue(quiet(0))
        assertTrue(quiet(3))
        assertTrue(quiet(7))
    }

    @Test
    fun `день вне окна`() {
        assertTrue("Полночь должна быть тихой", quiet(0))
        assertFalse(quiet(8))
        assertFalse(quiet(12))
        assertFalse(quiet(21))
    }

    @Test
    fun `час начала входит, час окончания уже нет`() {
        // В 22:00 тишина начинается, в 8:00 заканчивается — иначе окно
        // растянулось бы на час дольше заявленного
        assertTrue(quiet(22))
        assertFalse(quiet(8))
    }

    // ── Обычное окно внутри суток: 1:00–7:00 ────────────────────────────────

    @Test
    fun `окно без перехода через полночь`() {
        assertFalse(quiet(0, start = 1, end = 7))
        assertTrue(quiet(1, start = 1, end = 7))
        assertTrue(quiet(6, start = 1, end = 7))
        assertFalse(quiet(7, start = 1, end = 7))
        assertFalse(quiet(23, start = 1, end = 7))
    }

    // ── Вырожденный случай ──────────────────────────────────────────────────

    @Test
    fun `совпадающие начало и конец тишины не дают`() {
        // Ползунки можно свести в одну точку. «Круглые сутки тишина» здесь
        // было бы неожиданностью: человек сводил их, а не выключал уведомления
        (0..23).forEach { hour ->
            assertFalse("Час $hour", quiet(hour, start = 5, end = 5))
        }
    }
}
