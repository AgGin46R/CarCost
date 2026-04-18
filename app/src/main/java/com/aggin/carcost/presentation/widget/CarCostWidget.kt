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

        provideContent {
            CarCostWidgetContent(
                carsCount = carsCount,
                monthlyTotal = monthlyFormatted,
                carName = carName,
                odometer = odometer,
                activeCarId = activeCarId,
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
    }
}

class CarCostWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CarCostWidget()
}
