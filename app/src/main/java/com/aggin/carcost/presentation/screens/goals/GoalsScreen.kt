package com.aggin.carcost.presentation.screens.goals

import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
import android.app.Application
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
import com.aggin.carcost.data.local.database.entities.SavingsGoal
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.domain.gamification.AchievementChecker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable

data class GoalsUiState(
    val goals: List<SavingsGoal> = emptyList(),
    val isLoading: Boolean = true,
    val showAddDialog: Boolean = false
)

class GoalsViewModel(
    application: Application,
    private val carId: String
) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.savingsGoalDao()

    val uiState: StateFlow<GoalsUiState> = dao.getGoalsByCarId(carId)
        .map { GoalsUiState(goals = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GoalsUiState())

    fun addGoal(title: String, target: Double) {
        viewModelScope.launch {
            dao.insert(SavingsGoal(carId = carId, title = title, targetAmount = target))
        }
    }

    fun addToGoal(goal: SavingsGoal, amount: Double) {
        viewModelScope.launch {
            val newAmount = (goal.currentAmount + amount).coerceAtMost(goal.targetAmount)
            val completed = newAmount >= goal.targetAmount
            dao.update(goal.copy(
                currentAmount = newAmount,
                isCompleted = completed,
                updatedAt = System.currentTimeMillis()
            ))
            // Check SAVINGS_GOAL_COMPLETE achievement when goal is first completed
            if (completed && !goal.isCompleted) {
                try {
                    val userId = SupabaseAuthRepository().getUserId()
                    if (userId != null) {
                        AchievementChecker(db.achievementDao(), db.expenseDao())
                            .checkAfterGoalCompleted(userId)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("GoalsViewModel", "Achievement check failed", e)
                }
            }
        }
    }

    fun deleteGoal(goal: SavingsGoal) {
        viewModelScope.launch { dao.delete(goal) }
    }
}

class GoalsViewModelFactory(
    private val app: Application,
    private val carId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return GoalsViewModel(app, carId) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    carId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val viewModel: GoalsViewModel = viewModel(
        factory = GoalsViewModelFactory(context.applicationContext as Application, carId)
    )
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    if (showAddDialog) {
        AddGoalDialog(
            onConfirm = { title, target ->
                viewModel.addGoal(title, target)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cardetail_tseli_nakopleniya)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, null)
            }
        }
    ) { padding ->
        if (uiState.goals.isEmpty() && !uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Savings, null, Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.goals_net_tseley_nakopleniya), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.goals_sozdayte_tsel_naprimer_n_novye_shiny_ili),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.goals, key = { it.id }) { goal ->
                GoalCard(
                    goal = goal,
                    onAdd = { amount -> viewModel.addToGoal(goal, amount) },
                    onDelete = { viewModel.deleteGoal(goal) }
                )
            }
        }
    }
}

@Composable
private fun GoalCard(
    goal: SavingsGoal,
    onAdd: (Double) -> Unit,
    onDelete: () -> Unit
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    if (showAddDialog) {
        var amountText by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.goals_dobavit_sredstva)) },
            text = {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.goals_summa)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    amountText.replace(',', '.').toDoubleOrNull()?.let { onAdd(it) }
                    showAddDialog = false
                }) { Text(stringResource(R.string.action_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    val progress = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).toFloat() else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (goal.isCompleted)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (goal.isCompleted) {
                        Icon(Icons.Default.CheckCircle, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Default.Savings, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    Text(goal.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null,
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("${goal.currentAmount.toInt()} ₽", fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.goals_iz, goal.targetAmount.toInt()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }

            Spacer(Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary
            )

            if (!goal.isCompleted) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.goals_popolnit))
                }
            }
        }
    }
}

@Composable
private fun AddGoalDialog(
    onConfirm: (String, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var targetText by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goals_novaya_tsel)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.goals_nazvanie_tseli)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = { Text(stringResource(R.string.goals_tselevaya_summa)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val target = targetText.replace(',', '.').toDoubleOrNull()
                if (title.isNotBlank() && target != null && target > 0) {
                    onConfirm(title, target)
                }
            }) { Text(stringResource(R.string.goals_sozdat)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
