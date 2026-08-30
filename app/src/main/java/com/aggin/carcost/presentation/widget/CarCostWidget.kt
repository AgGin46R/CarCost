package com.aggin.carcost.presentation.widget

import com.aggin.carcost.R
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.aggin.carcost.MainActivity
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.notifications.NotificationHelper
import java.util.Calendar
import com.aggin.carcost.presentation.common.emoji
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.aggin.carcost.data.local.database.entities.FuelType
import com.aggin.carcost.data.local.database.entities.canCharge
import com.aggin.carcost.data.local.database.entities.canRefuel
import com.aggin.carcost.domain.fuel.EnergyConsumptionCalculator
import com.aggin.carcost.domain.fuel.FuelConsumptionCalculator
import com.aggin.carcost.util.CurrencyUtils

/**
 * Какая машина показана в этом экземпляре виджета.
 *
 * Хранится у каждого экземпляра отдельно: человек с двумя машинами может
 * поставить два виджета рядом и видеть обе сразу, не переключая.
 */
internal val SELECTED_CAR_KEY = stringPreferencesKey("widget_selected_car")

class CarCostWidget : GlanceAppWidget() {

    // Без этого виджет не помнит ничего между обновлениями, и выбранная машина
    // сбрасывалась бы на первую при каждой перерисовке
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = AppDatabase.getDatabase(context)
        val prefs: Preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)

        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfMonth = cal.timeInMillis

        val allCars = try { database.carDao().getAllActiveCarsSync() } catch (e: Exception) { emptyList() }

        // Показанная машина, а не всегда первая. Если выбранную удалили —
        // откатываемся к первой, а не показываем пустоту
        val selectedId = prefs[SELECTED_CAR_KEY]
        val activeCar = allCars.firstOrNull { it.id == selectedId } ?: allCars.firstOrNull()

        // Итог за месяц — по показанной машине, а не по всем сразу.
        //
        // Раньше сумма считалась по всему автопарку, а пробег, категории и
        // ближайшее ТО относились к одной машине. Получалась карточка, где
        // половина цифр про одно, половина про другое.
        val monthExpenses = activeCar?.let { car ->
            try {
                database.expenseDao().getExpensesInDateRangeSync(
                    carId = car.id,
                    startDate = startOfMonth,
                    endDate = System.currentTimeMillis()
                )
            } catch (e: Exception) { emptyList() }
        } ?: emptyList()

        val monthlyTotal = monthExpenses.sumOf { it.amount }

        val carsCount = allCars.size
        // Валюта берётся у машины: символ рубля стоял здесь жёстко в двух местах
        val currency = activeCar?.currency ?: "RUB"
        val monthlyFormatted = CurrencyUtils.format(monthlyTotal, currency)

        // Расход — главное, ради чего ведут учёт, и в виджете его не было вовсе
        val consumptionLabel: String? = activeCar?.let { car ->
            try {
                val all = database.expenseDao().getExpensesByCarIdSync(car.id)
                if (car.fuelType.canCharge && !car.fuelType.canRefuel) {
                    EnergyConsumptionCalculator.average(all)?.let { context.getString(R.string.widget_1f_kvt_ch_100).format(it) }
                } else {
                    FuelConsumptionCalculator.average(all)?.let { context.getString(R.string.widget_1f_l_100).format(it) }
                }
            } catch (e: Exception) { null }
        }
        val carName = activeCar?.let { "${it.brand} ${it.model}" }
        val odometer = activeCar?.currentOdometer
        val activeCarId = activeCar?.id

        // Top-3 categories by amount this month (for the active car)
        val top3Categories: List<Pair<String, String>> = try {
            if (activeCar != null) {
                // Расходы уже загружены выше — второй запрос к базе не нужен
                monthExpenses.groupBy { it.category }
                    .mapValues { (_, list) -> list.sumOf { it.amount } }
                    .entries
                    .sortedByDescending { it.value }
                    .take(3)
                    .map { (cat, amount) ->
                        cat.emoji() to CurrencyUtils.format(amount, currency)
                    }
            } else emptyList()
        } catch (e: Exception) { emptyList() }

        // Next maintenance reminder (min remaining km)
        val nextMaintenanceLabel: String? = try {
            if (activeCar != null) {
                val reminders = database.maintenanceReminderDao()
                    .getRemindersByCarIdSync(activeCar.id)
                val nearest = reminders
                    .filter { it.nextChangeOdometer > activeCar.currentOdometer }
                    .minByOrNull { it.nextChangeOdometer - activeCar.currentOdometer }
                if (nearest != null) {
                    val remaining = nearest.nextChangeOdometer - activeCar.currentOdometer
                    context.getString(R.string.widget_cherez_km, context.getString(nearest.type.displayNameRes), remaining)
                } else null
            } else null
        } catch (e: Exception) { null }

        provideContent {
            CarCostWidgetContent(
                carsCount = carsCount,
                monthlyTotal = monthlyFormatted,
                carName = carName,
                odometer = odometer,
                activeCarId = activeCarId,
                consumptionLabel = consumptionLabel,
                canSwitchCar = allCars.size > 1,
                top3Categories = top3Categories,
                nextMaintenanceLabel = nextMaintenanceLabel,
                context = context
            )
        }
    }
}

