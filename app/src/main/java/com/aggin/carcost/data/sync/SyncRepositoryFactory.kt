package com.aggin.carcost.data.sync

import android.content.Context
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.repository.CarRepository
import com.aggin.carcost.data.local.repository.ExpenseRepository
import com.aggin.carcost.data.local.repository.ExpenseTagRepository
import com.aggin.carcost.data.local.repository.MaintenanceReminderRepository
import com.aggin.carcost.data.local.repository.PlannedExpenseRepository
import com.aggin.carcost.data.remote.repository.SupabaseAchievementRepository
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarDocumentRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarIncidentRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarRepository
import com.aggin.carcost.data.remote.repository.SupabaseCategoryBudgetRepository
import com.aggin.carcost.data.remote.repository.SupabaseExpenseRepository
import com.aggin.carcost.data.remote.repository.SupabaseExpenseTagRepository
import com.aggin.carcost.data.remote.repository.SupabaseFluidLevelRepository
import com.aggin.carcost.data.remote.repository.SupabaseGpsTripRepository
import com.aggin.carcost.data.remote.repository.SupabaseInsurancePolicyRepository
import com.aggin.carcost.data.remote.repository.SupabaseMaintenanceReminderRepository
import com.aggin.carcost.data.remote.repository.SupabasePlannedExpenseRepository
import com.aggin.carcost.data.remote.repository.SupabaseSavingsGoalRepository

/**
 * Сборка [SyncRepository] в одном месте.
 *
 * У него 21 зависимость, и раньше этот блок был скопирован в LoginViewModel,
 * RegisterViewModel и ProfileViewModel — забыть передать один из опциональных
 * репозиториев означало молча отключить синхронизацию целого раздела данных.
 *
 * Полноценный DI это не заменяет, но убирает копипасту и делает набор
 * зависимостей одинаковым во всех точках.
 */
object SyncRepositoryFactory {

    fun create(
        context: Context,
        database: AppDatabase = AppDatabase.getDatabase(context),
        auth: SupabaseAuthRepository = SupabaseAuthRepository()
    ): SyncRepository = SyncRepository(
        localCarRepo = CarRepository(database.carDao()),
        localExpenseRepo = ExpenseRepository(database.expenseDao()),
        localReminderRepo = MaintenanceReminderRepository(database.maintenanceReminderDao()),
        localTagRepo = ExpenseTagRepository(database.expenseTagDao()),
        localTagDao = database.expenseTagDao(),
        localPlannedExpenseRepo = PlannedExpenseRepository(database.plannedExpenseDao()),
        supabaseAuthRepo = auth,
        supabaseCarRepo = SupabaseCarRepository(auth),
        supabaseExpenseRepo = SupabaseExpenseRepository(auth),
        supabaseReminderRepo = SupabaseMaintenanceReminderRepository(auth),
        supabaseTagRepo = SupabaseExpenseTagRepository(auth),
        supabasePlannedExpenseRepo = SupabasePlannedExpenseRepository(auth),
        localDb = database,
        supabaseFluidLevelRepo = SupabaseFluidLevelRepository(auth),
        supabaseGpsTripRepo = SupabaseGpsTripRepository(auth),
        supabaseIncidentRepo = SupabaseCarIncidentRepository(auth),
        supabaseInsuranceRepo = SupabaseInsurancePolicyRepository(auth),
        supabaseSavingsGoalRepo = SupabaseSavingsGoalRepository(auth),
        supabaseCategoryBudgetRepo = SupabaseCategoryBudgetRepository(auth),
        supabaseCarDocumentRepo = SupabaseCarDocumentRepository(auth),
        supabaseAchievementRepo = SupabaseAchievementRepository(auth)
    )
}
