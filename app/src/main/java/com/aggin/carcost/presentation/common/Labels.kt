package com.aggin.carcost.presentation.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CarRepair
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.LocalCarWash
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.database.entities.ServiceType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Единственный источник подписей, эмодзи, иконок и цветов категорий.
 *
 * До этого файла маппинг «категория → название» был переписан в восьми местах и
 * успел разойтись: MAINTENANCE был и «Обслуживанием», и «ТО», и «ТО/Сервисом».
 * Любая новая категория требовала правки всех копий, а забытая копия молча
 * показывала старый текст.
 */

private val RU = Locale("ru")

// ── Категории расходов ───────────────────────────────────────────────────────

fun ExpenseCategory.displayName(): String = when (this) {
    ExpenseCategory.FUEL -> "Топливо"
    ExpenseCategory.CHARGING -> "Зарядка"
    ExpenseCategory.MAINTENANCE -> "Обслуживание"
    ExpenseCategory.REPAIR -> "Ремонт"
    ExpenseCategory.INSURANCE -> "Страховка"
    ExpenseCategory.TAX -> "Налог"
    ExpenseCategory.PARKING -> "Парковка"
    ExpenseCategory.TOLL -> "Платная дорога"
    ExpenseCategory.WASH -> "Мойка"
    ExpenseCategory.FINE -> "Штраф"
    ExpenseCategory.ACCESSORIES -> "Аксессуары"
    ExpenseCategory.OTHER -> "Прочее"
}

/**
 * Подпись по строковому имени категории — для мест, где на руках нет enum
 * (уведомления получают категорию из payload).
 */
fun categoryDisplayName(rawCategory: String): String =
    runCatching { ExpenseCategory.valueOf(rawCategory.uppercase()).displayName() }
        .getOrDefault("Расход")

fun ExpenseCategory.emoji(): String = when (this) {
    ExpenseCategory.FUEL -> "⛽"
    ExpenseCategory.CHARGING -> "🔌"
    ExpenseCategory.MAINTENANCE -> "🔧"
    ExpenseCategory.REPAIR -> "🛠️"
    ExpenseCategory.INSURANCE -> "🛡️"
    ExpenseCategory.TAX -> "📋"
    ExpenseCategory.PARKING -> "🅿️"
    ExpenseCategory.TOLL -> "🛣️"
    ExpenseCategory.WASH -> "🚿"
    ExpenseCategory.FINE -> "⚠️"
    ExpenseCategory.ACCESSORIES -> "🔩"
    ExpenseCategory.OTHER -> "📦"
}

fun ExpenseCategory.icon(): ImageVector = when (this) {
    ExpenseCategory.FUEL -> Icons.Default.LocalGasStation
    ExpenseCategory.CHARGING -> Icons.Default.EvStation
    ExpenseCategory.MAINTENANCE -> Icons.Default.Build
    ExpenseCategory.REPAIR -> Icons.Default.CarRepair
    ExpenseCategory.INSURANCE -> Icons.Default.Security
    ExpenseCategory.TAX -> Icons.Default.AttachMoney
    ExpenseCategory.PARKING -> Icons.Default.LocalParking
    ExpenseCategory.TOLL -> Icons.Default.Toll
    ExpenseCategory.WASH -> Icons.Default.LocalCarWash
    ExpenseCategory.FINE -> Icons.Default.Warning
    ExpenseCategory.ACCESSORIES -> Icons.Default.ShoppingCart
    ExpenseCategory.OTHER -> Icons.Default.MoreHoriz
}

fun ExpenseCategory.color(): Color = when (this) {
    ExpenseCategory.FUEL -> Color(0xFFE57373)
    ExpenseCategory.CHARGING -> Color(0xFF4FC3F7)
    ExpenseCategory.MAINTENANCE -> Color(0xFF81C784)
    ExpenseCategory.REPAIR -> Color(0xFF64B5F6)
    ExpenseCategory.INSURANCE -> Color(0xFFFFD54F)
    ExpenseCategory.TAX -> Color(0xFFBA68C8)
    ExpenseCategory.PARKING -> Color(0xFF4DB6AC)
    ExpenseCategory.TOLL -> Color(0xFFFF8A65)
    ExpenseCategory.WASH -> Color(0xFFA1887F)
    ExpenseCategory.FINE -> Color(0xFFEF5350)
    ExpenseCategory.ACCESSORIES -> Color(0xFF9575CD)
    ExpenseCategory.OTHER -> Color(0xFF90A4AE)
}

// ── Виды работ ───────────────────────────────────────────────────────────────

fun ServiceType.displayName(): String = when (this) {
    ServiceType.OIL_CHANGE -> "Замена масла"
    ServiceType.OIL_FILTER -> "Масляный фильтр"
    ServiceType.AIR_FILTER -> "Воздушный фильтр"
    ServiceType.FUEL_FILTER -> "Топливный фильтр"
    ServiceType.CABIN_FILTER -> "Салонный фильтр"
    ServiceType.SPARK_PLUGS -> "Свечи зажигания"
    ServiceType.BRAKE_PADS -> "Тормозные колодки"
    ServiceType.BRAKE_FLUID -> "Тормозная жидкость"
    ServiceType.COOLANT -> "Охлаждающая жидкость"
    ServiceType.TRANSMISSION_FLUID -> "Трансмиссионное масло"
    ServiceType.TIMING_BELT -> "Ремень ГРМ"
    ServiceType.TIRES -> "Шины"
    ServiceType.BATTERY -> "Аккумулятор"
    ServiceType.ALIGNMENT -> "Развал-схождение"
    ServiceType.BALANCING -> "Балансировка"
    ServiceType.INSPECTION -> "Техосмотр"
    ServiceType.FULL_SERVICE -> "Полное ТО"
    ServiceType.REDUCER_OIL -> "Масло редуктора"
    ServiceType.BATTERY_COOLANT -> "Охлаждающая жидкость батареи"
    ServiceType.BATTERY_HEALTH -> "Проверка состояния батареи"
    ServiceType.BRAKE_CALIPERS -> "Чистка и смазка суппортов"
    ServiceType.OTHER -> "Другое"
}

// ── Даты ─────────────────────────────────────────────────────────────────────
// Три формата намеренно разные: длинный в формах ввода, короткий в списках,
// компактный в фильтрах. Схлопывать их в один нельзя — поедет вёрстка.

/** «05 февраля 2026» — формы добавления и редактирования */
fun formatDateLong(timestamp: Long): String =
    SimpleDateFormat("dd MMMM yyyy", RU).format(Date(timestamp))

/** «05 фев 2026» — списки расходов и планов */
fun formatDateShort(timestamp: Long): String =
    SimpleDateFormat("dd MMM yyyy", RU).format(Date(timestamp))

/** «05.02.26» — фильтры, где место ограничено */
fun formatDateCompact(timestamp: Long): String =
    SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(timestamp))
