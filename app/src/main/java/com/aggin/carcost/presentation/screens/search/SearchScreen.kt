package com.aggin.carcost.presentation.screens.search

import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.presentation.components.EmptyState
import com.aggin.carcost.presentation.navigation.Screen
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import com.aggin.carcost.presentation.common.emoji
import com.aggin.carcost.presentation.common.displayName
import androidx.compose.material.icons.filled.FilterList
import com.aggin.carcost.presentation.navigation.navigateOnce
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.onGloballyPositioned

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    viewModel: SearchViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }

    /**
     * Поле поиска уже размещено на экране.
     *
     * Раньше фокус запрашивался в LaunchedEffect(Unit) — то есть сразу при входе
     * в композицию. Но само поле живёт в шапке Scaffold, а она собирается позже,
     * и на медленном устройстве эффект успевал первым: модификатор ещё не
     * привязан, FocusRequester об этом сообщает исключением, и приложение падало.
     *
     * Ждём, пока поле действительно окажется на экране. Это единственный
     * надёжный признак: composition сам по себе ничего о размещении не говорит.
     */
    var searchFieldPlaced by remember { mutableStateOf(false) }

    LaunchedEffect(searchFieldPlaced) {
        if (!searchFieldPlaced) return@LaunchedEffect
        // runCatching на случай, когда экран закрывают в тот же миг: фокус
        // просить уже некому, и падать из-за этого приложение не должно
        runCatching { focusRequester.requestFocus() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text(stringResource(R.string.search_poisk_po_rashodam)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onGloballyPositioned { searchFieldPlaced = true },
                        trailingIcon = {
                            if (uiState.query.isNotEmpty()) {
                                IconButton(onClick = viewModel::clearQuery) {
                                    Icon(Icons.Default.Clear, stringResource(R.string.documents_ochistit))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        BadgedBox(badge = {
                            if (uiState.filter.isActive) {
                                Badge { Text(uiState.filter.activeCount.toString()) }
                            }
                        }) {
                            Icon(Icons.Default.FilterList, stringResource(R.string.search_filtry))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isSearching -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                uiState.query.length < 2 && !uiState.filter.isActive && !uiState.hasSearched -> {
                    EmptyState(
                        icon = Icons.Default.Search,
                        title = stringResource(R.string.search_poisk_po_rashodam_2),
                        subtitle = stringResource(R.string.search_vvedite_minimum_2_simvola_ili_zadayte),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.hasSearched && uiState.results.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.Search,
                        title = stringResource(R.string.search_nichego_ne_naydeno),
                        subtitle = stringResource(R.string.search_poprobuyte_izmenit_zapros_ili_proverte),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.results.isNotEmpty() -> {
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            // Отбор идёт в SQL с пределом, поэтому точного числа
                            // совпадений тут больше нет — запрос берёт на строку
                            // больше предела, и её наличие означает «есть ещё».
                            // Считать все совпадения ради подписи значило бы
                            // выполнять второй тяжёлый запрос на каждую букву.
                            val truncated = uiState.totalMatches > uiState.results.size
                            Text(
                                if (truncated)
                                    stringResource(R.string.search_pokazany_pervye_est_esche, uiState.results.size)
                                else
                                    stringResource(R.string.search_naydeno, uiState.results.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }
                        items(uiState.results, key = { it.expense.id }) { result ->
                            SearchResultCard(
                                result = result,
                                onClick = {
                                    navController.navigateOnce(
                                        Screen.EditExpense.createRoute(
                                            result.expense.carId,
                                            result.expense.id
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    result: ExpenseSearchResult,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        maximumFractionDigits = 0
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Иконка категории
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        categoryEmoji(result.expense.category),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    result.expense.title ?: categoryDisplayName(result.expense.category),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (result.car != null) {
                        Text(
                            result.car.brand + " " + result.car.model,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("·", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                    Text(
                        dateFormat.format(Date(result.expense.date)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                result.expense.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Text(
                "${numberFormat.format(result.expense.amount)} ₽",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun categoryEmoji(category: ExpenseCategory) = category.emoji()

@Composable
private fun categoryDisplayName(category: ExpenseCategory) = category.displayName()
