package com.aggin.carcost.data.local.database.dao

import androidx.room.*
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    // CREATE
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense) // ✅ Void - не возвращает ID

    /**
     * Запись расхода, пришедшего с сервера.
     *
     * Через REPLACE строка расхода удалялась и вставлялась заново, а от неё
     * каскадом висит expense_tag_cross_ref — привязки тегов. Поэтому теги
     * пропадали с расхода при каждой синхронизации и возвращались, только если
     * связь успела уехать на сервер и вернуться оттуда. Отсюда «то есть, то
     * нет» и нули в подсчёте по тегам.
     */
    @Upsert
    suspend fun upsertExpense(expense: Expense)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<Expense>)

    // READ
    /**
     * Сумма всех расходов по всем автомобилям — потоком.
     *
     * Экран профиля раньше считал её так: брал список машин, по каждой поднимал
     * в память ВСЕ расходы и складывал. Ради одного числа. Хуже того, весь этот
     * расчёт сидел внутри подписки на таблицу ПОЛЬЗОВАТЕЛЕЙ, а она при добавлении
     * расхода не меняется — поэтому сумма застывала: при первом открытии, пока
     * локальная база пуста, показывался ноль, и он оставался нулём, сколько бы
     * записей человек ни внёс.
     *
     * Локальная база содержит данные только текущего пользователя (при выходе
     * очищается целиком), поэтому условие по владельцу здесь не нужно.
     */
    @Query("SELECT SUM(amount) FROM expenses")
    fun getTotalAmountOfAllExpenses(): Flow<Double?>

    @Query("SELECT * FROM expenses WHERE id = :expenseId")
    suspend fun getExpenseById(expenseId: String): Expense? // ✅ String UUID

    @Query("SELECT * FROM expenses WHERE carId = :carId")
    fun getExpensesByCar(carId: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE carId = :carId ORDER BY date DESC")
    fun getExpensesByCarId(carId: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE carId = :carId AND category = :category ORDER BY date DESC")
    fun getExpensesByCategory(carId: String, category: ExpenseCategory): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE carId = :carId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getExpensesInDateRange(carId: String, startDate: Long, endDate: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE carId = :carId ORDER BY date DESC")
    suspend fun getExpensesByCarIdSync(carId: String): List<Expense>

    @Query("SELECT * FROM expenses WHERE carId = :carId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getExpensesInDateRangeSync(carId: String, startDate: Long, endDate: Long): List<Expense>

    @Query("SELECT * FROM expenses WHERE carId = :carId ORDER BY date DESC LIMIT :limit")
    fun getRecentExpenses(carId: String, limit: Int = 10): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE carId = :carId AND category = :category ORDER BY date DESC LIMIT 1")
    fun getLastExpenseByCategory(carId: String, category: ExpenseCategory): Flow<Expense?>

    // STATISTICS
    @Query("SELECT SUM(amount) FROM expenses WHERE carId = :carId")
    fun getTotalExpenses(carId: String): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expenses WHERE carId = :carId AND category = :category")
    fun getTotalExpensesByCategory(carId: String, category: ExpenseCategory): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expenses WHERE carId = :carId AND date BETWEEN :startDate AND :endDate")
    fun getTotalExpensesInDateRange(carId: String, startDate: Long, endDate: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM expenses WHERE carId = :carId")
    fun getExpenseCount(carId: String): Flow<Int>

    // FUEL-SPECIFIC
    @Query("SELECT * FROM expenses WHERE carId = :carId AND category = 'FUEL' AND isFullTank = 1 ORDER BY date DESC LIMIT :limit")
    fun getFullTankRefuels(carId: String, limit: Int = 10): Flow<List<Expense>>

    // SEARCH
    @Query("""
        SELECT * FROM expenses
        WHERE title LIKE '%' || :query || '%'
           OR description LIKE '%' || :query || '%'
           OR location LIKE '%' || :query || '%'
           OR workshopName LIKE '%' || :query || '%'
           OR CAST(amount AS TEXT) LIKE '%' || :query || '%'
        ORDER BY date DESC
        LIMIT 100
    """)
    suspend fun searchExpenses(query: String): List<Expense>

    /**
     * Поиск с фильтрами — целиком в SQL.
     *
     * Раньше экран поиска на каждое нажатие клавиши поднимал в память ВСЕ
     * расходы всех машин (по запросу на машину) и фильтровал их в Kotlin, хотя
     * до выдачи доходило максимум сто строк. При тысяче записей набор из четырёх
     * букв означал четыре полных выгрузки базы.
     *
     * Пустые фильтры и короткий запрос обходятся флагами: передать пустой список
     * в IN нельзя, а сравнение с NULL в SQL никогда не истинно.
     *
     * @param matchedCategories категории, чьё РУССКОЕ название подошло под запрос
     *        («топливо», «штраф»). Сопоставление имён живёт в Kotlin, в базе
     *        хранится имя enum-константы.
     */
    @Query("""
        SELECT * FROM expenses
        WHERE (:carId IS NULL OR carId = :carId)
          AND (:hasCategoryFilter = 0 OR category IN (:categories))
          AND (:startDate IS NULL OR date >= :startDate)
          AND (:endDate IS NULL OR date <= :endDate)
          AND (:minAmount IS NULL OR amount >= :minAmount)
          AND (:maxAmount IS NULL OR amount <= :maxAmount)
          AND (
                :queryTooShort = 1
             OR lower(title) LIKE '%' || :query || '%'
             OR lower(description) LIKE '%' || :query || '%'
             OR lower(location) LIKE '%' || :query || '%'
             OR lower(workshopName) LIKE '%' || :query || '%'
             OR lower(maintenanceParts) LIKE '%' || :query || '%'
             OR CAST(CAST(amount AS INTEGER) AS TEXT) LIKE '%' || :query || '%'
             OR (:hasMatchedCategories = 1 AND category IN (:matchedCategories))
          )
        ORDER BY date DESC
        LIMIT :limit
    """)
    suspend fun searchWithFilters(
        query: String,
        queryTooShort: Int,
        carId: String?,
        categories: List<ExpenseCategory>,
        hasCategoryFilter: Int,
        matchedCategories: List<ExpenseCategory>,
        hasMatchedCategories: Int,
        startDate: Long?,
        endDate: Long?,
        minAmount: Double?,
        maxAmount: Double?,
        limit: Int
    ): List<Expense>

    @Query("SELECT SUM(amount) FROM expenses WHERE carId = :carId AND category = :category AND date BETWEEN :startDate AND :endDate")
    suspend fun getTotalByCategoryAndPeriod(carId: String, category: ExpenseCategory, startDate: Long, endDate: Long): Double?

    // UPDATE
    @Update
    suspend fun updateExpense(expense: Expense)

    @Update
    suspend fun updateExpenses(expenses: List<Expense>)

    // DELETE
    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpenseById(expenseId: String) // ✅ String UUID

    @Query("DELETE FROM expenses WHERE carId = :carId")
    suspend fun deleteExpensesByCarId(carId: String)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses() // ✅ Удалить все расходы

    /**
     * Наибольший пробег среди записей автомобиля.
     *
     * Пробег указывается в каждом расходе, но на карточку автомобиля он до сих
     * пор попадал только из одного места — экрана добавления, и только если
     * оказывался больше текущего. Запись совладельца, пришедшая с сервера, и
     * правка уже существующего расхода пробег не двигали вовсе. Порядок
     * добавления при этом произвольный: можно внести заправку на 1200, потом
     * вспомнить про мойку на 900 — считать надо по максимуму, а не по
     * последней внесённой.
     */
    @Query("SELECT MAX(odometer) FROM expenses WHERE carId = :carId")
    suspend fun getMaxOdometer(carId: String): Int?

    /**
     * Места, где этот автомобиль уже обслуживали или заправляли.
     *
     * Заправляются люди на одних и тех же трёх-четырёх колонках, а название
     * набирали заново каждый раз — и писали по-разному, отчего по месту потом
     * ничего не сгруппировать. Порядок — по свежести: последняя заправка почти
     * всегда и есть нужная.
     */
    @Query("""
        SELECT location FROM expenses
        WHERE carId = :carId AND location IS NOT NULL AND TRIM(location) != ''
        GROUP BY LOWER(TRIM(location))
        ORDER BY MAX(date) DESC
        LIMIT :limit
    """)
    suspend fun getRecentLocations(carId: String, limit: Int = 8): List<String>
}