@Composable
fun CarCostWidgetContent(
    carsCount: Int,
    monthlyTotal: String,
    carName: String?,
    odometer: Int?,
    activeCarId: String?,
    /** Расход на сотню — литры или киловатт-часы, смотря чем машина движется */
    consumptionLabel: String? = null,
    /** Машин больше одной: показываем стрелку переключения */
    canSwitchCar: Boolean = false,
    top3Categories: List<Pair<String, String>> = emptyList(),
    nextMaintenanceLabel: String? = null,
    context: Context
) {
    // Intent to open app (root)
    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    // Intent to open Add Expense for active car
    val addExpenseIntent = activeCarId?.let {
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationHelper.EXTRA_NAV_TYPE, NotificationHelper.NAV_TYPE_ADD_EXPENSE)
            putExtra(NotificationHelper.EXTRA_NAV_CAR_ID, it)
            putExtra(MainActivity.EXTRA_FROM_WIDGET, true)
        }
    } ?: openAppIntent

    // Заправка — самая частая запись, и ради неё не стоит проходить выбор
    // категории: с виджета форма открывается уже с выбранным топливом
    val addFuelIntent = activeCarId?.let {
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationHelper.EXTRA_NAV_TYPE, NotificationHelper.NAV_TYPE_ADD_FUEL)
            putExtra(NotificationHelper.EXTRA_NAV_CAR_ID, it)
            putExtra(MainActivity.EXTRA_FROM_WIDGET, true)
        }
    } ?: openAppIntent

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF1565C0)))
            .padding(12.dp)
            .clickable(actionStartActivity(openAppIntent)),
        verticalAlignment = Alignment.Top
    ) {
        // ── Header row: CarCost title + "+" action button ──────────────────────
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CarCost",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            // Заправка отдельной кнопкой: открывает форму с готовой категорией
            Box(
                modifier = GlanceModifier
                    .background(ColorProvider(Color(0xFF42A5F5)))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clickable(actionStartActivity(addFuelIntent)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⛽",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(GlanceModifier.width(6.dp))

            // Quick-add expense button
            Box(
                modifier = GlanceModifier
                    .background(ColorProvider(Color(0xFF42A5F5)))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clickable(actionStartActivity(addExpenseIntent)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = context.getString(R.string.widget_rashod),
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(GlanceModifier.height(8.dp))

        // ── Название машины и переключение ───────────────────────────────────
        //
        // Стрелка появляется только при нескольких машинах: у владельца одной
        // она была бы кнопкой, которая ничего не делает.
        if (carName != null) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = carName,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                if (canSwitchCar) {
                    Box(
                        modifier = GlanceModifier
                            .background(ColorProvider(Color.White.copy(alpha = 0.18f)))
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                            .clickable(actionRunCallback<SwitchCarAction>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "›",
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }

        // ── Пробег и расход в одной строке ───────────────────────────────────
        if (odometer != null || consumptionLabel != null) {
            Spacer(GlanceModifier.height(2.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                if (odometer != null) {
                    Text(
                        text = context.getString(R.string.widget_d_km).format(odometer),
                        style = TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.75f)),
                            fontSize = 11.sp
                        )
                    )
                }
                if (consumptionLabel != null) {
                    if (odometer != null) {
                        Text(
                            text = "  •  ",
                            style = TextStyle(
                                color = ColorProvider(Color.White.copy(alpha = 0.5f)),
                                fontSize = 11.sp
                            )
                        )
                    }
                    Text(
                        text = consumptionLabel,
                        style = TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.75f)),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        Spacer(GlanceModifier.height(6.dp))

        // ── Monthly expenses ─────────────────────────────────────────────────
        Text(
            text = context.getString(R.string.widget_rashody_za_mesyats),
            style = TextStyle(
                color = ColorProvider(Color.White.copy(alpha = 0.75f)),
                fontSize = 11.sp
            )
        )
        Text(
            text = monthlyTotal,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(GlanceModifier.height(4.dp))

        // ── Cars count (only if more than 1 car) ─────────────────────────────
        if (carsCount > 1) {
            Text(
                text = context.getString(R.string.widget_avt, carsCount),
                style = TextStyle(
                    color = ColorProvider(Color.White.copy(alpha = 0.75f)),
                    fontSize = 11.sp
                )
            )
        }

        // ── Top-3 expense categories ──────────────────────────────────────────
        if (top3Categories.isNotEmpty()) {
            Spacer(GlanceModifier.height(6.dp))
            top3Categories.forEach { (emoji, amount) ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = emoji,
                        style = TextStyle(fontSize = 11.sp),
                        modifier = GlanceModifier.width(20.dp)
                    )
                    Text(
                        text = amount,
                        style = TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.85f)),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        // ── Next maintenance ──────────────────────────────────────────────────
        if (nextMaintenanceLabel != null) {
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = "🔧 $nextMaintenanceLabel",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFFFD54F)),
                    fontSize = 10.sp
                ),
                modifier = GlanceModifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Переключение на следующую машину.
 *
 * По кругу, а не списком: выпадающее меню в виджете рисовать нечем, а у людей
 * с несколькими машинами их обычно две-три — пролистать быстрее, чем выбирать.
 */
class SwitchCarAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val cars = try {
            AppDatabase.getDatabase(context).carDao().getAllActiveCarsSync()
        } catch (e: Exception) { emptyList() }
        if (cars.size < 2) return

        val current = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[SELECTED_CAR_KEY]
        val currentIndex = cars.indexOfFirst { it.id == current }
        // Неизвестная машина (её удалили) даёт -1, и следующей окажется первая
        val next = cars[(currentIndex + 1).mod(cars.size)]

        // Возвращаем новый набор настроек, а не правим прежний: этого требует
        // сам updateAppWidgetState — состояние здесь неизменяемое
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply { this[SELECTED_CAR_KEY] = next.id }
        }
        CarCostWidget().update(context, glanceId)
    }
}

class CarCostWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CarCostWidget()
}
