package com.aggin.carcost.data.local.database.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Подсчёт пробега комплекта шин.
 *
 * Тут легко ошибиться незаметно: пробег текущего периода нигде не хранится, он
 * каждый раз считается от одометра машины. Ошибка в этой арифметике не падает и
 * не подсвечивается — просто показывает человеку неверную цифру, по которой он
 * решает, менять резину или ездить дальше.
 */
class TyreSetTest {

    private fun set(
        totalKm: Int = 0,
        installedAt: Int? = null,
        installed: Boolean = false,
        life: Int? = null
    ) = TyreSet(
        carId = "car",
        name = "Комплект",
        totalKm = totalKm,
        installedAtOdometer = installedAt,
        isInstalled = installed,
        expectedLifeKm = life
    )

    // ── Пробег ──────────────────────────────────────────────────────────────

    @Test
    fun `у снятого комплекта пробег равен накопленному`() {
        assertEquals(30_000, set(totalKm = 30_000).kmWith(currentOdometer = 95_000))
    }

    @Test
    fun `у установленного добавляется пройденное с момента установки`() {
        val tyres = set(totalKm = 20_000, installedAt = 80_000, installed = true)
        assertEquals(25_000, tyres.kmWith(currentOdometer = 85_000))
    }

    @Test
    fun `новый комплект сразу после установки имеет нулевой пробег`() {
        val tyres = set(installedAt = 80_000, installed = true)
        assertEquals(0, tyres.kmWith(currentOdometer = 80_000))
    }

    @Test
    fun `откат одометра назад не уменьшает накопленный пробег`() {
        // Пробег правят задним числом — например, исправляя опечатку в расходе.
        // Отрицательный период вычитал бы из уже пройденного
        val tyres = set(totalKm = 20_000, installedAt = 80_000, installed = true)
        assertEquals(20_000, tyres.kmWith(currentOdometer = 79_000))
    }

    @Test
    fun `сохранившийся одометр установки не учитывается у снятого комплекта`() {
        // isInstalled = false главнее, чем непустой installedAtOdometer:
        // иначе снятый комплект продолжал бы «наматывать» километры
        val tyres = set(totalKm = 20_000, installedAt = 80_000, installed = false)
        assertEquals(20_000, tyres.kmWith(currentOdometer = 95_000))
    }

    // ── Износ ───────────────────────────────────────────────────────────────

    @Test
    fun `без указанного ресурса износ не считается`() {
        assertNull(set(totalKm = 40_000).wearFraction(currentOdometer = 90_000))
    }

    @Test
    fun `износ равен доле от ресурса`() {
        val tyres = set(totalKm = 20_000, life = 50_000)
        assertEquals(0.4f, tyres.wearFraction(currentOdometer = 90_000)!!, 0.001f)
    }

    @Test
    fun `износ выше ресурса не превышает единицы`() {
        val tyres = set(totalKm = 70_000, life = 50_000)
        assertEquals(1f, tyres.wearFraction(currentOdometer = 90_000)!!, 0.001f)
    }

    @Test
    fun `нулевой ресурс не приводит к делению на ноль`() {
        assertNull(set(totalKm = 10_000, life = 0).wearFraction(currentOdometer = 90_000))
    }

    // ── Два периода подряд ──────────────────────────────────────────────────

    @Test
    fun `пробег суммируется за несколько сезонов`() {
        // Первый сезон: поставили на 80 000, сняли на 90 000
        var tyres = set(installedAt = 80_000, installed = true)
        val afterFirst = tyres.copy(
            totalKm = tyres.kmWith(90_000),
            isInstalled = false,
            installedAtOdometer = null
        )
        assertEquals(10_000, afterFirst.totalKm)

        // Между сезонами машина проехала 15 000 на другом комплекте —
        // на снятый они попасть не должны
        assertEquals(10_000, afterFirst.kmWith(105_000))

        // Второй сезон: поставили обратно на 105 000
        tyres = afterFirst.copy(installedAtOdometer = 105_000, isInstalled = true)
        assertEquals(18_000, tyres.kmWith(113_000))
    }
}
