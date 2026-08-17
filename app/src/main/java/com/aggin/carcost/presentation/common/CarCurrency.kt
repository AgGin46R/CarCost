package com.aggin.carcost.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.util.CurrencyUtils

/**
 * Валюта автомобиля, открытого на экране.
 *
 * Валюта — свойство машины, а не отдельной записи: человек ведёт одну машину в
 * одной валюте. Протаскивать её параметром через десяток вложенных функций
 * разметки бессмысленно, поэтому она объявлена здесь.
 *
 * Такой приём уже применялся в аналитике и планируемых расходах — там символ
 * рубля был захардкожен в полутора десятках мест, и у машины в евро весь экран
 * показывал неверные подписи. Этот файл сводит приём в одно место, чтобы каждый
 * следующий экран не изобретал его заново.
 *
 * Значение по умолчанию — рубль: экраны, до которых валюта ещё не доведена,
 * ведут себя как раньше, а не падают.
 */
val LocalCarCurrency = compositionLocalOf { "RUB" }

/** Сумма с символом валюты текущего автомобиля */
@Composable
fun currencyFormat(amount: Double, decimals: Int = 0): String {
    val symbol = CurrencyUtils.symbol(LocalCarCurrency.current)
    return "%.${decimals}f $symbol".format(amount)
}

/**
 * Смешаны ли в этих записях разные валюты.
 *
 * Такое возможно только у данных, созданных до того, как валюта стала браться у
 * автомобиля: тогда каждой записи проставлялся рубль независимо от машины.
 *
 * Пересчитывать по курсу нельзя — неизвестно ни курс какого дня брать, ни какая
 * валюта в записи настоящая. Единственное честное поведение — сказать человеку,
 * что итог здесь складывать нельзя, и дать ему решить самому.
 */
fun hasMixedCurrencies(expenses: List<Expense>): Boolean =
    expenses.mapTo(HashSet()) { it.currency }.size > 1
