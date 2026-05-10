package com.aggin.carcost.presentation.widget

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

class CarCostWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = AppDatabase.getDatabase(context)

        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfMonth = cal.timeInMillis

        val allCars = try { database.carDao().getAllActiveCarsSync() } catch (e: Exception) { emptyList() }
        val activeCar = allCars.firstOrNull()

        var monthlyTotal = 0.0
        for (car in allCars) {
            try {
                val expenses = database.expenseDao().getExpensesInDateRangeSync(
                    carId = car.id,
                    startDate = startOfMonth,
                    endDate = System.currentTimeMillis()
                )
                monthlyTotal += expenses.sumOf { it.amount }
            } catch (e: Exception) { /* skip */ }
        }

        val carsCount = allCars.size
        val monthlyFormatted = "%.0f ₽".format(monthlyTotal)
        val carName = activeCar?.let { "${it.brand} ${it.model}" }
        val odometer = activeCar?.currentOdometer
        val activeCarId = activeCar?.id

        // Top-3 categories by amount this month (for the active car)
        val top3Categories: List<Pair<String, String>> = try {
            if (activeCar != null) {
                val expenses = database.expenseDao().getExpensesInDateRangeSync(
                    carId = activeCar.id,
                    startDate = startOfMonth,
                    endDate = System.currentTimeMillis()
                )
                expenses.groupBy { it.category }
                    .mapValues { (_, list) -> list.sumOf { it.amount } }
                    .entries
                    .sortedByDescending { it.value }
                    .take(3)
                    .map { (cat, amount) ->
                        val emoji = when (cat) {
                            com.aggin.carcost.data.local.database.entities.ExpenseCategory.FUEL -> "⛽"
                            com.aggin.carcost.data.local.database.entities.ExpenseCategory.MAINTENANCE -> "🔧"
                            com.aggin.carcost.data.local.database.entities.ExpenseCategory.REPAIR -> "🛠️"
                            com.aggin.carcost.data.local.database.entities.ExpenseCategory.INSURANCE -> "🛡️"
                            com.aggin.carcost.data.local.database.entities.ExpenseCategory.PARKING -> "🅿️"
                            else -> "📦"
                        }
                        emoji to "%.0f ₽".format(amount)
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
                    "${nearest.type.displayName}: через $remaining км"
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
            // Quick-add expense button
            Box(
                modifier = GlanceModifier
                    .background(ColorProvider(Color(0xFF42A5F5)))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clickable(actionStartActivity(addExpenseIntent)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+ Расход",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(GlanceModifier.height(8.dp))

        // ── Active car name ───────────────────────────────────────────────────
        if (carName != null) {
            Text(
                text = carName,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.fillMaxWidth()
            )
        }

        // ── Odometer ─────────────────────────────────────────────────────────
        if (odometer != null) {
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = "%,d км".format(odometer),
                style = TextStyle(
                    color = ColorProvider(Color.White.copy(alpha = 0.75f)),
                    fontSize = 11.sp
                )
            )
        }

        Spacer(GlanceModifier.height(6.dp))

        // ── Monthly expenses ─────────────────────────────────────────────────
        Text(
            text = "Расходы за месяц",
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
                text = "$carsCount авт.",
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

class CarCostWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CarCostWidget()
}
