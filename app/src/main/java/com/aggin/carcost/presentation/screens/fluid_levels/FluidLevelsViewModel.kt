package com.aggin.carcost.presentation.screens.fluid_levels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.FluidLevel
import com.aggin.carcost.data.local.database.entities.FluidType
import com.aggin.carcost.data.local.repository.FluidLevelRepository
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseFluidLevelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FluidLevelItem(
    val type: FluidType,
    val existing: FluidLevel?,       // null = ещё не проверялась
    val isOverdue: Boolean            // true = последняя проверка давно
)

data class FluidLevelsUiState(
    val carId: String = "",
    val items: List<FluidLevelItem> = emptyList(),
    val isLoading: Boolean = true,
    val showUpdateDialog: Boolean = false,
    val dialogFluidType: FluidType? = null
)

class FluidLevelsViewModel(
    application: Application,
    private val carId: String
) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = FluidLevelRepository(db.fluidLevelDao())
    private val supabaseRepo = SupabaseFluidLevelRepository(SupabaseAuthRepository())

    private val _dialogState = MutableStateFlow<FluidType?>(null)

    val uiState: StateFlow<FluidLevelsUiState> = combine(
        repository.getFluidLevelsByCarId(carId),
        _dialogState
    ) { levels, dialogType ->
        val now = System.currentTimeMillis()
        val items = FluidType.values().map { type ->
            val existing = levels.firstOrNull { it.type == type }
            val isOverdue = existing == null ||
                (now - existing.checkedAt) > (type.checkIntervalDays * 86_400_000L)
            FluidLevelItem(type = type, existing = existing, isOverdue = isOverdue)
        }
        FluidLevelsUiState(
            carId = carId,
            items = items,
            isLoading = false,
            showUpdateDialog = dialogType != null,
            dialogFluidType = dialogType
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FluidLevelsUiState(carId = carId)
    )

    init {
        // Синхронизируем данные из Supabase при открытии экрана
        viewModelScope.launch(Dispatchers.IO) {
            try {
                supabaseRepo.getFluidLevelsByCarId(carId).getOrNull()?.forEach { level ->
                    repository.upsert(level)
                }
            } catch (e: Exception) {
                Log.w("FluidLevelsVM", "Supabase sync on load failed", e)
            }
        }
    }

    fun openUpdateDialog(type: FluidType) {
        _dialogState.value = type
    }

    fun dismissDialog() {
        _dialogState.value = null
    }

    fun saveFluidLevel(type: FluidType, level: Float, notes: String) {
        viewModelScope.launch {
            val existing = repository.getLatestFluidLevel(carId, type)
            val entry = FluidLevel(
                id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                carId = carId,
                type = type,
                level = level.coerceIn(0f, 1f),
                checkedAt = System.currentTimeMillis(),
                notes = notes.ifBlank { null },
                updatedAt = System.currentTimeMillis()
            )
            // Сохраняем в Room (немедленно)
            repository.upsert(entry)
            _dialogState.value = null

            // Синхронизируем с Supabase в фоне
            launch(Dispatchers.IO) {
                try {
                    supabaseRepo.upsertFluidLevel(entry)
                } catch (e: Exception) {
                    Log.w("FluidLevelsVM", "Supabase upsert failed", e)
                }
            }
        }
    }
}
