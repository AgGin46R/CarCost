package com.aggin.carcost.data.obd

/**
 * Разбор ответов ELM327 и стандартных параметров OBD-II.
 *
 * Вынесено отдельно от связи по Bluetooth намеренно: это единственная часть
 * работы с адаптером, которую можно проверить без железа. Формулы пересчёта
 * взяты из SAE J1979 и ошибиться в них легко — сдвиг на байт превращает
 * температуру двигателя в правдоподобную, но неверную цифру.
 *
 * **Чего здесь принципиально нет — одометра.** В стандарте OBD-II параметра
 * пробега не существует. Производители отдают его собственными командами,
 * у каждого своими, и универсально прочитать его нельзя. Любая библиотека,
 * обещающая «пробег по OBD», либо знает конкретную марку, либо считает его
 * сама по скорости — то есть выдумывает.
 */
object ObdProtocol {

    /** Ответ адаптера, из которого ничего не извлечь */
    private val NON_DATA_ANSWERS = listOf(
        "NODATA", "STOPPED", "UNABLETOCONNECT", "BUSINIT", "BUSERROR",
        "CANERROR", "ERROR", "?", "SEARCHING"
    )

    /**
     * Приводит сырой ответ к виду «сплошные шестнадцатеричные пары».
     *
     * Адаптер отвечает как придётся: с эхом команды, переводами строк,
     * приглашением `>`, пробелами между байтами и словом `SEARCHING...`,
     * пока ищет протокол. Всё это нужно снять до разбора, иначе первая же
     * пара окажется не тем байтом.
     *
     * @return только шестнадцатеричные символы в верхнем регистре, либо null,
     *   если данных в ответе нет
     */
    fun clean(raw: String): String? {
        if (raw.isBlank()) return null

        val compact = raw
            .replace(">", " ")
            .replace("\r", " ")
            .replace("\n", " ")
            .uppercase()

        // Служебные ответы проверяем по строке без пробелов: «NO DATA» и
        // «NODATA» — один и тот же ответ, а разные адаптеры пишут по-разному
        val squeezed = compact.replace(" ", "")
        if (NON_DATA_ANSWERS.any { squeezed.contains(it) }) return null

        // Чётной длины не требуем: при включённых заголовках CAN перед
        // данными идёт трёхсимвольный адрес вроде «7E8», и требование чётности
        // отбрасывало бы совершенно нормальный ответ целиком. Выравнивание по
        // байтам делает разбор — от найденного заголовка, а не от начала строки
        val hex = squeezed.filter { it in "0123456789ABCDEF" }
        return hex.takeIf { it.length >= 2 }
    }

    /**
     * Байты полезной части ответа на запрос параметра.
     *
     * Ответ начинается с эха режима, увеличенного на 0x40, и номера параметра:
     * на запрос `010C` приходит `410C1AF8`. Заголовок нужно снять, иначе
     * обороты будут считаться от кода режима.
     *
     * @param mode режим запроса, например 0x01
     * @param pid номер параметра, например 0x0C
     *
     * @return байты после заголовка, либо null если заголовок не тот
     */
    fun payload(raw: String, mode: Int, pid: Int): List<Int>? {
        val hex = clean(raw) ?: return null
        val header = "%02X%02X".format(mode + 0x40, pid)

        // Заголовок может быть не в начале: перед ним встречаются байты
        // адресации CAN, если у адаптера включены заголовки
        val at = hex.indexOf(header)
        if (at < 0) return null

        val body = hex.substring(at + header.length)
        if (body.length < 2) return null

        return body.chunked(2)
            .mapNotNull { it.toIntOrNull(16) }
            .takeIf { it.isNotEmpty() }
    }

    // ── Параметры ───────────────────────────────────────────────────────────

    /** Обороты двигателя, об/мин. `((A*256)+B)/4` */
    fun rpm(raw: String): Int? {
        val b = payload(raw, 0x01, 0x0C) ?: return null
        if (b.size < 2) return null
        return (b[0] * 256 + b[1]) / 4
    }

    /** Скорость, км/ч. Один байт, без пересчёта */
    fun speedKmh(raw: String): Int? =
        payload(raw, 0x01, 0x0D)?.firstOrNull()

    /**
     * Температура охлаждающей жидкости, °C. `A - 40`.
     *
     * Смещение в сорок градусов — не описка: без него холодный двигатель
     * показывал бы сорок градусов вместо нуля, а минусовые значения были бы
     * непредставимы.
     */
    fun coolantTempC(raw: String): Int? =
        payload(raw, 0x01, 0x05)?.firstOrNull()?.let { it - 40 }

    /** Температура воздуха на впуске, °C. Та же шкала, что и у охлаждающей */
    fun intakeTempC(raw: String): Int? =
        payload(raw, 0x01, 0x0F)?.firstOrNull()?.let { it - 40 }

