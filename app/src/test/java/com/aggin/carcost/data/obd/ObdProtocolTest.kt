package com.aggin.carcost.data.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Разбор ответов адаптера.
 *
 * Единственная часть работы с OBD, которую можно проверить без железа — и
 * ровно та, где ошибка незаметна. Сдвиг на один байт не роняет приложение: он
 * превращает восемьдесят пять градусов в правдоподобные двадцать, и человек
 * поверит, потому что цифра выглядит нормально.
 *
 * Ответы в тестах записаны так, как их реально возвращают адаптеры: с эхом
 * команды, пробелами между байтами, переводами строк и приглашением.
 */
class ObdProtocolTest {

    // ── Очистка ответа ──────────────────────────────────────────────────────

    @Test
    fun `эхо, пробелы и приглашение снимаются`() {
        assertEquals("410C1AF8", ObdProtocol.clean("41 0C 1A F8\r\r>"))
    }

    @Test
    fun `поиск протокола данными не считается`() {
        // Пока адаптер ищет протокол, он пишет SEARCHING... — и если принять
        // это за ответ, разбор выдаст мусор вместо пустоты
        assertNull(ObdProtocol.clean("SEARCHING...\r41 0C 1A F8"))
    }

    @Test
    fun `отсутствие данных распознаётся в любом написании`() {
        assertNull(ObdProtocol.clean("NO DATA"))
        assertNull(ObdProtocol.clean("NODATA\r>"))
        assertNull(ObdProtocol.clean("?"))
        assertNull(ObdProtocol.clean("UNABLE TO CONNECT"))
    }

    @Test
    fun `пустой ответ не ломает разбор`() {
        assertNull(ObdProtocol.clean(""))
        assertNull(ObdProtocol.clean("   \r\n>"))
    }

    // ── Параметры ───────────────────────────────────────────────────────────

    @Test
    fun `обороты считаются с делением на четыре`() {
        // 0x1AF8 = 6904, делим на 4 → 1726 об/мин
        assertEquals(1726, ObdProtocol.rpm("41 0C 1A F8"))
    }

    @Test
    fun `обороты холостого хода`() {
        assertEquals(750, ObdProtocol.rpm("410C0BB8"))
    }

    @Test
    fun `скорость читается одним байтом`() {
        assertEquals(90, ObdProtocol.speedKmh("41 0D 5A"))
        assertEquals(0, ObdProtocol.speedKmh("410D00"))
    }

    @Test
    fun `температура двигателя сдвинута на сорок градусов`() {
        // 0x7B = 123, минус 40 → 83 °C, рабочая температура
        assertEquals(83, ObdProtocol.coolantTempC("41 05 7B"))
    }

    @Test
    fun `мороз на улице даёт отрицательную температуру впуска`() {
        // 0x14 = 20, минус 40 → −20 °C. Без смещения было бы +20
        assertEquals(-20, ObdProtocol.intakeTempC("41 0F 14"))
    }

    @Test
    fun `уровень топлива приходит долей`() {
        assertEquals(1f, ObdProtocol.fuelLevel("41 2F FF")!!, 0.001f)
        assertEquals(0f, ObdProtocol.fuelLevel("41 2F 00")!!, 0.001f)
        assertEquals(0.5f, ObdProtocol.fuelLevel("41 2F 80")!!, 0.01f)
    }

    @Test
    fun `напряжение сети в вольтах`() {
        // 0x36B0 = 14000 мВ
        assertEquals(14.0, ObdProtocol.controlModuleVoltage("41 42 36 B0")!!, 0.01)
    }

    @Test
    fun `пробег после сброса ошибок читается, но это не одометр`() {
        // В стандарте OBD-II одометра нет. Этот счётчик обнуляется вместе с
        // ошибками, и подставлять его в пробег машины нельзя
        assertEquals(1234, ObdProtocol.kmSinceCodesCleared("41 31 04 D2"))
    }

