package com.aggin.carcost.data.backup

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Резервная копия должна охватывать все данные человека.
 *
 * Проверка появилась потому, что копия уже разошлась с базой молча: шины
 * завели в сентябре, в копию добавить забыли, и восстановление возвращало
 * автомобиль без комплектов и без их пробега. Заметить это можно было только
 * зная, что искать, — ошибки не было, просто данных не стало.
 *
 * Тест читает список сущностей прямо из `AppDatabase.kt` и сверяет его с
 * полями [CarCostBackup]. Новая сущность, не попавшая ни в копию, ни в список
 * исключений ниже, здесь и остановится.
 */
class BackupCoverageTest {

    /**
     * Сущности, которых в копии нет намеренно.
     *
     * Каждая — с причиной. Причина обязательна: без неё список превратится в
     * место, куда сваливают всё, что не хочется добавлять.
     */
    private val excluded = mapOf(
        "User" to "профиль приходит с сервера при входе, а не из файла",
        "CarMember" to "состав участников живёт на сервере; восстановление из файла " +
            "вернуло бы доступы, которые владелец уже отозвал",
        "ChatMessage" to "переписка синхронизируется с сервером и восстанавливается оттуда",
        "ChatReaction" to "то же, что и переписка",
        "PendingWrite" to "очередь неотправленного: к моменту восстановления она " +
            "бессмысленна, записи в ней ссылаются на прошлое состояние"
    )

    private fun repoFile(relative: String): File =
        listOf(File(relative), File("app/$relative"))
            .firstOrNull { it.exists() }
            ?: error("Не найден $relative — тест запущен не из корня проекта?")

    /** Сущности из аннотации @Database */
    private fun entitiesInDatabase(): Set<String> {
        val source = repoFile("src/main/java/com/aggin/carcost/data/local/database/AppDatabase.kt")
            .readText()
        val block = source.substringAfter("entities = [").substringBefore("]")
        return Regex("(\\w+)::class").findAll(block).map { it.groupValues[1] }.toSet()
    }

    /** Типы, перечисленные в полях CarCostBackup */
    private fun entitiesInBackup(): Set<String> {
        val source = repoFile("src/main/java/com/aggin/carcost/data/backup/BackupService.kt")
            .readText()
        val block = source.substringAfter("data class CarCostBackup(").substringBefore("\n) {")
        return Regex("List<(\\w+)>").findAll(block).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun `в копию входит всё, кроме явно исключённого`() {
        val inDb = entitiesInDatabase()
        val inBackup = entitiesInBackup()

        assertTrue("Не разобрали список сущностей базы", inDb.size > 15)
        assertTrue("Не разобрали состав копии", inBackup.size > 10)

        val missing = inDb - inBackup - excluded.keys
        assertEquals(
            "Эти сущности не попадают ни в резервную копию, ни в список исключений. " +
                "Добавьте их в CarCostBackup — или в excluded, с причиной: $missing",
            emptySet<String>(),
            missing
        )
    }

    @Test
    fun `в исключениях нет того, чего в базе уже нет`() {
        // Список исключений должен стареть вместе с базой, а не превращаться в
        // кладбище имён, по которым непонятно, было ли такое вообще
        val stale = excluded.keys - entitiesInDatabase()
        assertEquals("Исключения ссылаются на несуществующие сущности: $stale",
            emptySet<String>(), stale)
    }

    @Test
    fun `у каждого исключения есть причина`() {
        excluded.forEach { (name, reason) ->
            assertTrue("Пустая причина у $name", reason.length > 20)
        }
    }

    @Test
    fun `версия формата поднята вместе с составом`() {
        // Первая версия не знала о шинах, поездках, тегах, достижениях и
        // местах. Читать её всё ещё можно — новые поля со значением по
        // умолчанию, — но номер обязан отличаться, иначе два разных формата
        // называются одинаково
        assertTrue(
            "FORMAT_VERSION = ${CarCostBackup.FORMAT_VERSION}, а состав уже шире первой версии",
            CarCostBackup.FORMAT_VERSION >= 2
        )
    }
}
