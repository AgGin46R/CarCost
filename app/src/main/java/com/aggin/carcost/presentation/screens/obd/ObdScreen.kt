package com.aggin.carcost.presentation.screens.obd

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.R

/**
 * Диагностика через адаптер OBD-II.
 *
 * Главное здесь — коды неисправностей с расшифровкой: лампочка на панели
 * перестаёт быть загадкой, и в сервис человек приезжает, зная, о чём речь.
 * Живые параметры — приятное дополнение, а не смысл экрана.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObdScreen(
    navController: NavController,
    carId: String
) {
    val context = LocalContext.current
    val viewModel: ObdViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ObdViewModel(context.applicationContext as android.app.Application, carId) as T
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }

    // С Android 12 для работы с сопряжёнными устройствами нужно отдельное
    // разрешение. На более старых версиях его не существует, и просить нечего
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.loadDevices() }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        } else {
            viewModel.loadDevices()
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.obd_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadDevices() }) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.obd_refresh_devices))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!uiState.connected) {
                Text(
                    text = stringResource(R.string.obd_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when {
                !uiState.bluetoothAvailable -> Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = stringResource(R.string.obd_bluetooth_off),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                uiState.connected -> ConnectedBlock(
                    uiState = uiState,
                    onDisconnect = { viewModel.disconnect() },
                    onReadCodes = { viewModel.readCodes() },
                    onClearCodes = { confirmClear = true },
                    onReadVin = { viewModel.readVin() }
                )

                else -> DeviceList(
                    uiState = uiState,
                    onConnect = { viewModel.connect(it) }
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.obd_clear_title)) },
            text = { Text(stringResource(R.string.obd_clear_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCodes()
                    confirmClear = false
                }) { Text(stringResource(R.string.obd_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun DeviceList(uiState: ObdUiState, onConnect: (String) -> Unit) {
    if (uiState.devices.isEmpty()) {
        Card {
            Text(
                text = stringResource(R.string.obd_no_devices),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    Text(
        text = stringResource(R.string.obd_paired_devices),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )

    uiState.devices.forEach { device ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !uiState.connecting) { onConnect(device.address) }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Bluetooth, null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(device.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        device.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (uiState.connecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectedBlock(
    uiState: ObdUiState,
    onDisconnect: () -> Unit,
    onReadCodes: () -> Unit,
    onClearCodes: () -> Unit,
    onReadVin: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Bluetooth, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.obd_connected_to, uiState.connectedTo.orEmpty()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onDisconnect) {
            Text(stringResource(R.string.obd_disconnect))
        }
    }

    // ── Живые параметры ─────────────────────────────────────────────────────
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.obd_live_data),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            val live = uiState.live
            ParamRow(
                stringResource(R.string.obd_param_rpm),
                live.rpm?.toString()
            )
            ParamRow(
                stringResource(R.string.obd_param_speed),
                live.speedKmh?.let { "$it ${stringResource(R.string.navigator_km_ch)}" }
            )
            ParamRow(
                stringResource(R.string.obd_param_coolant),
                live.coolantTempC?.let { "$it °C" }
            )
            ParamRow(
                stringResource(R.string.obd_param_intake),
                live.intakeTempC?.let { "$it °C" }
            )
            ParamRow(
                stringResource(R.string.obd_param_fuel_level),
                live.fuelLevel?.let { "${(it * 100).toInt()} %" }
            )
            ParamRow(
                stringResource(R.string.obd_param_load),
                live.engineLoad?.let { "${(it * 100).toInt()} %" }
            )
            ParamRow(
                stringResource(R.string.obd_param_voltage),
                live.voltage?.let { "%.1f В".format(it) }
            )
        }
    }

    // ── Коды ────────────────────────────────────────────────────────────────
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.obd_codes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            when {
                uiState.scanningCodes -> Box(
                    Modifier.fillMaxWidth().padding(12.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }

                // «Ошибок нет» пишем только после чтения. До него молчим:
                // иначе экран утверждал бы, что всё в порядке, ничего не спросив
                uiState.codesRead && uiState.codes.isEmpty() -> Text(
                    text = stringResource(R.string.obd_no_codes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                uiState.codes.isNotEmpty() -> uiState.codes.forEach { code ->
                    Row(Modifier.padding(vertical = 6.dp)) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = code.code,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = stringResource(code.descriptionRes),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            // Честно помечаем, что точной расшифровки нет и
                            // показано описание подсистемы
                            if (!code.isKnown) {
                                Text(
                                    text = stringResource(R.string.obd_code_approximate),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onReadCodes, enabled = !uiState.scanningCodes) {
                    Text(stringResource(R.string.obd_read_codes))
                }
                if (uiState.codes.isNotEmpty()) {
                    OutlinedButton(onClick = onClearCodes) {
                        Text(stringResource(R.string.obd_clear_codes))
                    }
                }
            }
        }
    }

    // ── VIN ─────────────────────────────────────────────────────────────────
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.passport_vin),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            uiState.vin?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onReadVin) {
                Text(stringResource(R.string.obd_read_vin))
            }
        }
    }

    HorizontalDivider()

    Text(
        text = stringResource(R.string.obd_no_odometer_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ParamRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            // Прочерк, а не ноль: «параметр не читается» и «значение равно
            // нулю» — разные вещи, и ноль оборотов у работающего двигателя
            // выглядел бы поломкой
            text = value ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
