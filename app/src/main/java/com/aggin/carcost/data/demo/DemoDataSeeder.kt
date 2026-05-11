package com.aggin.carcost.data.demo

import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.database.entities.FuelType
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import com.aggin.carcost.data.local.database.entities.MaintenanceType
import com.aggin.carcost.data.local.database.entities.PlannedExpense
import com.aggin.carcost.data.local.database.entities.PlannedExpensePriority
import com.aggin.carcost.data.local.database.entities.PlannedExpenseStatus
import java.util.*

/**
 * Seeds realistic demo data for first-launch users who want to explore the app.
 *
 * Call from a coroutine:
 *   DemoDataSeeder.seed(database, userId)
 */
object DemoDataSeeder {

    /** Check if demo data is already seeded (car count > 0) */
    suspend fun isSeeded(db: AppDatabase): Boolean =
        db.carDao().getAllActiveCarsSync().isNotEmpty()

    suspend fun seed(db: AppDatabase, userId: String) {
        // ── Demo car ─────────────────────────────────────────────────────────
        val car = Car(
            brand = "Toyota",
            model = "Camry",
            year = 2021,
            licensePlate = "А777АА77",
            vin = "4T1BF1FK5CU509765",
            color = "#C0C0C0",
            currentOdometer = 47_200,
            odometerUnit = com.aggin.carcost.data.local.database.entities.OdometerUnit.KM,
            purchaseDate = dateOf(2021, Calendar.JUNE, 15),
            purchasePrice = 2_450_000.0,
            purchaseOdometer = 0,
            fuelType = FuelType.GASOLINE,
            tankCapacity = 60.0,
            currency = "RUB"
        )
        db.carDao().insertCar(car)

        // ── Demo expenses (last 6 months) ─────────────────────────────────
        val now = System.currentTimeMillis()
        val ms30 = 30L * 24 * 3600 * 1000

        // Simulate odometer readings spaced ~800 km per month
        val baseOdo = 40_000
        val expenses = listOf(
            // Fuel — roughly monthly
            expense(car.id, ExpenseCategory.FUEL, 3_850.0, now - ms30 * 0 - dayMs(3), baseOdo + 7200, "АЗС Лукойл, 47 л"),
            expense(car.id, ExpenseCategory.FUEL, 3_760.0, now - ms30 * 1 - dayMs(2), baseOdo + 6400, "АЗС Роснефть, 46 л"),
            expense(car.id, ExpenseCategory.FUEL, 3_900.0, now - ms30 * 2 - dayMs(5), baseOdo + 5600, "АЗС Газпром, 48 л"),
            expense(car.id, ExpenseCategory.FUEL, 3_620.0, now - ms30 * 3 - dayMs(1), baseOdo + 4800, "АЗС BP, 44 л"),
            expense(car.id, ExpenseCategory.FUEL, 3_780.0, now - ms30 * 4 - dayMs(4), baseOdo + 4000, "АЗС Лукойл, 46 л"),
            expense(car.id, ExpenseCategory.FUEL, 3_700.0, now - ms30 * 5 - dayMs(2), baseOdo + 3200, "АЗС Роснефть, 45 л"),

            // Maintenance
            expense(car.id, ExpenseCategory.MAINTENANCE, 7_500.0, now - ms30 * 2 - dayMs(10), baseOdo + 5200, "Замена масла + фильтр, 5W-30 Shell"),
            expense(car.id, ExpenseCategory.MAINTENANCE, 2_200.0, now - ms30 * 4 - dayMs(7), baseOdo + 3600, "Замена воздушного фильтра"),

            // Insurance
            expense(car.id, ExpenseCategory.INSURANCE, 28_500.0, now - ms30 * 5 - dayMs(20), baseOdo + 3000, "КАСКО на год"),

            // Parking
            expense(car.id, ExpenseCategory.PARKING, 1_200.0, now - ms30 * 1 - dayMs(8), baseOdo + 6000, "Паркинг ТЦ Авиапарк, месяц"),
            expense(car.id, ExpenseCategory.PARKING, 350.0, now - dayMs(5), baseOdo + 7100, "Паркинг аэропорт"),

            // Wash
            expense(car.id, ExpenseCategory.WASH, 890.0, now - ms30 * 1 - dayMs(15), baseOdo + 5900, "Мойка + чернение шин"),
            expense(car.id, ExpenseCategory.WASH, 650.0, now - ms30 * 3 - dayMs(3), baseOdo + 4700, "Экспресс-мойка"),

            // Fine
            expense(car.id, ExpenseCategory.FINE, 500.0, now - ms30 * 2 - dayMs(18), baseOdo + 5400, "Штраф ГИБДД, превышение скорости"),
        )
        db.expenseDao().insertExpenses(expenses)

        // ── Maintenance reminders ─────────────────────────────────────────
        val reminders = listOf(
            MaintenanceReminder(
                carId = car.id,
                type = MaintenanceType.OIL_CHANGE,
                lastChangeOdometer = 40_000,
                intervalKm = 10_000,
                nextChangeOdometer = 50_000,
                notes = "Shell Helix Ultra 5W-30"
            ),
            MaintenanceReminder(
                carId = car.id,
                type = MaintenanceType.BRAKE_PADS,
                lastChangeOdometer = 25_000,
                intervalKm = 40_000,
                nextChangeOdometer = 65_000
            ),
            MaintenanceReminder(
                carId = car.id,
                type = MaintenanceType.TIMING_BELT,
                lastChangeOdometer = 0,
                intervalKm = 90_000,
                nextChangeOdometer = 90_000
            ),
        )
        reminders.forEach { db.maintenanceReminderDao().insertReminder(it) }

        // ── Planned expenses ──────────────────────────────────────────────
        val planned = listOf(
            PlannedExpense(
                carId = car.id,
                userId = userId,
                title = "Летняя резина → зимняя",
                category = ExpenseCategory.MAINTENANCE,
                estimatedAmount = 12_000.0,
                priority = PlannedExpensePriority.HIGH,
                status = PlannedExpenseStatus.PLANNED,
                targetDate = dateOf(2025, Calendar.OCTOBER, 15),
                notes = "Continental ContiWinterContact TS860, R17"
            ),
            PlannedExpense(
                carId = car.id,
                userId = userId,
                title = "Тонировка стёкол",
                category = ExpenseCategory.ACCESSORIES,
                estimatedAmount = 6_500.0,
                priority = PlannedExpensePriority.LOW,
                status = PlannedExpenseStatus.PLANNED
            ),
        )
        planned.forEach { db.plannedExpenseDao().insertPlannedExpense(it) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun dateOf(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun dayMs(days: Int): Long = days * 24L * 3600 * 1000

    private fun expense(
        carId: String,
        category: ExpenseCategory,
        amount: Double,
        date: Long,
        odometer: Int,
        notes: String? = null
    ) = Expense(
        carId = carId,
        category = category,
        amount = amount,
        date = date,
        odometer = odometer,
        description = notes
    )
}
