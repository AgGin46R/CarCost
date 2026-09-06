package com.aggin.carcost.data.obd

import androidx.annotation.StringRes
import com.aggin.carcost.R

/**
 * Расшифровка кодов неисправностей.
 *
 * Ради этого справочника вся работа с адаптером и затевалась: сам по себе
 * код «P0301» человеку не говорит ничего, и лампочка на панели остаётся
 * такой же загадкой, какой была.
 *
 * Кодов существуют тысячи, включая заводские у каждой марки свои. Здесь
 * лежат самые частые, а для остальных есть разбор по устройству самого
 * кода: третий символ задаёт подсистему, и сказать «неисправность системы
 * подачи топлива» честнее и полезнее, чем показать голый код.
 *
 * Файл собран скриптом из одной таблицы — иначе строки на четырёх языках
 * разъехались бы при первой же правке.
 */
object DtcCatalog {

    private val KNOWN: Map<String, Int> = mapOf(
        "P0300" to R.string.dtc_p0300,
        "P0301" to R.string.dtc_p0301,
        "P0302" to R.string.dtc_p0302,
        "P0303" to R.string.dtc_p0303,
        "P0304" to R.string.dtc_p0304,
        "P0305" to R.string.dtc_p0305,
        "P0306" to R.string.dtc_p0306,
        "P0316" to R.string.dtc_p0316,
        "P0171" to R.string.dtc_p0171,
        "P0172" to R.string.dtc_p0172,
        "P0174" to R.string.dtc_p0174,
        "P0175" to R.string.dtc_p0175,
        "P0100" to R.string.dtc_p0100,
        "P0101" to R.string.dtc_p0101,
        "P0102" to R.string.dtc_p0102,
        "P0103" to R.string.dtc_p0103,
        "P0106" to R.string.dtc_p0106,
        "P0107" to R.string.dtc_p0107,
        "P0108" to R.string.dtc_p0108,
        "P0110" to R.string.dtc_p0110,
        "P0113" to R.string.dtc_p0113,
        "P0130" to R.string.dtc_p0130,
        "P0131" to R.string.dtc_p0131,
        "P0132" to R.string.dtc_p0132,
        "P0133" to R.string.dtc_p0133,
        "P0134" to R.string.dtc_p0134,
        "P0135" to R.string.dtc_p0135,
        "P0136" to R.string.dtc_p0136,
        "P0141" to R.string.dtc_p0141,
        "P0420" to R.string.dtc_p0420,
        "P0430" to R.string.dtc_p0430,
        "P0440" to R.string.dtc_p0440,
        "P0441" to R.string.dtc_p0441,
        "P0442" to R.string.dtc_p0442,
        "P0443" to R.string.dtc_p0443,
        "P0455" to R.string.dtc_p0455,
        "P0456" to R.string.dtc_p0456,
        "P0115" to R.string.dtc_p0115,
        "P0117" to R.string.dtc_p0117,
        "P0118" to R.string.dtc_p0118,
        "P0125" to R.string.dtc_p0125,
        "P0128" to R.string.dtc_p0128,
        "P0325" to R.string.dtc_p0325,
        "P0335" to R.string.dtc_p0335,
        "P0336" to R.string.dtc_p0336,
        "P0340" to R.string.dtc_p0340,
        "P0341" to R.string.dtc_p0341,
        "P0016" to R.string.dtc_p0016,
        "P0011" to R.string.dtc_p0011,
        "P0014" to R.string.dtc_p0014,
        "P0351" to R.string.dtc_p0351,
        "P0352" to R.string.dtc_p0352,
        "P0353" to R.string.dtc_p0353,
        "P0354" to R.string.dtc_p0354,
        "P0400" to R.string.dtc_p0400,
        "P0401" to R.string.dtc_p0401,
        "P0402" to R.string.dtc_p0402,
        "P0403" to R.string.dtc_p0403,
        "P0120" to R.string.dtc_p0120,
        "P0121" to R.string.dtc_p0121,
        "P0122" to R.string.dtc_p0122,
        "P0123" to R.string.dtc_p0123,
        "P0221" to R.string.dtc_p0221,
        "P0505" to R.string.dtc_p0505,
        "P0506" to R.string.dtc_p0506,
        "P0507" to R.string.dtc_p0507,
        "P0201" to R.string.dtc_p0201,
        "P0202" to R.string.dtc_p0202,
        "P0203" to R.string.dtc_p0203,
        "P0204" to R.string.dtc_p0204,
        "P0087" to R.string.dtc_p0087,
        "P0088" to R.string.dtc_p0088,
        "P0230" to R.string.dtc_p0230,
        "P0234" to R.string.dtc_p0234,
        "P0299" to R.string.dtc_p0299,
        "P0562" to R.string.dtc_p0562,
        "P0563" to R.string.dtc_p0563,
        "P0500" to R.string.dtc_p0500,
        "P0501" to R.string.dtc_p0501,
        "P0700" to R.string.dtc_p0700,
        "P0715" to R.string.dtc_p0715,
        "P0730" to R.string.dtc_p0730,
        "P0740" to R.string.dtc_p0740,
        "P0741" to R.string.dtc_p0741,
        "P0755" to R.string.dtc_p0755,
        "U0100" to R.string.dtc_u0100,
        "U0101" to R.string.dtc_u0101,
        "U0121" to R.string.dtc_u0121,
        "U0155" to R.string.dtc_u0155,
        "C0035" to R.string.dtc_c0035,
        "C0040" to R.string.dtc_c0040,
        "C0045" to R.string.dtc_c0045,
        "C0050" to R.string.dtc_c0050,
    )

    /**
     * Описание кода.
     *
     * @return ресурс с расшифровкой. Для незнакомого кода — описание его
     *   подсистемы; для совсем непонятного — честное «расшифровки нет»
     */
    @StringRes
    fun describe(code: String): Int {
        val normalized = code.trim().uppercase()
        KNOWN[normalized]?.let { return it }
        return groupOf(normalized)
    }

    /** Знаем ли код точно, или показываем описание подсистемы */
    fun isKnown(code: String): Boolean = KNOWN.containsKey(code.trim().uppercase())

    /**
     * Описание подсистемы по устройству кода.
     *
     * Для кодов двигателя смысл несёт третий символ, для остальных систем
     * достаточно первого: у шасси и кузова подгруппы не стандартизованы,
     * и притворяться, что мы их знаем, не стоит.
     */
    @StringRes
    private fun groupOf(code: String): Int {
        if (code.length < 3) return R.string.dtc_group_unknown
        return when (code[0]) {
            'P' -> when (code[2]) {
                '1' -> R.string.dtc_group_p_1
                '2' -> R.string.dtc_group_p_2
                '3' -> R.string.dtc_group_p_3
                '4' -> R.string.dtc_group_p_4
                '5' -> R.string.dtc_group_p_5
                '6' -> R.string.dtc_group_p_6
                '7' -> R.string.dtc_group_p_7
                'A' -> R.string.dtc_group_p_a
                else -> R.string.dtc_group_unknown
            }
            'C' -> R.string.dtc_group_c
            'B' -> R.string.dtc_group_b
            'U' -> R.string.dtc_group_u
            else -> R.string.dtc_group_unknown
        }
    }
}
