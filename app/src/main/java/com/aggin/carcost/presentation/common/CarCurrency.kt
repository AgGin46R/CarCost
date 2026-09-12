package com.aggin.carcost.presentation.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aggin.carcost.R
import com.aggin.carcost.data.local.database.entities.Car

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

/**
 * Ведутся ли машины в разных валютах.
 *
 * Своё имя, а не перегрузка hasMixedCurrencies: на JVM обе принимали бы
 * List и различить их было бы нельзя. И смысл всё-таки другой.
 *
 * Отдельно от проверки по расходам: там смешение — след старых данных и почти
 * всегда ошибка, а здесь это нормальное положение дел. Одна машина куплена в
 * рублях, вторая в евро — и обе ведутся правильно. Складывать и сравнивать их
 * суммы всё равно нельзя.
 */
fun carsUseDifferentCurrencies(cars: List<Car>): Boolean =
    cars.mapTo(HashSet()) { it.currency }.size > 1

/**
 * Полоса «здесь смешаны валюты».
 *
 * Была только в аналитике, хотя суммы складываются ещё в стоимости владения,
 * в сравнении машин и в итогах года. В сравнении отсутствие такой полосы —
 * худший случай: две машины в разных валютах выставлялись рядом как
 * сопоставимые, и вывод получался прямо ложным.
 *
 * Пересчитать по курсу нельзя: неизвестно ни курс какого дня брать, ни какая
 * валюта в записи настоящая. Поэтому не досочиняем, а говорим.
 *
 * @param explanationRes чем именно здесь нельзя пользоваться
 */
@Composable
fun MixedCurrencyWarning(
    modifier: Modifier = Modifier,
    @StringRes titleRes: Int = R.string.currency_mixed_title,
    @StringRes explanationRes: Int = R.string.currency_mixed_totals
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(titleRes),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = stringResource(explanationRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
