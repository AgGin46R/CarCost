package com.aggin.carcost.presentation.screens.tyres

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.R
import com.aggin.carcost.data.local.database.entities.TyreSet
import com.aggin.carcost.presentation.common.currencyFormat
import com.aggin.carcost.presentation.components.DateField
import com.aggin.carcost.presentation.components.dateDigitsToMillis
import com.aggin.carcost.presentation.components.millisToDateDigits

/**
 * Комплекты шин.
 *
 * Главное, ради чего экран существует, — ответ на вопрос «сколько эта резина
 * уже отходила». Поэтому пробег стоит на видном месте у каждого комплекта, а
 * не прячется в карточке.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TyresScreen(
    navController: NavController,
    carId: String
) {
    val context = LocalContext.current
    val viewModel: TyresViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TyresViewModel(context.applicationContext as android.app.Application, carId) as T
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    var deleting by remember { mutableStateOf<TyreSet?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tyres_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.startNew() }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.tyres_add))
            }
        }
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            uiState.items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.tyres_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.items, key = { it.set.id }) { item ->
                    TyreSetCard(
                        item = item,
                        onInstall = { viewModel.install(item.set.id) },
                        onUninstall = { viewModel.uninstall(item.set.id) },
                        onEdit = { viewModel.startEdit(item.set) },
                        onDelete = { deleting = item.set }
                    )
                }
            }
        }
    }

    uiState.editing?.let { editing ->
        TyreSetForm(
            initial = editing,
            onDismiss = { viewModel.dismissForm() },
            onSave = { viewModel.save(it) }
        )
    }

    deleting?.let { set ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.tyres_delete_title)) },
            text = { Text(stringResource(R.string.tyres_delete_message, set.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(set)
                    deleting = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun TyreSetCard(
    item: TyreSetItem,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.set.isInstalled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.set.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = stringResource(item.set.season.labelRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        item.set.size?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.tyres_km_total, item.km),
                style = MaterialTheme.typography.titleLarge
            )
            if (item.set.isInstalled && item.currentPeriodKm > 0) {
                Text(
                    text = stringResource(R.string.tyres_km_this_season, item.currentPeriodKm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Полоса износа показывается только когда ресурс задан. Рисовать её
            // от выдуманного значения — значит показывать человеку цифру, за
            // которой ничего не стоит
            item.wear?.let { wear ->
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { wear },
                    modifier = Modifier.fillMaxWidth(),
                    color = when {
                        wear >= 0.9f -> MaterialTheme.colorScheme.error
                        wear >= 0.7f -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                Text(
                    text = stringResource(
                        R.string.tyres_wear_of_life,
                        (wear * 100).toInt(),
                        item.set.expectedLifeKm ?: 0
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item.set.purchasePrice?.let { price ->
                Spacer(Modifier.height(4.dp))
                val perKm = if (item.km > 0) price / item.km else null
                Text(
                    text = if (perKm != null) {
                        stringResource(
                            R.string.tyres_price_and_per_km,
                            currencyFormat(price),
                            currencyFormat(perKm, decimals = 2)
                        )
                    } else {
                        currencyFormat(price)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!item.set.isInstalled) {
                item.set.storageLocation?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = stringResource(R.string.tyres_stored_at, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (item.set.isInstalled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.tyres_installed_now)) }
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onUninstall) {
                        Text(stringResource(R.string.tyres_uninstall))
                    }
                }
            } else {
                Button(onClick = onInstall) {
                    Text(stringResource(R.string.tyres_install))
                }
            }
        }
    }
}

/**
 * Форма комплекта.
 *
 * Обязательное здесь только название. Остальное человек заполнит, когда
 * захочет: пустой размер или неизвестная цена не мешают считать пробег, ради
 * которого экран и сделан.
 */
@Composable
private fun TyreSetForm(
    initial: TyreSet,
    onDismiss: () -> Unit,
    onSave: (TyreSet) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var season by remember { mutableStateOf(initial.season) }
    var size by remember { mutableStateOf(initial.size.orEmpty()) }
    var price by remember { mutableStateOf(initial.purchasePrice?.let { "%.0f".format(it) } ?: "") }
    var dateDigits by remember {
        mutableStateOf(initial.purchaseDate?.let { millisToDateDigits(it) } ?: "")
    }
    var life by remember { mutableStateOf(initial.expectedLifeKm?.toString() ?: "") }
    var storage by remember { mutableStateOf(initial.storageLocation.orEmpty()) }
    var notes by remember { mutableStateOf(initial.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial.name.isBlank()) R.string.tyres_add else R.string.tyres_edit
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.tyres_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TyresViewModel.SEASONS.forEach { option ->
                        FilterChip(
                            selected = season == option,
                            onClick = { season = option },
                            label = { Text(stringResource(option.labelRes)) }
                        )
                    }
                }

                OutlinedTextField(
                    value = size,
                    onValueChange = { size = it },
                    label = { Text(stringResource(R.string.tyres_field_size)) },
                    placeholder = { Text(stringResource(R.string.tyres_size_example)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = price,
                    onValueChange = { input -> price = input.filter { it.isDigit() } },
                    label = { Text(stringResource(R.string.tyres_field_price)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                DateField(
                    digits = dateDigits,
                    onDigitsChange = { dateDigits = it },
                    label = stringResource(R.string.tyres_field_purchase_date),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = life,
                    onValueChange = { input -> life = input.filter { it.isDigit() }.take(6) },
                    label = { Text(stringResource(R.string.tyres_field_expected_life)) },
                    supportingText = { Text(stringResource(R.string.tyres_expected_life_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = storage,
                    onValueChange = { storage = it },
                    label = { Text(stringResource(R.string.tyres_field_storage)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.tyres_field_notes)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            season = season,
                            size = size.trim().ifBlank { null },
                            purchasePrice = price.toDoubleOrNull(),
                            // Незаконченную дату не сохраняем и не подменяем
                            // сегодняшней: пустая дата честнее выдуманной
                            purchaseDate = dateDigits.takeIf { it.length == 8 }
                                ?.let { dateDigitsToMillis(it) },
                            expectedLifeKm = life.toIntOrNull()?.takeIf { it > 0 },
                            storageLocation = storage.trim().ifBlank { null },
                            notes = notes.trim().ifBlank { null }
                        )
                    )
                }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
