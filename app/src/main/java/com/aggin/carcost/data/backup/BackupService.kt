package com.aggin.carcost.data.backup

import com.aggin.carcost.R
import android.content.Context
import android.net.Uri
import android.util.Log
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

private const val TAG = "BackupService"

/**
 * Полная резервная копия в JSON.
 *
 * Существовавший CSV-«бэкап» восстановить было нельзя в принципе: он писался
 * для человека, без идентификаторов, без напоминаний/документов/бюджетов, а
 * даты — в локальном формате устройства. Кнопка «Backup» обещала страховку,
 * которой не было.
 *
 * Здесь наоборот: машинный формат с id, чтобы повторный импорт не плодил дубли
 * (все DAO вставляют с OnConflictStrategy.REPLACE).
 *
 * CSV и PDF остаются как отчёты — это разные задачи, и смешивать их не надо.
 */
@Serializable
data class CarCostBackup(
    /** Поднимать при несовместимом изменении формата */
    val formatVersion: Int = FORMAT_VERSION,
    val createdAt: Long,
    val cars: List<Car> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val reminders: List<MaintenanceReminder> = emptyList(),
    val plannedExpenses: List<PlannedExpense> = emptyList(),
    val documents: List<CarDocument> = emptyList(),
    val insurancePolicies: List<InsurancePolicy> = emptyList(),
    val incidents: List<CarIncident> = emptyList(),
    val budgets: List<CategoryBudget> = emptyList(),
    val savingsGoals: List<SavingsGoal> = emptyList(),
    val fluidLevels: List<FluidLevel> = emptyList()
) {
    companion object {
        const val FORMAT_VERSION = 1
    }

    /**
     * Человеческая сводка резервной копии.
     *
     * Функция с контекстом, а не свойство: подписи живут в ресурсах, а у
     * данных резервной копии своего контекста нет и быть не должно — это
     * простой набор записей, который сериализуется в файл.
     */
    fun summary(context: Context): String =
        buildList {
            if (cars.isNotEmpty()) add(context.getString(R.string.backup_avto, cars.size))
            if (expenses.isNotEmpty()) add(context.getString(R.string.backup_rashodov, expenses.size))
            if (reminders.isNotEmpty()) add(context.getString(R.string.backup_napominaniy, reminders.size))
            if (plannedExpenses.isNotEmpty()) add(context.getString(R.string.backup_planov, plannedExpenses.size))
            if (documents.isNotEmpty()) add(context.getString(R.string.backup_dokumentov, documents.size))
            if (insurancePolicies.isNotEmpty()) add(context.getString(R.string.backup_strahovok, insurancePolicies.size))
            if (incidents.isNotEmpty()) add(context.getString(R.string.backup_intsidentov, incidents.size))
            if (budgets.isNotEmpty()) add(context.getString(R.string.backup_byudzhetov, budgets.size))
            if (savingsGoals.isNotEmpty()) add(context.getString(R.string.backup_tseley, savingsGoals.size))
            if (fluidLevels.isNotEmpty()) add(context.getString(R.string.backup_zamerov_zhidkostey, fluidLevels.size))
        }.joinToString(", ").ifEmpty { context.getString(R.string.backup_pusto) }
}

