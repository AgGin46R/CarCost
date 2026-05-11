package com.aggin.carcost.presentation.screens.fuel_calculator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelCalculatorScreen(
    navController: NavController,
    /** Pre-filled from navigator route estimate or car's avg consumption */
    initialDistanceKm: Double = 0.0,
    initialAvgL100: Double = 0.0,
    initialPricePerL: Double = 0.0
) {
    // --- Inputs ---
    var distanceKmStr by remember { mutableStateOf(if (initialDistanceKm > 0) "%.1f".format(initialDistanceKm) else "") }
    var avgL100Str    by remember { mutableStateOf(if (initialAvgL100 > 0) "%.1f".format(initialAvgL100) else "") }
    var pricePerLStr  by remember { mutableStateOf(if (initialPricePerL > 0) "%.2f".format(initialPricePerL) else "") }
    var tankLitresStr by remember { mutableStateOf("") }

    // --- Derived ---
    val distanceKm = distanceKmStr.toDoubleOrNull() ?: 0.0
    val avgL100    = avgL100Str.toDoubleOrNull() ?: 0.0
    val pricePerL  = pricePerLStr.toDoubleOrNull() ?: 0.0
    val tankLitres = tankLitresStr.toDoubleOrNull() ?: 0.0

    val litresNeeded  = if (avgL100 > 0) distanceKm * avgL100 / 100.0 else 0.0
    val totalCost     = litresNeeded * pricePerL
    val fullTankRange = if (avgL100 > 0 && tankLitres > 0) tankLitres / avgL100 * 100.0 else 0.0
    val refuelsNeeded = if (tankLitres > 0 && litresNeeded > 0) litresNeeded / tankLitres else 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Калькулятор топлива") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Inputs card ──────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Параметры поездки",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    FuelCalcField(
                        value = distanceKmStr,
                        onValueChange = { distanceKmStr = it },
                        label = "Расстояние, км",
                        icon = Icons.Default.Route
                    )
                    FuelCalcField(
                        value = avgL100Str,
                        onValueChange = { avgL100Str = it },
                        label = "Средний расход, л/100 км",
                        icon = Icons.Default.LocalGasStation
                    )
                    FuelCalcField(
                        value = pricePerLStr,
                        onValueChange = { pricePerLStr = it },
                        label = "Цена топлива, ₽/л",
                        icon = Icons.Default.AttachMoney
                    )
                    FuelCalcField(
                        value = tankLitresStr,
                        onValueChange = { tankLitresStr = it },
                        label = "Объём бака, л (необязательно)",
                        icon = Icons.Default.OilBarrel
                    )
                }
            }

            // ── Results card ─────────────────────────────────────────────
            if (litresNeeded > 0 || totalCost > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Результат",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        ResultRow(
                            icon = Icons.Default.LocalGasStation,
                            label = "Нужно топлива",
                            value = "%.2f л".format(litresNeeded)
                        )

                        if (totalCost > 0) {
                            ResultRow(
                                icon = Icons.Default.AttachMoney,
                                label = "Стоимость",
                                value = "%.0f ₽".format(totalCost),
                                highlight = true
                            )
                        }

                        if (fullTankRange > 0) {
                            ResultRow(
                                icon = Icons.Default.Speed,
                                label = "Запас хода на полном баке",
                                value = "${fullTankRange.roundToInt()} км"
                            )
                        }

                        if (refuelsNeeded >= 1) {
                            ResultRow(
                                icon = Icons.Default.Repeat,
                                label = "Заправок в пути",
                                value = "~${Math.ceil(refuelsNeeded).toInt()}"
                            )
                        }

                        // Cost per km
                        if (distanceKm > 0 && totalCost > 0) {
                            ResultRow(
                                icon = Icons.Default.Calculate,
                                label = "Цена за километр",
                                value = "%.2f ₽/км".format(totalCost / distanceKm)
                            )
                        }
                    }
                }
            }

            // ── Tip ──────────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Lightbulb, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Совет: средний расход вашего авто можно посмотреть в разделе Аналитика → Статистика топлива",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FuelCalcField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(20.dp)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ResultRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                icon, null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Text(
            value,
            style = if (highlight) MaterialTheme.typography.titleLarge
                    else MaterialTheme.typography.bodyLarge,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
