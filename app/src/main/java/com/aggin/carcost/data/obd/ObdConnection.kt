package com.aggin.carcost.data.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Связь с адаптером ELM327 по классическому Bluetooth.
 *
 * **Каждый шаг обмена пишется в журнал целиком.** Это не отладочный мусор,
 * который потом уберут: адаптеров ELM327 в продаже десятки, добрая половина —
 * клоны с собственными представлениями о протоколе, и разобраться, почему
 * молчит конкретный, можно только по стенограмме обмена. Проверить это на всех
 * адаптерах невозможно, поэтому журнал — часть функции.
 *
 * Смотреть: `adb logcat -s ObdConnection`
 */
class ObdConnection {

    companion object {
        private const val TAG = "ObdConnection"

        /** Стандартный UUID последовательного порта. У всех ELM327 он один */
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /**
         * Сколько ждём ответ на команду.
         *
         * Пять секунд — с запасом: сама команда выполняется за десятки
         * миллисекунд, но первая после подключения может ждать, пока адаптер
         * переберёт протоколы связи с машиной.
         */
        private const val REPLY_TIMEOUT_MS = 5_000L

        /** Приглашение, которым адаптер сообщает, что договорил */
        private const val PROMPT = '>'

        /**
         * Команды настройки адаптера.
         *
         * Порядок важен. `ATZ` — сброс, после него адаптер забывает всё;
         * `ATE0` выключает эхо, иначе каждый ответ начинается с самой команды;
         * `ATL0` убирает лишние переводы строк, `ATS0` — пробелы между байтами;
         * `ATSP0` включает автоопределение протокола, чтобы не спрашивать у
         * человека, какая шина в его машине.
         */
        private val INIT_COMMANDS = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATSP0")
    }

    private var socket: BluetoothSocket? = null

    val isConnected: Boolean get() = socket?.isConnected == true

    /**
     * Подключается к адаптеру и настраивает его.
     *
     * @return null при успехе, иначе текст ошибки для показа человеку
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): String? = withContext(Dispatchers.IO) {
        disconnect()
        try {
            Log.d(TAG, "Подключаемся к ${device.address}")
            val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
            s.connect()
            socket = s
            Log.d(TAG, "Сокет открыт")

            INIT_COMMANDS.forEach { command ->
                val reply = send(command)
                Log.d(TAG, "Настройка $command → ${reply?.replace("\r", "\\r") ?: "нет ответа"}")
            }
            null
        } catch (e: IOException) {
            Log.w(TAG, "Не удалось подключиться", e)
            disconnect()
            e.message ?: "IOException"
        } catch (e: SecurityException) {
            // Разрешение могли отозвать между проверкой и подключением
            Log.w(TAG, "Нет разрешения на Bluetooth", e)
            disconnect()
            e.message ?: "SecurityException"
        }
    }

    fun disconnect() {
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Ошибка при закрытии сокета", e)
        }
        socket = null
    }

    /**
     * Отправляет команду и ждёт ответ.
     *
     * @return ответ адаптера без приглашения, либо null — связи нет или
     *   адаптер не ответил за отведённое время
     */
    suspend fun send(command: String): String? = withContext(Dispatchers.IO) {
        val s = socket ?: run {
            Log.w(TAG, "Команда «$command» без подключения")
            return@withContext null
        }

        try {
            val out = s.outputStream
            out.write("$command\r".toByteArray())
            out.flush()

            val reply = withTimeoutOrNull(REPLY_TIMEOUT_MS) { readUntilPrompt(s) }
            if (reply == null) {
                Log.w(TAG, "$command → молчание дольше ${REPLY_TIMEOUT_MS} мс")
            } else {
                Log.d(TAG, "$command → ${reply.replace("\r", "\\r")}")
            }
            reply
        } catch (e: IOException) {
            Log.w(TAG, "Обрыв связи на команде «$command»", e)
            disconnect()
            null
        }
    }

    /**
     * Читает поток до приглашения.
     *
     * Читаем побайтно, а не буфером фиксированного размера: длина ответа
     * заранее неизвестна, а на многокадровых ответах вроде VIN буфер обрезал бы
     * ответ посередине.
     */
    private fun readUntilPrompt(s: BluetoothSocket): String {
        val input = s.inputStream
        val buffer = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte == -1) break
            val ch = byte.toChar()
            if (ch == PROMPT) break
            buffer.append(ch)
        }
        return buffer.toString().trim()
    }

    // ── Готовые запросы ─────────────────────────────────────────────────────

    suspend fun readRpm(): Int? = send("010C")?.let { ObdProtocol.rpm(it) }
    suspend fun readSpeed(): Int? = send("010D")?.let { ObdProtocol.speedKmh(it) }
    suspend fun readCoolantTemp(): Int? = send("0105")?.let { ObdProtocol.coolantTempC(it) }
    suspend fun readIntakeTemp(): Int? = send("010F")?.let { ObdProtocol.intakeTempC(it) }
    suspend fun readFuelLevel(): Float? = send("012F")?.let { ObdProtocol.fuelLevel(it) }
    suspend fun readEngineLoad(): Float? = send("0104")?.let { ObdProtocol.engineLoad(it) }
    suspend fun readVoltage(): Double? = send("0142")?.let { ObdProtocol.controlModuleVoltage(it) }

    suspend fun readVin(): String? = send("0902")?.let { ObdProtocol.parseVin(it) }

    suspend fun readTroubleCodes(): List<String> =
        send("03")?.let { ObdProtocol.parseDtcs(it) } ?: emptyList()

    /**
     * Стирает коды и гасит лампочку.
     *
     * Само по себе стирание ничего не чинит: если причина осталась, код
     * вернётся через несколько поездок. Экран об этом предупреждает — иначе
     * кнопка выглядит как «починить».
     */
    suspend fun clearTroubleCodes(): Boolean {
        val reply = send("04") ?: return false
        // Некоторые адаптеры отвечают «44», некоторые «OK», некоторые обоими
        return ObdProtocol.isOk(reply) || reply.replace(" ", "").contains("44")
    }
}
