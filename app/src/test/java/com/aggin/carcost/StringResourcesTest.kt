package com.aggin.carcost

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Согласованность строковых ресурсов между четырьмя языками.
 *
 * Повод предельно конкретный. После выноса строк в ресурсы пользователям
 * уехала сборка, где вместо расстояний и расхода показывалось «%.1f км»:
 * знак процента был удвоен там, где строка передаётся в String.format вручную,
 * и подстановка не срабатывала. Ошибка видна на каждом втором экране, но
 * сборка проходила, потому что проверять это было нечем.
 *
 * Здесь проверяется то, что ломается тихо:
 *
 * - потерянный при переводе ключ — надпись молча остаётся русской;
 * - разошедшийся набор подстановок — приложение падает во время работы,
 *   а не при сборке;
 * - неэкранированный апостроф — сборка ресурсов не проходит вовсе.
 */
class StringResourcesTest {

    private val languages = listOf("en", "be", "kk")

    /**
     * Каталог ресурсов.
     *
     * Тесты запускаются с рабочим каталогом модуля, но при запуске из среды
     * разработки им может оказаться корень проекта — проверяем оба, иначе тест
     * падал бы не из-за ошибки в ресурсах, а из-за того, откуда его запустили.
     */
    private val resDir: File = listOf(
        File("src/main/res"),
        File("app/src/main/res")
    ).firstOrNull { it.isDirectory }
        ?: error("Не найден каталог ресурсов")

    private fun strings(qualifier: String): Map<String, String> {
        val file = File(resDir, "values$qualifier/strings.xml")
        val text = file.readText()
        return Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    /** Подстановки вида %1$s, %d, %.2f — без учёта экранированного %% */
    private fun placeholders(value: String): List<String> =
        Regex("""%(\d+\$)?[sdf]""")
            .findAll(value.replace("%%", ""))
            .map { it.value }
            .sorted()
            .toList()

    @Test
    fun `в переводах нет потерянных ключей`() {
        val ru = strings("")
        val missing = mutableListOf<String>()

        for (lang in languages) {
            val translated = strings("-$lang")
            for (key in ru.keys) {
                // app_name — имя продукта, оно не переводится
                if (key == "app_name") continue
                if (key !in translated) missing += "$lang: $key"
            }
        }

        assertTrue(
            "Не переведены строки:\n" + missing.joinToString("\n"),
            missing.isEmpty()
        )
    }

    @Test
    fun `наборы подстановок совпадают во всех языках`() {
        val ru = strings("")
        val broken = mutableListOf<String>()

        for (lang in languages) {
            for ((key, translated) in strings("-$lang")) {
                val original = ru[key] ?: continue
                val expected = placeholders(original)
                val actual = placeholders(translated)
                if (expected != actual) {
                    broken += "$lang: $key — ожидалось $expected, найдено $actual"
                }
            }
        }

        assertTrue(
            "Подстановки разошлись — это падение во время работы:\n" +
                broken.joinToString("\n"),
            broken.isEmpty()
        )
    }

    @Test
    fun `удвоение процента одинаково во всех языках`() {
        // Двойной процент нужен там, где строка проходит форматирование:
        // Android подставляет аргументы через String.format. Если в одном
        // языке он есть, а в другом нет, на одном языке будет число, а на
        // другом — литеральный процент.
        val ru = strings("")
        val broken = mutableListOf<String>()

        for (lang in languages) {
            for ((key, translated) in strings("-$lang")) {
                val original = ru[key] ?: continue
                val a = original.split("%%").size - 1
                val b = translated.split("%%").size - 1
                if (a != b) broken += "$lang: $key — в русском $a, в переводе $b"
            }
        }

        assertTrue("Удвоение процента разошлось:\n" + broken.joinToString("\n"), broken.isEmpty())
    }

    @Test
    fun `апострофы экранированы`() {
        // Неэкранированный апостроф не даёт собрать ресурсы вовсе — ошибка
        // всплывает при сборке и выглядит как «Invalid unicode escape sequence»,
        // что о причине не говорит ничего
        val broken = mutableListOf<String>()

        for (qualifier in listOf("") + languages.map { "-$it" }) {
            for ((key, value) in strings(qualifier)) {
                value.forEachIndexed { i, ch ->
                    if (ch == '\'' && (i == 0 || value[i - 1] != '\\')) {
                        broken += "${qualifier.ifEmpty { "ru" }}: $key"
                        return@forEachIndexed
                    }
                }
            }
        }

        assertTrue(
            "Неэкранированный апостроф ломает сборку ресурсов:\n" + broken.joinToString("\n"),
            broken.isEmpty()
        )
    }
}
