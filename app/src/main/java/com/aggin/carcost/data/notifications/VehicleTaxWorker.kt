package com.aggin.carcost.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aggin.carcost.R
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.VehicleType
import com.aggin.carcost.domain.tax.VehicleTaxCalculator
import java.util.Calendar

/**
 * Напоминание о транспортном налоге.
 *
 * Срок уплаты — первое декабря. Уведомления от налоговой приходят осенью, и
 * именно тогда о налоге вспоминают: в ноябре напомнить полезно, в феврале —
 * бессмысленно. Поэтому воркер запускается ежедневно, но пишет только в
 * заданные дни, а не при каждом запуске.
 */
class VehicleTaxWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "vehicle_tax_reminder"

        /**
         * Дни ноября, в которые напоминаем.
         *
         * Первое — заранее, чтобы успеть свериться с квитанцией; двадцать
         * пятое — когда до срока неделя.
         */
        private val REMIND_DAYS = listOf(1, 25)
    }

    override suspend fun doWork(): Result {
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.MONTH) != Calendar.NOVEMBER) return Result.success()
        if (cal.get(Calendar.DAY_OF_MONTH) !in REMIND_DAYS) return Result.success()

        val db = AppDatabase.getDatabase(applicationContext)
        val cars = db.carDao().getAllActiveCarsSync()
        val taxYear = VehicleTaxCalculator.currentTaxYear()

        cars.forEachIndexed { index, car ->
            // Без мощности налог не посчитать. Напоминать «заплатите налог,
            // сколько — не знаем» смысла нет: человек и так помнит, что налог
            // существует
            val power = car.enginePowerHp?.takeIf { it > 0 } ?: return@forEachIndexed
            val months = VehicleTaxCalculator.ownedMonthsIn(car.purchaseDate, taxYear)
            if (months <= 0) return@forEachIndexed

            val amount = VehicleTaxCalculator.annualTax(
                powerHp = power,
                ratePerHp = car.taxRatePerHp,
                ownedMonths = months,
                isMotorcycle = car.vehicleType == VehicleType.MOTORCYCLE
            ) ?: return@forEachIndexed

            NotificationHelper.sendGenericNotification(
                context = applicationContext,
                notificationId = 7100 + index,
                title = applicationContext.getString(
                    R.string.tax_notify_title,
                    "${car.brand} ${car.model}"
                ),
                body = applicationContext.getString(
                    R.string.tax_notify_body,
                    "%.0f".format(amount),
                    taxYear
                ),
                carId = car.id,
                navType = null
            )
        }

        return Result.success()
    }
}
