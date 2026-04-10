package com.aggin.carcost.presentation.screens.parking

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.aggin.carcost.data.parking.ParkingTimerManager
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkingTimerScreen(navController: NavController) {
    val context = LocalContext.current

    var durationMinutes by remember { mutableIntStateOf(60) }
    var endTime by remember { mutableLongStateOf(ParkingTimerManager.getEndTime(context) ?: 0L) }
    val isRunning = endTime > System.currentTimeMillis()

    // Countdown ticker
    var remainingMs by remember { mutableLongStateOf(if (isRunning) endTime - System.currentTimeMillis() else 0L) }
    LaunchedEffect(endTime) {
        while (endTime > 0L && System.currentTimeMillis() < endTime) {
            remainingMs = endTime - System.currentTimeMillis()
            delay(1000L)
        }
        remainingMs = 0L
    }

    val minutes = (remainingMs / 60000).toInt()
    val seconds = ((remainingMs % 60000) / 1000).toInt()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Таймер парковки") },
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Icon
            Icon(
                Icons.Default.LocalParking,
                null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            // Countdown or setup
            if (isRunning || remainingMs > 0) {
                Text(
                    text = "%02d:%02d".format(minutes, seconds),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (minutes < 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "до истечения парковки",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                // +15 / -15 adjustments
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = {
                        val newEnd = endTime - 15 * 60 * 1000L
                        if (newEnd > System.currentTimeMillis()) {
                            endTime = newEnd
                            ParkingTimerManager.startTimer(context, ((newEnd - System.currentTimeMillis()) / 60000).toInt())
                        }
                    }) { Text("-15 мин") }
                    OutlinedButton(onClick = {
                        val newEnd = endTime + 15 * 60 * 1000L
                        endTime = newEnd
                        ParkingTimerManager.startTimer(context, ((newEnd - System.currentTimeMillis()) / 60000).toInt())
                    }) { Text("+15 мин") }
                }
                Button(
                    onClick = {
                        ParkingTimerManager.cancelTimer(context)
                        endTime = 0L
                        remainingMs = 0L
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Stop, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Остановить")
                }
            } else {
                // Duration picker
                Text(
                    "Выберите время парковки",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "$durationMinutes мин",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60, 90, 120).forEach { mins ->
                        FilterChip(
                            selected = durationMinutes == mins,
                            onClick = { durationMinutes = mins },
                            label = { Text("${mins}м") }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = { if (durationMinutes > 15) durationMinutes -= 15 }) { Text("-15") }
                    OutlinedButton(onClick = { durationMinutes += 15 }) { Text("+15") }
                }
                Button(
                    onClick = {
                        ParkingTimerManager.startTimer(context, durationMinutes)
                        endTime = System.currentTimeMillis() + durationMinutes * 60 * 1000L
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Timer, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Запустить таймер")
                }
            }
        }
    }
}
