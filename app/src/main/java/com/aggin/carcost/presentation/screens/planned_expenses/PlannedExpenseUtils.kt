package com.aggin.carcost.presentation.screens.planned_expenses

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.aggin.carcost.data.local.database.entities.*
import java.text.SimpleDateFormat
import java.util.*

// Иконки категорий
fun getCategoryIcon(category: ExpenseCategory): ImageVector = when (category) {
    ExpenseCategory.FUEL -> Icons.Default.LocalGasStation
    ExpenseCategory.MAINTENANCE -> Icons.Default.Build
    ExpenseCategory.REPAIR -> Icons.Default.CarRepair
    ExpenseCategory.INSURANCE -> Icons.Default.Security
    ExpenseCategory.TAX -> Icons.Default.AttachMoney
    ExpenseCategory.PARKING -> Icons.Default.LocalParking
    ExpenseCategory.WASH -> Icons.Default.LocalCarWash
    ExpenseCategory.FINE -> Icons.Default.Warning
    ExpenseCategory.TOLL -> Icons.Default.Toll
    ExpenseCategory.ACCESSORIES -> Icons.Default.ShoppingCart
    ExpenseCategory.OTHER -> Icons.Default.MoreHoriz
}

// Названия категорий
fun getCategoryName(category: ExpenseCategory): String = when (category) {
    ExpenseCategory.FUEL -> "Топливо"
    ExpenseCategory.MAINTENANCE -> "ТО"
    ExpenseCategory.REPAIR -> "Ремонт"
    ExpenseCategory.INSURANCE -> "Страховка"
    ExpenseCategory.TAX -> "Налог"
    ExpenseCategory.PARKING -> "Парковка"
    ExpenseCategory.WASH -> "Мойка"
    ExpenseCategory.FINE -> "Штраф"
    ExpenseCategory.TOLL -> "Платная дорога"
    ExpenseCategory.ACCESSORIES -> "Аксессуары"
    ExpenseCategory.OTHER -> "Другое"
}

// Форматирование даты
fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale("ru"))
    return sdf.format(Date(timestamp))
}

// Форматирование валюты
fun formatCurrency(amount: Double): String {
    return String.format("%.0f ₽", amount)
}