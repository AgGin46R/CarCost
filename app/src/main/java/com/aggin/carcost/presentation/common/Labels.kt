package com.aggin.carcost.presentation.common

import android.content.Context
import androidx.annotation.StringRes
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
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

/**
 * Язык подписей.
 *
 * Через get(), а не val: значение читается заново при каждом обращении.
 * Файловое свойство инициализируется один раз на процесс, а язык меняется без
 * его перезапуска — застывшее значение осталось бы прежним до перезагрузки
 * приложения, и месяцы показывались бы на старом языке.
 */
private val RU: Locale get() = Locale.getDefault()

// ── Категории расходов ───────────────────────────────────────────────────────

/**
 * Ключ подписи категории.
 *
 * Возвращается идентификатор ресурса, а не готовая строка: подписи нужны и на
 * экранах, и в уведомлениях, и в выгрузке, а способ достать текст в этих трёх
 * местах разный. Общим остаётся ключ.
 */
@StringRes
fun ExpenseCategory.displayNameRes(): Int = when (this) {
    ExpenseCategory.FUEL -> R.string.category_fuel
    ExpenseCategory.CHARGING -> R.string.category_charging
    ExpenseCategory.MAINTENANCE -> R.string.category_maintenance
    ExpenseCategory.REPAIR -> R.string.category_repair
    ExpenseCategory.INSURANCE -> R.string.category_insurance
    ExpenseCategory.TAX -> R.string.category_tax
    ExpenseCategory.PARKING -> R.string.category_parking
    ExpenseCategory.TOLL -> R.string.category_toll
    ExpenseCategory.WASH -> R.string.category_wash
    ExpenseCategory.FINE -> R.string.category_fine
    ExpenseCategory.ACCESSORIES -> R.string.category_accessories
    ExpenseCategory.OTHER -> R.string.category_other
}

/** Подпись на экране */
@Composable
fun ExpenseCategory.displayName(): String = stringResource(displayNameRes())

/** Подпись вне композиции: уведомления, выгрузка, виджет */
fun ExpenseCategory.displayName(context: Context): String =
    context.getString(displayNameRes())

/**
 * Подпись по строковому имени категории — для мест, где на руках нет enum
 * (уведомления получают категорию из payload).
 */
fun categoryDisplayName(context: Context, rawCategory: String): String =
    runCatching { ExpenseCategory.valueOf(rawCategory.uppercase()).displayName(context) }
        .getOrDefault(context.getString(R.string.category_generic_expense))

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

/** Ключ названия работы. Как и у категорий — ключ, а не готовый текст */
@StringRes
fun ServiceType.displayNameRes(): Int = when (this) {
    ServiceType.OIL_CHANGE -> R.string.service_oil_change
    ServiceType.OIL_FILTER -> R.string.service_oil_filter
    ServiceType.AIR_FILTER -> R.string.service_air_filter
    ServiceType.FUEL_FILTER -> R.string.service_fuel_filter
    ServiceType.CABIN_FILTER -> R.string.service_cabin_filter
    ServiceType.SPARK_PLUGS -> R.string.service_spark_plugs
    ServiceType.BRAKE_PADS -> R.string.service_brake_pads
    ServiceType.BRAKE_FLUID -> R.string.service_brake_fluid
    ServiceType.COOLANT -> R.string.service_coolant
    ServiceType.TRANSMISSION_FLUID -> R.string.service_transmission_fluid
    ServiceType.TIMING_BELT -> R.string.service_timing_belt
    ServiceType.TIRES -> R.string.service_tires
    ServiceType.BATTERY -> R.string.service_battery
    ServiceType.ALIGNMENT -> R.string.service_alignment
    ServiceType.BALANCING -> R.string.service_balancing
    ServiceType.INSPECTION -> R.string.service_inspection
    ServiceType.FULL_SERVICE -> R.string.service_full
    ServiceType.REDUCER_OIL -> R.string.service_reducer_oil
    ServiceType.BATTERY_COOLANT -> R.string.service_battery_coolant
    ServiceType.BATTERY_HEALTH -> R.string.service_battery_health
    ServiceType.BRAKE_CALIPERS -> R.string.service_brake_calipers
    ServiceType.CHAIN_LUBE -> R.string.service_chain_lube
    ServiceType.CHAIN_REPLACE -> R.string.service_chain_replace
    ServiceType.FORK_OIL -> R.string.service_fork_oil
    ServiceType.VALVE_CLEARANCE -> R.string.service_valve_clearance
    ServiceType.OTHER -> R.string.service_other
}

/** Название работы на экране */
@Composable
fun ServiceType.displayName(): String = stringResource(displayNameRes())

/** Название работы вне композиции */
fun ServiceType.displayName(context: Context): String =
    context.getString(displayNameRes())

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
