package com.aggin.carcost.presentation.screens.recalls

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.remote.api.NhtsaRecall
import com.aggin.carcost.data.remote.api.NhtsaRecallApiService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RecallsUiState(
    val isLoading: Boolean = false,
    val recalls: List<NhtsaRecall> = emptyList(),
    val error: String? = null,
    val carInfo: String = ""
)

class RecallsViewModel(
    application: Application,
    private val carId: String
) : AndroidViewModel(application) {
    private val carDao = AppDatabase.getDatabase(application).carDao()
    private val recallApi = NhtsaRecallApiService.create()

    private val _uiState = MutableStateFlow(RecallsUiState())
    val uiState: StateFlow<RecallsUiState> = _uiState.asStateFlow()

    init {
        loadRecalls()
    }

    private fun loadRecalls() {
        viewModelScope.launch {
            val car = carDao.getCarById(carId) ?: return@launch
            val carInfo = "${car.brand} ${car.model} ${car.year}"
            _uiState.update { it.copy(isLoading = true, carInfo = carInfo) }

            try {
                val response = recallApi.getRecalls(
                    make = car.brand,
                    model = car.model,
                    year = car.year
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        recalls = response.results ?: emptyList(),
                        error = if (response.results.isNullOrEmpty())
                            "Отзывов для $carInfo не найдено в базе NHTSA.\nПримечание: база NHTSA содержит данные только для автомобилей рынка США."
                        else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Ошибка запроса: ${e.message}") }
            }
        }
    }
}

class RecallsViewModelFactory(
    private val app: Application,
    private val carId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return RecallsViewModel(app, carId) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecallsScreen(
    carId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val viewModel: RecallsViewModel = viewModel(
        factory = RecallsViewModelFactory(context.applicationContext as Application, carId)
    )
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Отзывы и жалобы")
                        if (uiState.carInfo.isNotEmpty()) {
                            Text(uiState.carInfo, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                Alignment.Center
            ) { CircularProgressIndicator() }

            uiState.error != null -> Box(
                Modifier.fillMaxSize().padding(padding),
                Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null,
                            Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(uiState.error!!, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }

            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D0D0D))
                    ) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, null,
                                tint = Color(0xFFFFB74D), modifier = Modifier.size(20.dp))
                            Text(
                                "Найдено ${uiState.recalls.size} отзывов/жалоб (база NHTSA, только США)",
                                fontSize = 13.sp, color = Color(0xFFFFB74D)
                            )
                        }
                    }
                }

                items(uiState.recalls) { recall ->
                    RecallCard(recall)
                }
            }
        }
    }
}

@Composable
private fun RecallCard(recall: NhtsaRecall) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null,
                    Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                Text(
                    recall.component ?: "Неизвестный компонент",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
            if (!recall.summary.isNullOrBlank()) {
                Text(recall.summary, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!recall.consequence.isNullOrBlank()) {
                Text("Последствия: ${recall.consequence}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
            if (!recall.remedy.isNullOrBlank()) {
                Text("Решение: ${recall.remedy}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            recall.campaignNumber?.let {
                Text("№ кампании: $it", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
