package com.aggin.carcost.presentation.screens.insurance

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.database.entities.InsurancePolicy
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class InsurancePoliciesViewModel(
    application: Application,
    private val carId: String
) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).insurancePolicyDao()

    val policies = dao.getPoliciesForCar(carId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addPolicy(
        type: String, company: String, policyNumber: String,
        startDate: Long, endDate: Long, cost: Double, notes: String?
    ) {
        viewModelScope.launch {
            dao.insert(
                InsurancePolicy(
                    carId = carId, type = type, company = company,
                    policyNumber = policyNumber, startDate = startDate,
                    endDate = endDate, cost = cost,
                    notes = notes?.takeIf { it.isNotBlank() }
                )
            )
        }
    }

    fun deletePolicy(policy: InsurancePolicy) {
        viewModelScope.launch { dao.delete(policy) }
    }
}

class InsurancePoliciesViewModelFactory(
    private val app: Application,
    private val carId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return InsurancePoliciesViewModel(app, carId) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsurancePoliciesScreen(
    carId: String,
    navController: NavController
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: InsurancePoliciesViewModel = viewModel(
        factory = InsurancePoliciesViewModelFactory(
            context.applicationContext as Application, carId
        )
    )
    val policies by viewModel.policies.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Страховые полисы") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Добавить полис")
            }
        }
    ) { padding ->
        if (policies.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Security, null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Нет полисов", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text("Нажмите + чтобы добавить", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(policies, key = { it.id }) { policy ->
                    InsurancePolicyCard(
                        policy = policy,
                        onDelete = { viewModel.deletePolicy(policy) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddInsurancePolicyDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { type, company, number, start, end, cost, notes ->
                viewModel.addPolicy(type, company, number, start, end, cost, notes)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun InsurancePolicyCard(policy: InsurancePolicy, onDelete: () -> Unit) {
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    val now = System.currentTimeMillis()
    val daysLeft = ((policy.endDate - now) / (1000L * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    val isExpired = policy.endDate < now
    val progress = if (policy.endDate > policy.startDate) {
        ((now - policy.startDate).toFloat() / (policy.endDate - policy.startDate)).coerceIn(0f, 1f)
    } else 1f

    val typeLabel = when (policy.type) {
        "OSAGO" -> "ОСАГО"
        "KASKO" -> "КАСКО"
        else -> "Другое"
    }
    val statusColor = when {
        isExpired -> MaterialTheme.colorScheme.error
        daysLeft <= 14 -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        daysLeft <= 30 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "$typeLabel • ${policy.company.ifBlank { "—" }}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (policy.policyNumber.isNotBlank()) {
                        Text(
                            "Полис: ${policy.policyNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    fmt.format(Date(policy.startDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (isExpired) "Истёк" else "Ещё $daysLeft дн.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
                Text(
                    fmt.format(Date(policy.endDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (policy.cost > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Стоимость: %.2f ₽".format(policy.cost),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddInsurancePolicyDialog(
    onDismiss: () -> Unit,
    onConfirm: (type: String, company: String, number: String, start: Long, end: Long, cost: Double, notes: String?) -> Unit
) {
    val types = listOf("OSAGO" to "ОСАГО", "KASKO" to "КАСКО", "OTHER" to "Другое")
    var selectedType by remember { mutableStateOf("OSAGO") }
    var company by remember { mutableStateOf("") }
    var policyNumber by remember { mutableStateOf("") }
    var costStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val cal = Calendar.getInstance()
    var startDateStr by remember { mutableStateOf(fmt.format(cal.time)) }
    cal.add(Calendar.YEAR, 1)
    var endDateStr by remember { mutableStateOf(fmt.format(cal.time)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить полис") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Тип полиса
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { (value, label) ->
                        FilterChip(
                            selected = selectedType == value,
                            onClick = { selectedType = value },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = company, onValueChange = { company = it },
                    label = { Text("Страховая компания") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = policyNumber, onValueChange = { policyNumber = it },
                    label = { Text("Номер полиса") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startDateStr, onValueChange = { startDateStr = it },
                        label = { Text("Начало") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endDateStr, onValueChange = { endDateStr = it },
                        label = { Text("Конец") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = costStr, onValueChange = { costStr = it },
                    label = { Text("Стоимость (₽)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Заметки") }, singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val start = runCatching { fmt.parse(startDateStr)?.time }.getOrNull()
                    ?: System.currentTimeMillis()
                val end = runCatching { fmt.parse(endDateStr)?.time }.getOrNull()
                    ?: (System.currentTimeMillis() + 365L * 24 * 3600 * 1000)
                val cost = costStr.toDoubleOrNull() ?: 0.0
                onConfirm(selectedType, company, policyNumber, start, end, cost, notes)
            }) { Text("Добавить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
