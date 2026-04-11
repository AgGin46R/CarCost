package com.aggin.carcost.presentation.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.aggin.carcost.MainActivity
import com.aggin.carcost.data.local.database.AppDatabase
import java.util.Calendar

class CarCostWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = AppDatabase.getDatabase(context)

        // Подсчёт суммарных расходов за текущий месяц по всем машинам
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfMonth = cal.timeInMillis

        val allCars = try { database.carDao().getAllActiveCarsSync() } catch (e: Exception) { emptyList() }
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

        provideContent {
            CarCostWidgetContent(
                carsCount = carsCount,
                monthlyTotal = monthlyFormatted
            )
        }
    }
}

@Composable
fun CarCostWidgetContent(carsCount: Int, monthlyTotal: String) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF1565C0)))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "CarCost",
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(GlanceModifier.height(6.dp))
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
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = "$carsCount авт.",
            style = TextStyle(
                color = ColorProvider(Color.White.copy(alpha = 0.75f)),
                fontSize = 11.sp
            )
        )
    }
}

class CarCostWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CarCostWidget()
}