class BackupService(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true   // бэкап из более новой версии не должен ронять импорт
        encodeDefaults = true
    }

    /** Собирает бэкап и пишет его во временный файл, готовый к share */
    suspend fun createBackupFile(): File = withContext(Dispatchers.IO) {
        val backup = collect()
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault())
            .format(java.util.Date())
        val file = File(context.cacheDir, "carcost_backup_$stamp.json")
        file.writeText(json.encodeToString(CarCostBackup.serializer(), backup))
        Log.d(TAG, "Backup written: ${backup.summary(context)}")
        file
    }

    private suspend fun collect(): CarCostBackup {
        val cars = db.carDao().getAllCars().first()

        val expenses = mutableListOf<Expense>()
        val reminders = mutableListOf<MaintenanceReminder>()
        val planned = mutableListOf<PlannedExpense>()
        val documents = mutableListOf<CarDocument>()
        val policies = mutableListOf<InsurancePolicy>()
        val incidents = mutableListOf<CarIncident>()
        val budgets = mutableListOf<CategoryBudget>()
        val goals = mutableListOf<SavingsGoal>()
        val fluids = mutableListOf<FluidLevel>()

        for (car in cars) {
            expenses += db.expenseDao().getExpensesByCarIdSync(car.id)
            reminders += db.maintenanceReminderDao().getAllRemindersByCarId(car.id).first()
            planned += db.plannedExpenseDao().getPlannedExpensesByCarId(car.id).first()
            documents += db.carDocumentDao().getDocumentsByCarIdSync(car.id)
            policies += db.insurancePolicyDao().getPoliciesForCarSync(car.id)
            incidents += db.carIncidentDao().getIncidentsByCarIdSync(car.id)
            budgets += db.categoryBudgetDao().getAllForCarSync(car.id)
            goals += db.savingsGoalDao().getGoalsByCarIdSync(car.id)
            fluids += db.fluidLevelDao().getFluidLevelsByCarIdSync(car.id)
        }

        return CarCostBackup(
            createdAt = System.currentTimeMillis(),
            cars = cars,
            expenses = expenses,
            reminders = reminders,
            plannedExpenses = planned,
            documents = documents,
            insurancePolicies = policies,
            incidents = incidents,
            budgets = budgets,
            savingsGoals = goals,
            fluidLevels = fluids
        )
    }

    /**
     * Читает и разбирает файл, НИЧЕГО не записывая — чтобы показать пользователю,
     * что именно он собирается восстановить, до того как это произойдёт.
     */
    suspend fun peek(uri: Uri): Result<CarCostBackup> = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().decodeToString()
            } ?: return@withContext Result.failure(Exception(context.getString(R.string.chat_ne_udalos_otkryt_fayl)))

            val backup = json.decodeFromString(CarCostBackup.serializer(), text)

            if (backup.formatVersion > CarCostBackup.FORMAT_VERSION) {
                return@withContext Result.failure(
                    Exception(context.getString(R.string.backup_fayl_sozdan_bolee_novoy_versiey))
                )
            }
            Result.success(backup)
        } catch (e: Exception) {
            Log.w(TAG, "Backup parse failed", e)
            Result.failure(
                Exception(context.getString(R.string.backup_eto_ne_rezervnaya_kopiya_carcost_starye))
            )
        }
    }

    /**
     * Применяет бэкап. Вставка идёт по id с REPLACE, поэтому повторный импорт
     * того же файла не создаёт дублей, а обновляет записи.
     *
     * Порядок важен: автомобили первыми, у остальных сущностей на них внешний ключ.
     */
    suspend fun restore(backup: CarCostBackup): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            com.aggin.carcost.data.analytics.Analytics.backupRestored()
            backup.cars.forEach { db.carDao().insertCar(it) }

            val knownCars = db.carDao().getAllCars().first().map { it.id }.toSet()
            // Записи без своего автомобиля вставить нельзя — упрутся в внешний ключ
            fun <T> ownedOnly(items: List<T>, carIdOf: (T) -> String) =
                items.filter { carIdOf(it) in knownCars }

            ownedOnly(backup.expenses) { it.carId }.forEach { db.expenseDao().insertExpense(it) }
            ownedOnly(backup.reminders) { it.carId }.forEach { db.maintenanceReminderDao().insertReminder(it) }
            ownedOnly(backup.plannedExpenses) { it.carId }.forEach { db.plannedExpenseDao().insertPlannedExpense(it) }
            ownedOnly(backup.documents) { it.carId }.forEach { db.carDocumentDao().insertDocument(it) }
            ownedOnly(backup.insurancePolicies) { it.carId }.forEach { db.insurancePolicyDao().insert(it) }
            ownedOnly(backup.incidents) { it.carId }.forEach { db.carIncidentDao().insertIncident(it) }
            ownedOnly(backup.budgets) { it.carId }.forEach { db.categoryBudgetDao().insertBudget(it) }
            ownedOnly(backup.savingsGoals) { it.carId }.forEach { db.savingsGoalDao().insert(it) }
            ownedOnly(backup.fluidLevels) { it.carId }.forEach { db.fluidLevelDao().insert(it) }

            Log.d(TAG, "Backup restored: ${backup.summary(context)}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            Result.failure(Exception(context.getString(R.string.backup_ne_udalos_vosstanovit, e.message)))
        }
    }
}
