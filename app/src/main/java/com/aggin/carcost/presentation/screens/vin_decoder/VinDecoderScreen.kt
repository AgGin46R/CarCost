package com.aggin.carcost.presentation.screens.vin_decoder

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.VinCache
import com.aggin.carcost.data.remote.api.NhtsaApiService
import com.aggin.carcost.data.remote.api.toVinInfo
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class VinDecoderUiState(
    val isLoading: Boolean = false,
    val vinInfo: Map<String, String> = emptyMap(),
    val error: String? = null,
    val recentSearches: List<VinCache> = emptyList()
)

class VinDecoderViewModel(application: Application) : AndroidViewModel(application) {
    private val vinCacheDao = AppDatabase.getDatabase(application).vinCacheDao()
    private val nhtsaApi = NhtsaApiService.create()

    private val _uiState = MutableStateFlow(VinDecoderUiState())
    val uiState: StateFlow<VinDecoderUiState> = _uiState.asStateFlow()

    fun decodeVin(vin: String) {
        if (vin.length < 17) {
            _uiState.update { it.copy(error = "VIN должен содержать 17 символов") }
            return
        }

        viewModelScope.launch {
            // Check cache first
            val cached = vinCacheDao.getByVin(vin.uppercase())
            if (cached != null) {
                val info = buildMap {
                    cached.make?.let { put("make", it) }
                    cached.model?.let { put("model", it) }
                    cached.year?.let { put("year", it) }
                    cached.engine?.let { put("engine", it) }
                    cached.country?.let { put("country", it) }
                }
                _uiState.update { it.copy(vinInfo = info, error = null, isLoading = false) }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, error = null, vinInfo = emptyMap()) }

            try {
                val response = nhtsaApi.decodeVin(vin.uppercase())
                val info = response.toVinInfo()

                if (info.isEmpty() || info.values.all { it.isBlank() }) {
                    _uiState.update { it.copy(isLoading = false, error = "Нет данных для этого VIN. NHTSA база данных содержит только автомобили для рынка США.") }
                    return@launch
                }

                // Save to cache
                vinCacheDao.insert(VinCache(
                    vin = vin.uppercase(),
                    make = info["make"],
                    model = info["model"],
                    year = info["year"],
                    engine = info["engine"],
                    country = info["country"]
                ))

                _uiState.update { it.copy(isLoading = false, vinInfo = info, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Ошибка запроса: ${e.message}") }
            }
        }
    }
}

class VinDecoderViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return VinDecoderViewModel(app) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VinDecoderScreen(
    carId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val viewModel: VinDecoderViewModel = viewModel(
        factory = VinDecoderViewModelFactory(context.applicationContext as Application)
    )
    val uiState by viewModel.uiState.collectAsState()
    var vinInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VIN-декодер") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = vinInput,
                    onValueChange = { if (it.length <= 17) vinInput = it.uppercase() },
                    label = { Text("VIN номер (17 символов)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.decodeVin(vinInput) }),
                    trailingIcon = {
                        if (vinInput.isNotEmpty()) {
                            IconButton(onClick = { viewModel.decodeVin(vinInput) }) {
                                Icon(Icons.Default.Search, null)
                            }
                        }
                    },
                    supportingText = { Text("${vinInput.length}/17") }
                )
            }

            item {
                Button(
                    onClick = { viewModel.decodeVin(vinInput) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = vinInput.length == 17 && !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Декодировать VIN")
                    }
                }
            }

            if (uiState.error != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Text(uiState.error!!, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            if (uiState.vinInfo.isNotEmpty()) {
                item {
                    Text("Результат", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                items(uiState.vinInfo.entries.toList()) { (key, value) ->
                    val label = when (key) {
                        "make" -> "Марка"
                        "model" -> "Модель"
                        "year" -> "Год"
                        "engine" -> "Двигатель"
                        "fuelType" -> "Тип топлива"
                        "country" -> "Страна производства"
                        "vehicleType" -> "Тип кузова"
                        else -> key
                    }
                    VinInfoRow(label = label, value = value)
                }
            }
        }
    }
}

@Composable
private fun VinInfoRow(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}
