package com.aggin.carcost.presentation.screens.car_members

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarMembersRepository
import com.aggin.carcost.data.remote.repository.SupabaseCarRepository
import com.aggin.carcost.data.remote.repository.SupabaseExpenseRepository
import com.aggin.carcost.data.remote.repository.SupabaseMaintenanceReminderRepository
import com.aggin.carcost.presentation.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AcceptInviteState {
    object Loading : AcceptInviteState()
    data class Success(val carId: String) : AcceptInviteState()
    data class Error(val message: String) : AcceptInviteState()
}

class AcceptInviteViewModel(
    application: Application,
    private val token: String
) : AndroidViewModel(application) {
    private val auth = SupabaseAuthRepository()
    private val supabaseMembers = SupabaseCarMembersRepository(auth)
    private val supabaseCars = SupabaseCarRepository(auth)
    private val supabaseExpenses = SupabaseExpenseRepository(auth)
    private val supabaseReminders = SupabaseMaintenanceReminderRepository(auth)
    private val db = AppDatabase.getDatabase(application)

    private val _state = MutableStateFlow<AcceptInviteState>(AcceptInviteState.Loading)
    val state: StateFlow<AcceptInviteState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                // Step 1: Accept invitation in Supabase
                val inviteResult = supabaseMembers.acceptInvitation(token)
                if (inviteResult.isFailure) {
                    _state.value = AcceptInviteState.Error(
                        inviteResult.exceptionOrNull()?.message ?: "Не удалось принять приглашение"
                    )
                    return@launch
                }
                val member = inviteResult.getOrThrow()

                // Step 2: Save membership to local Room
                try {
                    db.carMemberDao().insert(member)
                } catch (e: Exception) {
                    android.util.Log.w("AcceptInvite", "carMemberDao insert failed (may already exist): ${e.message}")
                }

                // Step 3: Sync the shared car locally
                try {
                    supabaseCars.fetchSharedCar(member.carId)
                        .onSuccess { car -> db.carDao().insertCar(car) }
                } catch (e: Exception) {
                    android.util.Log.w("AcceptInvite", "fetchSharedCar failed: ${e.message}")
                }

                // Step 4: Sync existing expenses for that car
                try {
                    supabaseExpenses.getExpensesByCarId(member.carId)
                        .onSuccess { expenses ->
                            expenses.forEach {
                                try { db.expenseDao().insertExpense(it) } catch (_: Exception) {}
                            }
                        }
                } catch (e: Exception) {
                    android.util.Log.w("AcceptInvite", "expenses sync failed: ${e.message}")
                }

                // Step 5: Sync maintenance reminders for that car
                try {
                    supabaseReminders.getRemindersByCarId(member.carId)
                        .onSuccess { reminders ->
                            reminders.forEach {
                                try { db.maintenanceReminderDao().insertReminder(it) } catch (_: Exception) {}
                            }
                        }
                } catch (e: Exception) {
                    android.util.Log.w("AcceptInvite", "reminders sync failed: ${e.message}")
                }

                _state.value = AcceptInviteState.Success(member.carId)

            } catch (e: Exception) {
                android.util.Log.e("AcceptInvite", "Unexpected crash", e)
                _state.value = AcceptInviteState.Error(e.message ?: "Неизвестная ошибка")
            }
        }
    }
}

class AcceptInviteViewModelFactory(
    private val app: Application,
    private val token: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AcceptInviteViewModel(app, token) as T
    }
}

@Composable
fun AcceptInviteScreen(
    token: String,
    navController: NavController
) {
    val context = LocalContext.current
    val viewModel: AcceptInviteViewModel = viewModel(
        factory = AcceptInviteViewModelFactory(context.applicationContext as Application, token)
    )
    val state by viewModel.state.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        when (val s = state) {
            is AcceptInviteState.Loading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Принимаем приглашение...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            is AcceptInviteState.Success -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Вы успешно присоединились!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Теперь у вас есть доступ к этому автомобилю.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Перейти к автомобилям", fontSize = 16.sp)
                    }
                }
            }

            is AcceptInviteState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Не удалось принять приглашение",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        s.message,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Назад")
                    }
                }
            }
        }
    }
}
