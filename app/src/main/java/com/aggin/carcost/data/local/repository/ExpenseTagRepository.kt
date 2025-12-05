package com.aggin.carcost.data.local.repository

import com.aggin.carcost.data.local.database.dao.ExpenseTagDao
import com.aggin.carcost.data.local.database.entities.ExpenseTag
import com.aggin.carcost.data.local.database.entities.TagWithExpenseCount
import kotlinx.coroutines.flow.Flow

class ExpenseTagRepository(private val dao: ExpenseTagDao) {

    // Метод для экрана управления тегами с подсчётом
    fun getTagsWithCount(userId: String): Flow<List<TagWithExpenseCount>> {
        return dao.getTagsWithExpenseCount(userId)
    }

    // Метод для получения простого списка тегов для фильтра
    fun getTagsByUser(userId: String): Flow<List<ExpenseTag>> {
        return dao.getAllTags(userId)
    }

    // Метод для получения тегов конкретного расхода
    fun getTagsForExpense(expenseId: String): Flow<List<ExpenseTag>> { // ✅ String UUID
        return dao.getTagsForExpense(expenseId)
    }

    // Добавление нового тега
    suspend fun insertTag(tag: ExpenseTag): String { // ✅ Возвращает String UUID
        dao.insertTag(tag)
        return tag.id
    }

    // Удаление тега
    suspend fun deleteTag(tag: ExpenseTag) {
        dao.deleteTag(tag)
    }
}