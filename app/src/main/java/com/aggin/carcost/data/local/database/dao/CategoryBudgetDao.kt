package com.aggin.carcost.data.local.database.dao

import androidx.room.*
import com.aggin.carcost.data.local.database.entities.CategoryBudget
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryBudgetDao {

    @Query("SELECT * FROM category_budgets WHERE carId = :carId AND month = :month AND year = :year")
    fun getBudgetsByCarIdAndPeriod(carId: String, month: Int, year: Int): Flow<List<CategoryBudget>>

    @Query("SELECT * FROM category_budgets WHERE carId = :carId AND month = :month AND year = :year")
    suspend fun getBudgetsSync(carId: String, month: Int, year: Int): List<CategoryBudget>

    @Query("SELECT * FROM category_budgets WHERE carId = :carId")
    suspend fun getAllForCarSync(carId: String): List<CategoryBudget>

    @Query("SELECT * FROM category_budgets WHERE carId = :carId AND category = :category AND month = :month AND year = :year LIMIT 1")
    suspend fun getBudget(carId: String, category: ExpenseCategory, month: Int, year: Int): CategoryBudget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: CategoryBudget)

    @Update
    suspend fun updateBudget(budget: CategoryBudget)

    @Query("DELETE FROM category_budgets WHERE id = :id")
    suspend fun deleteBudget(id: String)

    @Query("DELETE FROM category_budgets WHERE carId = :carId AND category = :category AND month = :month AND year = :year")
    suspend fun deleteBudgetByCategory(carId: String, category: ExpenseCategory, month: Int, year: Int)
}
