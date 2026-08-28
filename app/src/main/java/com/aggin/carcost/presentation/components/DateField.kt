package com.aggin.carcost.presentation.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Поле даты, в которое вводят только цифры.
 *
 * Разделители человек не набирает — они появляются сами по мере ввода:
 * 31122026 показывается как 31.12.2026. Клавиатура открывается цифровая, а не
 * буквенная, так что до цифр не нужно переключаться.
 *
 * Раньше дату полиса приходилось набирать целиком, вместе с точками, на обычной
 * клавиатуре — и, что хуже, ошибка в наборе никак не показывалась: при разборе
 * строки неудача молча заменялась сегодняшним числом. Человек вводил «1.12.26»,
 * сохранял и получал полис, начинающийся сегодня, ничего об этом не узнав.
 *
 * @param digits только цифры, до восьми: ддммгггг
 */
@Composable
fun DateField(
    digits: String,
    onDigitsChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val complete = digits.length == 8
    val parsed = if (complete) dateDigitsToMillis(digits) else null
    val invalid = complete && parsed == null

    OutlinedTextField(
        value = digits,
        onValueChange = { input ->
            onDigitsChange(input.filter { it.isDigit() }.take(8))
        },
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        isError = invalid,
        supportingText = if (invalid) {
            { Text("Такой даты нет") }
        } else null,
        placeholder = { Text("дд.мм.гггг") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        visualTransformation = DateMask
    )
}

/**
 * Расстановка точек между группами.
 *
 * Точка появляется только тогда, когда за ней уже что-то есть: после «31» поле
 * показывает «31», а не «31.» — висящий разделитель выглядит как ошибка ввода.
 */
private object DateMask : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val masked = buildString {
            for (i in digits.indices) {
                append(digits[i])
                if (i == 1 && digits.length > 2) append('.')
                if (i == 3 && digits.length > 4) append('.')
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                offset + (if (offset > 2) 1 else 0) + (if (offset > 4) 1 else 0)

            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 5 -> offset - 1
                else -> offset - 2
            }
        }
        return TransformedText(AnnotatedString(masked), mapping)
    }
}

/**
 * Разбор восьми цифр в дату.
 *
 * Разбор строгий: 31.02 — не дата, и подставлять вместо неё ближайшее число
 * нельзя. Возвращаем null, чтобы вызывающий показал ошибку, а не сохранил
 * молча что-то своё.
 */
fun dateDigitsToMillis(digits: String): Long? {
    if (digits.length != 8) return null
    val format = SimpleDateFormat("ddMMyyyy", Locale.getDefault()).apply { isLenient = false }
    val date = runCatching { format.parse(digits) }.getOrNull() ?: return null
    // Полдень: у полуночи при переводе часов дата иногда съезжает на сутки назад
    val calendar = Calendar.getInstance().apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 12)
    }
    return calendar.timeInMillis
}

/** Обратное преобразование — для полей, открывающихся с уже известной датой */
fun millisToDateDigits(millis: Long): String =
    SimpleDateFormat("ddMMyyyy", Locale.getDefault()).format(Date(millis))
