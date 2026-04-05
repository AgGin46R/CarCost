package com.aggin.carcost.presentation.screens.fuel_prices

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.FuelPrice
import com.aggin.carcost.data.local.database.entities.FuelGradeType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ViewModel
data class FuelPricesUiState(
    val prices: List<FuelPrice> = emptyList(),
    val isLoading: Boolean = true,
    val showAddDialog: Boolean = false
)

class FuelPricesViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).fuelPriceDao()

    private val _uiState = MutableStateFlow(FuelPricesUiState())
    val uiState: StateFlow<FuelPricesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dao.getAllFuelPrices().collect { list ->
                _uiState.update { it.copy(prices = list, isLoading = false) }
            }
        }
    }

    fun addPrice(stationName: String, fuelType: FuelGradeType, price: Double) {
        viewModelScope.launch {
            dao.insert(FuelPrice(stationName = stationName, fuelType = fuelType, pricePerLiter = price))
            _uiState.update { it.copy(showAddDialog = false) }
        }
    }

    fun deletePrice(price: FuelPrice) {
        viewModelScope.launch { dao.delete(price) }
    }

    fun showDialog() = _uiState.update { it.copy(showAddDialog = true) }
    fun hideDialog() = _uiState.update { it.copy(showAddDialog = false) }
}

class FuelPricesViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return FuelPricesViewModel(app) as T
    }
}

// Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelPricesScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: FuelPricesViewModel = viewModel(
        factory = FuelPricesViewModelFactory(context.applicationContext as Application)
    )
    val uiState by viewModel.uiState.collectAsState()
    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    if (uiState.showAddDialog) {
        AddFuelPriceDialog(
            onConfirm = { station, type, price -> viewModel.addPrice(station, type, price) },
            onDismiss = { viewModel.hideDialog() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Цены топлива") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showDialog() }) {
                Icon(Icons.Default.Add, null)
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (uiState.prices.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LocalGasStation, null, Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Нет записей о ценах", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Добавляйте цены с АЗС для\nотслеживания стоимости топлива",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.prices, key = { it.id }) { price ->
                FuelPriceCard(price, dateFmt, onDelete = { viewModel.deletePrice(price) })
            }
        }
    }
}

@Composable
private fun FuelPriceCard(
    price: FuelPrice,
    dateFmt: SimpleDateFormat,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = price.stationName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(price.fuelType.name, fontSize = 11.sp) }
                    )
                    Text(
                        dateFmt.format(Date(price.recordedAt)),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "%.2f ₽".format(price.pricePerLiter),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("за литр", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null,
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun AddFuelPriceDialog(
    onConfirm: (String, FuelGradeType, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var stationName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(FuelGradeType.AI95) }
    var priceText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить цену") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = stationName,
                    onValueChange = { stationName = it },
                    label = { Text("Название АЗС") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Цена (₽/л)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Тип топлива", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FuelGradeType.values().take(4).forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.name, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val price = priceText.replace(',', '.').toDoubleOrNull()
                    if (stationName.isNotBlank() && price != null && price > 0) {
                        onConfirm(stationName, selectedType, price)
                    }
                }
            ) { Text("Добавить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
