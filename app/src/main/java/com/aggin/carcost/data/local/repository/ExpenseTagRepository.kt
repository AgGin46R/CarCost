package com.aggin.carcost.data.local.repository

import com.aggin.carcost.data.local.database.dao.ExpenseTagDao
import com.aggin.carcost.data.local.database.entities.ExpenseTag
import com.aggin.carcost.data.local.database.entities.TagWithExpenseCount
import kotlinx.coroutines.flow.Flow

class ExpenseTagRepository(private val dao: ExpenseTagDao) {

    // Этот метод нужен для экрана управления тегами
    fun getTagsWithCount(userId: String): Flow<List<TagWithExpenseCount>> {
        return dao.getTagsWithExpenseCount(userId)
    }

    // --- ИСПРАВЛЕНИЕ ЗДЕСЬ ---
    // Этот метод нужен для ViewModel, чтобы получить простой список тегов для фильтра
    fun getTagsByUser(userId: String): Flow<List<ExpenseTag>> {
        // Вызываем правильный метод из DAO - getAllTags
        return dao.getAllTags(userId)
    }
    // --- КОНЕЦ ИСПРАВЛЕНИЯ ---

    // Этот метод понадобится для фильтрации по тегам в будущем
    fun getTagsForExpense(expenseId: Long): Flow<List<ExpenseTag>> {
        return dao.getTagsForExpense(expenseId)
    }
}