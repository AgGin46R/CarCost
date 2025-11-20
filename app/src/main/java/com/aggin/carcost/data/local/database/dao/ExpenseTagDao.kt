package com.aggin.carcost.data.local.database.dao

import androidx.room.*
import com.aggin.carcost.data.local.database.entities.ExpenseTag
import com.aggin.carcost.data.local.database.entities.ExpenseTagCrossRef
import com.aggin.carcost.data.local.database.entities.TagWithExpenseCount
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseTagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: ExpenseTag): Long

    @Delete
    suspend fun deleteTag(tag: ExpenseTag)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenseTagCrossRef(crossRef: ExpenseTagCrossRef)

    @Query("DELETE FROM expense_tag_cross_ref WHERE expenseId = :expenseId")
    suspend fun deleteExpenseTagsByExpenseId(expenseId: Long)

    // Базовый запрос для получения всех тегов
    @Query("SELECT * FROM expense_tags WHERE userId = :userId ORDER BY name ASC")
    fun getAllTags(userId: String): Flow<List<ExpenseTag>>

    // Запрос с подсчетом расходов
    @Query("""
        SELECT 
            t.id as id,
            t.name as name,
            t.color as color,
            t.userId as userId,
            t.createdAt as createdAt,
            COUNT(DISTINCT xt.expenseId) as expenseCount
        FROM expense_tags as t
        LEFT JOIN expense_tag_cross_ref AS xt ON t.id = xt.tagId
        WHERE t.userId = :userId
        GROUP BY t.id, t.name, t.color, t.userId, t.createdAt
        ORDER BY expenseCount DESC, t.name ASC
    """)
    fun getTagsWithExpenseCount(userId: String): Flow<List<TagWithExpenseCount>>

    // Получить теги для конкретного расхода
    @Query("""
        SELECT t.* FROM expense_tags as t
        INNER JOIN expense_tag_cross_ref as xt ON t.id = xt.tagId
        WHERE xt.expenseId = :expenseId
    """)
    fun getTagsForExpense(expenseId: Long): Flow<List<ExpenseTag>>
}