package com.aggin.carcost.util

object CurrencyUtils {

    val SUPPORTED_CURRENCIES = listOf("RUB", "USD", "EUR", "GBP", "CNY", "JPY", "KZT", "BYN", "UAH")

    fun symbol(currency: String): String = when (currency.uppercase()) {
        "RUB" -> "₽"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "CNY", "JPY" -> "¥"
        "KZT" -> "₸"
        "BYN" -> "Br"
        "UAH" -> "₴"
        else -> currency
    }

    fun format(amount: Double, currency: String): String =
        "%.0f ${symbol(currency)}".format(amount)
}
