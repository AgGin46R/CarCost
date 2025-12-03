package com.aggin.carcost.presentation.screens.categories

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.ExpenseTag
import com.aggin.carcost.data.local.database.entities.TagWithExpenseCount
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CategoryManagementUiState(
    val tags: List<TagWithExpenseCount> = emptyList(),
    val isLoading: Boolean = true
)

class CategoryManagementViewModel(application: Application) : AndroidViewModel(application) {

    private val tagDao = AppDatabase.getDatabase(application).expenseTagDao()
    private val supabaseAuth = SupabaseAuthRepository()

    private val _uiState = MutableStateFlow(CategoryManagementUiState())
    val uiState: StateFlow<CategoryManagementUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val userId = supabaseAuth.getUserId() ?: return

        viewModelScope.launch {
            tagDao.getTagsWithExpenseCount(userId).collect { tags ->
                _uiState.update { it.copy(tags = tags, isLoading = false) }
            }
        }
    }

    fun addTag(name: String, color: String) {
        val userId = supabaseAuth.getUserId() ?: return
        viewModelScope.launch {
            val tag = ExpenseTag(
                name = name.trim(),
                color = color,
                userId = userId
            )
            tagDao.insertTag(tag)
        }
    }

    fun updateTag(tagId: Long, name: String, color: String) {
        val userId = supabaseAuth.getUserId() ?: return
        viewModelScope.launch {
            // Получаем текущий тег
            val currentTag = _uiState.value.tags.find { it.id == tagId } ?: return@launch

            val updatedTag = ExpenseTag(
                id = tagId,
                name = name.trim(),
                color = color,
                userId = userId,
                createdAt = currentTag.createdAt
            )
            tagDao.insertTag(updatedTag)
        }
    }

    fun deleteTag(tagToDelete: TagWithExpenseCount) {
        viewModelScope.launch {
            val tag = ExpenseTag(
                id = tagToDelete.id,
                name = tagToDelete.name,
                color = tagToDelete.color,
                userId = tagToDelete.userId,
                createdAt = tagToDelete.createdAt
            )
            tagDao.deleteTag(tag)
        }
    }
}