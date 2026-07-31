package com.aggin.carcost.presentation.screens.planned_expenses

import androidx.compose.ui.graphics.vector.ImageVector
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.presentation.common.displayName
import com.aggin.carcost.presentation.common.formatDateShort
import com.aggin.carcost.presentation.common.icon
import com.aggin.carcost.util.CurrencyUtils

// Подписи, иконки и форматы живут в presentation/common/Labels.kt — здесь только
// привычные экрану имена, чтобы не переписывать десятки вызовов.

fun getCategoryIcon(category: ExpenseCategory): ImageVector = category.icon()

fun getCategoryName(category: ExpenseCategory): String = category.displayName()

fun formatDate(timestamp: Long): String = formatDateShort(timestamp)

/**
 * Раньше здесь был жёстко зашит ₽ — при валюте автомобиля, отличной от рубля,
 * планируемые расходы показывали неверный символ.
 */
fun formatCurrency(amount: Double, currency: String = "RUB"): String =
    CurrencyUtils.format(amount, currency)
