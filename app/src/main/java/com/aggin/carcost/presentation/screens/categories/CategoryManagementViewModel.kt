package com.aggin.carcost.presentation.screens.categories

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.ExpenseTag
import com.aggin.carcost.data.local.database.entities.TagWithExpenseCount
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseExpenseTagRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CategoryManagementUiState(
    val tags: List<TagWithExpenseCount> = emptyList(),
    val isLoading: Boolean = true
)

class CategoryManagementViewModel(application: Application) : AndroidViewModel(application) {

    private val tagDao = AppDatabase.getDatabase(application).expenseTagDao()
    private val supabaseAuth = SupabaseAuthRepository()
    private val supabaseTagRepo = SupabaseExpenseTagRepository(supabaseAuth) // ✅ ДОБАВЛЕНО

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

            // 1. Сохраняем локально
            tagDao.insertTag(tag)
            Log.d("CategoryManagement", "✅ Tag saved locally: ${tag.id}")

            // 2. ✅ СИНХРОНИЗИРУЕМ С SUPABASE
            try {
                val result = supabaseTagRepo.insertTag(tag)
                result.fold(
                    onSuccess = {
                        Log.d("CategoryManagement", "✅ Tag synced to Supabase: ${tag.id}")
                    },
                    onFailure = { error ->
                        Log.e("CategoryManagement", "❌ Failed to sync tag to Supabase", error)
                    }
                )
            } catch (e: Exception) {
                Log.e("CategoryManagement", "Exception syncing tag", e)
            }
        }
    }

    fun updateTag(tagId: String, name: String, color: String) { // ✅ String UUID
        val userId = supabaseAuth.getUserId() ?: return
        viewModelScope.launch {
            // Получаем текущий тег
            val currentTag = _uiState.value.tags.find { it.id == tagId } ?: return@launch

            val updatedTag = ExpenseTag(
                id = tagId, // ✅ String UUID
                name = name.trim(),
                color = color,
                userId = userId,
                createdAt = currentTag.createdAt
            )

            // 1. Обновляем локально
            tagDao.insertTag(updatedTag)
            Log.d("CategoryManagement", "✅ Tag updated locally: ${updatedTag.id}")

            // 2. ✅ СИНХРОНИЗИРУЕМ С SUPABASE
            try {
                val result = supabaseTagRepo.updateTag(updatedTag)
                result.fold(
                    onSuccess = {
                        Log.d("CategoryManagement", "✅ Tag updated in Supabase: ${updatedTag.id}")
                    },
                    onFailure = { error ->
                        Log.e("CategoryManagement", "❌ Failed to update tag in Supabase", error)
                    }
                )
            } catch (e: Exception) {
                Log.e("CategoryManagement", "Exception updating tag", e)
            }
        }
    }

    fun deleteTag(tagToDelete: TagWithExpenseCount) {
        viewModelScope.launch {
            val tag = ExpenseTag(
                id = tagToDelete.id, // ✅ String UUID
                name = tagToDelete.name,
                color = tagToDelete.color,
                userId = tagToDelete.userId,
                createdAt = tagToDelete.createdAt
            )

            // 1. Удаляем локально
            tagDao.deleteTag(tag)
            Log.d("CategoryManagement", "✅ Tag deleted locally: ${tag.id}")

            // 2. ✅ СИНХРОНИЗИРУЕМ С SUPABASE
            try {
                val result = supabaseTagRepo.deleteTag(tag.id)
                result.fold(
                    onSuccess = {
                        Log.d("CategoryManagement", "✅ Tag deleted from Supabase: ${tag.id}")
                    },
                    onFailure = { error ->
                        Log.e("CategoryManagement", "❌ Failed to delete tag from Supabase", error)
                    }
                )
            } catch (e: Exception) {
                Log.e("CategoryManagement", "Exception deleting tag", e)
            }
        }
    }
}