    /** Уровень топлива в баке, доля от 0 до 1. `A*100/255` процентов */
    fun fuelLevel(raw: String): Float? =
        payload(raw, 0x01, 0x2F)?.firstOrNull()?.let { (it / 255f).coerceIn(0f, 1f) }

    /** Нагрузка на двигатель, доля от 0 до 1 */
    fun engineLoad(raw: String): Float? =
        payload(raw, 0x01, 0x04)?.firstOrNull()?.let { (it / 255f).coerceIn(0f, 1f) }

    /** Положение дроссельной заслонки, доля от 0 до 1 */
    fun throttlePosition(raw: String): Float? =
        payload(raw, 0x01, 0x11)?.firstOrNull()?.let { (it / 255f).coerceIn(0f, 1f) }

    /** Напряжение бортовой сети по данным блока управления, В */
    fun controlModuleVoltage(raw: String): Double? {
        val b = payload(raw, 0x01, 0x42) ?: return null
        if (b.size < 2) return null
        return (b[0] * 256 + b[1]) / 1000.0
    }

    /** Расход воздуха, г/с. Нужен для мгновенного расхода топлива */
    fun massAirFlow(raw: String): Double? {
        val b = payload(raw, 0x01, 0x10) ?: return null
        if (b.size < 2) return null
        return (b[0] * 256 + b[1]) / 100.0
    }

    /**
     * Пробег с момента сброса ошибок, км.
     *
     * Это НЕ одометр машины: счётчик обнуляется вместе с ошибками и считает
     * только с этого момента. Показывать его как пробег нельзя.
     */
    fun kmSinceCodesCleared(raw: String): Int? {
        val b = payload(raw, 0x01, 0x31) ?: return null
        if (b.size < 2) return null
        return b[0] * 256 + b[1]
    }

    // ── Коды неисправностей ─────────────────────────────────────────────────

    private val DTC_SYSTEM = charArrayOf('P', 'C', 'B', 'U')

    /**
     * Коды неисправностей из ответа на режим 03.
     *
     * Каждый код занимает два байта. Старшие два бита первого байта задают
     * систему (P/C/B/U), следующие два — первую цифру, дальше три цифры
     * подряд. Пара нулевых байтов означает пустое место в ответе, а не код
     * `P0000`: адаптер дополняет ответ до целого числа кадров.
     */
    fun parseDtcs(raw: String): List<String> {
        val hex = clean(raw) ?: return emptyList()

        // Снимаем эхо режима. Ищем, а не проверяем начало: при включённых
        // заголовках перед ним стоит адрес блока управления
        val at = hex.indexOf("43")
        val body = if (at >= 0) hex.substring(at + 2) else hex

        return body.chunked(4)
            .filter { it.length == 4 }
            .mapNotNull { group ->
                val value = group.toIntOrNull(16) ?: return@mapNotNull null
                if (value == 0) return@mapNotNull null

                val system = DTC_SYSTEM[(value shr 14) and 0b11]
                val first = (value shr 12) and 0b11
                val rest = "%03X".format(value and 0x0FFF)
                "$system$first$rest"
            }
            .distinct()
    }

    /**
     * VIN из ответа на режим 09, параметр 02.
     *
     * Ответ приходит несколькими строками, у каждой свой номер кадра, а сам
     * VIN — семнадцать байтов ASCII. Первый кадр начинается с числа блоков
     * данных, поэтому просто склеить всё и раскодировать нельзя: в начале
     * окажется мусор.
     *
     * @return семнадцать символов VIN, либо null
     */
    fun parseVin(raw: String): String? {
        val hex = clean(raw) ?: return null
        val at = hex.indexOf("4902")
        if (at < 0) return null

        val ascii = StringBuilder()
        var i = at
        // Условие именно i + 1: для байта нужны два символа. С «i + 3» из
        // ответа выпадал последний байт, VIN выходил шестнадцатисимвольным и
        // молча отбрасывался проверкой длины — то есть VIN не читался никогда
        while (i + 1 < hex.length) {
            // Каждый кадр: 4902 + номер кадра + до шести байт данных
            if (hex.startsWith("4902", i)) {
                i += 6 // «4902» и номер кадра
                continue
            }
            val byte = hex.substring(i, i + 2).toIntOrNull(16)
            i += 2
            if (byte == null || byte == 0x00) continue
            // VIN состоит только из цифр и заглавных латинских букв
            val ch = byte.toChar()
            if (ch.isDigit() || ch in 'A'..'Z') ascii.append(ch)
        }

        val vin = ascii.toString()
        return vin.takeIf { it.length == 17 }
    }

    /**
     * Читается ли ответ как подтверждение команды настройки.
     *
     * ELM327 отвечает `OK` на команды вида `ATE0`. Проверять по вхождению, а
     * не по равенству: в ответе обычно ещё эхо команды и приглашение.
     */
    fun isOk(raw: String): Boolean =
        raw.uppercase().replace(" ", "").contains("OK")
}
