package com.aggin.carcost.presentation.screens.tyres

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.TyreSeason
import com.aggin.carcost.data.local.database.entities.TyreSet
import com.aggin.carcost.data.local.repository.TyreSetRepository
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseTyreSetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Комплект вместе с посчитанным пробегом.
 *
 * Пробег текущего периода зависит от одометра машины, а он меняется отдельно
 * от самого комплекта — поэтому считается здесь, а не хранится в записи.
 */
data class TyreSetItem(
    val set: TyreSet,
    val km: Int,
    val wear: Float?,
    /** Сколько прошло за нынешнюю установку. 0 у снятого комплекта */
    val currentPeriodKm: Int
)

data class TyresUiState(
    val carId: String = "",
    val items: List<TyreSetItem> = emptyList(),
    val currentOdometer: Int = 0,
    val isLoading: Boolean = true,
    /** Комплект, открытый в форме. null — форма закрыта, NEW_SET — новый */
    val editing: TyreSet? = null,
    val showForm: Boolean = false,
    val message: String? = null
)

class TyresViewModel(
    application: Application,
    private val carId: String
) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = TyreSetRepository(db.tyreSetDao(), db.carDao())
    private val supabaseRepo = SupabaseTyreSetRepository(SupabaseAuthRepository())

    private val _editing = MutableStateFlow<TyreSet?>(null)
    private val _message = MutableStateFlow<String?>(null)

    private val odometerFlow = db.carDao().getCarByIdFlow(carId)
        .map { it?.currentOdometer ?: 0 }

    val uiState: StateFlow<TyresUiState> = combine(
        repository.getByCarId(carId),
        odometerFlow,
        _editing,
        _message
    ) { sets, odometer, editing, message ->
        TyresUiState(
            carId = carId,
            items = sets.map { set ->
                TyreSetItem(
                    set = set,
                    km = set.kmWith(odometer),
                    wear = set.wearFraction(odometer),
                    currentPeriodKm = if (set.isInstalled && set.installedAtOdometer != null) {
                        (odometer - set.installedAtOdometer).coerceAtLeast(0)
                    } else 0
                )
            },
            currentOdometer = odometer,
            isLoading = false,
            editing = editing,
            showForm = editing != null,
            message = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TyresUiState(carId = carId)
    )

    init {
        // Подтягиваем с сервера при открытии: комплект мог добавить совладелец
        viewModelScope.launch(Dispatchers.IO) {
            try {
                supabaseRepo.getByCarId(carId).getOrNull()?.forEach { set ->
                    repository.saveFromServer(set)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось загрузить шины с сервера", e)
            }
        }
    }

    fun startNew() {
        _editing.value = TyreSet(carId = carId, name = "")
    }

    fun startEdit(set: TyreSet) {
        _editing.value = set
    }

    fun dismissForm() {
        _editing.value = null
    }

    fun clearMessage() {
        _message.value = null
    }

    fun save(set: TyreSet) {
        if (set.name.isBlank()) return
        viewModelScope.launch {
            repository.save(set)
            _editing.value = null
            push(set.id)
        }
    }

    fun install(setId: String) {
        viewModelScope.launch {
            // Предыдущий комплект снимается внутри репозитория — его тоже
            // нужно отправить на сервер, иначе у совладельца останутся
            // установленными сразу два
            val previous = db.tyreSetDao().getInstalled(carId)
            if (repository.install(setId)) {
                // Сначала снятый, потом установленный, и строго по очереди:
                // при обратном порядке совладелец в момент между двумя
                // отправками видит два установленных комплекта
                previous?.let { if (it.id != setId) push(it.id) }
                push(setId)
            }
        }
    }

    fun uninstall(setId: String) {
        viewModelScope.launch {
            if (repository.uninstall(setId)) push(setId)
        }
    }

    fun delete(set: TyreSet) {
        viewModelScope.launch {
            repository.delete(set)
            launch(Dispatchers.IO) {
                try {
                    supabaseRepo.deleteById(set.id)
                } catch (e: Exception) {
                    Log.w(TAG, "Не удалось удалить комплект на сервере", e)
                }
            }
        }
    }

    /**
     * Отправка на сервер с отметкой о подтверждении.
     *
     * Отметка ставится только при успехе: без неё запись считается
     * неотправленной, и следующая синхронизация попробует снова. Поставить её
     * заранее — значит потом принять запись за удалённую совладельцем и стереть.
     */
    private suspend fun push(setId: String) = withContext(Dispatchers.IO) {
        try {
            val fresh = repository.getById(setId) ?: return@withContext
            supabaseRepo.upsert(fresh).onSuccess {
                repository.markSynced(setId)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось отправить комплект на сервер", e)
        }
    }

    companion object {
        private const val TAG = "TyresViewModel"

        /** Сезоны в порядке показа в форме */
        val SEASONS = listOf(TyreSeason.SUMMER, TyreSeason.WINTER, TyreSeason.ALL_SEASON)
    }
}