    @Test
    fun `ответ не на тот параметр отбрасывается`() {
        // Пришёл ответ про скорость, а спрашивали обороты
        assertNull(ObdProtocol.rpm("41 0D 5A"))
    }

    @Test
    fun `обрезанный ответ не превращается в число`() {
        assertNull(ObdProtocol.rpm("41 0C"))
    }

    @Test
    fun `заголовки CAN перед ответом не мешают`() {
        // При включённых заголовках адаптер шлёт адресацию перед данными
        assertEquals(1726, ObdProtocol.rpm("7E8 04 41 0C 1A F8"))
    }

    // ── Коды неисправностей ─────────────────────────────────────────────────

    @Test
    fun `пропуск зажигания в третьем цилиндре`() {
        // 0x0303 → P0303
        assertEquals(listOf("P0303"), ObdProtocol.parseDtcs("43 03 03"))
    }

    @Test
    fun `несколько кодов в одном ответе`() {
        val codes = ObdProtocol.parseDtcs("43 01 71 04 20 03 00")
        assertEquals(listOf("P0171", "P0420", "P0300"), codes)
    }

    @Test
    fun `нулевые пары — это дополнение ответа, а не код P0000`() {
        // Адаптер добивает ответ до целого кадра нулями. Показать человеку
        // «обнаружена ошибка P0000» — значит напугать пустотой
        assertTrue(ObdProtocol.parseDtcs("43 00 00 00 00").isEmpty())
    }

    @Test
    fun `система кода определяется старшими битами`() {
        // 0x4301 → C0301, 0x8301 → B0301, 0xC301 → U0301
        assertEquals(listOf("C0301"), ObdProtocol.parseDtcs("43 43 01"))
        assertEquals(listOf("B0301"), ObdProtocol.parseDtcs("43 83 01"))
        assertEquals(listOf("U0301"), ObdProtocol.parseDtcs("43 C3 01"))
    }

    @Test
    fun `код производителя отличается второй цифрой`() {
        // 0x1234 → P1234, где 1 означает «код завода, а не стандарта»
        assertEquals(listOf("P1234"), ObdProtocol.parseDtcs("43 12 34"))
    }

    @Test
    fun `повторы в ответе не дублируются`() {
        assertEquals(listOf("P0171"), ObdProtocol.parseDtcs("43 01 71 01 71"))
    }

    @Test
    fun `ошибок нет — список пуст`() {
        assertTrue(ObdProtocol.parseDtcs("NO DATA").isEmpty())
        assertTrue(ObdProtocol.parseDtcs("43").isEmpty())
    }

    // ── VIN ─────────────────────────────────────────────────────────────────

    @Test
    fun `VIN собирается из нескольких кадров`() {
        // Реальный ответ на 0902: три кадра, в первом лидирующие нули
        val raw = """
            49 02 01 00 00 00 31
            49 02 02 47 31 4A 43
            49 02 03 35 34 34 34
            49 02 04 52 37 32 35
            49 02 05 32 33 36 37
        """.trimIndent()
        assertEquals("1G1JC5444R7252367", ObdProtocol.parseVin(raw))
    }

    @Test
    fun `неполный VIN не принимается`() {
        // Семнадцать символов или ничего: обрезанный VIN хуже отсутствующего,
        // с ним не найдётся ни одна запчасть
        assertNull(ObdProtocol.parseVin("49 02 01 00 00 00 31 47 31"))
    }

    @Test
    fun `ответ без VIN даёт пустоту`() {
        assertNull(ObdProtocol.parseVin("NO DATA"))
        assertNull(ObdProtocol.parseVin("41 0C 1A F8"))
    }

    // ── Служебное ──────────────────────────────────────────────────────────

    @Test
    fun `подтверждение команды видно сквозь эхо`() {
        assertTrue(ObdProtocol.isOk("ATE0\rOK\r\r>"))
        assertTrue(ObdProtocol.isOk("OK"))
    }
}
