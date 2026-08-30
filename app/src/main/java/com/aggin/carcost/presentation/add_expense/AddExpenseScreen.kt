package com.aggin.carcost.presentation.screens.add_expense

import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState as rememberHScrollState
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.database.entities.FuelType
import com.aggin.carcost.data.local.database.entities.ServiceType
import com.aggin.carcost.data.local.database.entities.VehicleType
import com.aggin.carcost.data.local.database.entities.expenseCategoriesFor
import com.aggin.carcost.data.local.database.entities.serviceTypesFor
import java.text.SimpleDateFormat
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.lifecycle.compose.LifecycleEventEffect
import com.aggin.carcost.presentation.navigation.Screen
import com.aggin.carcost.presentation.components.TagSelector
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.*
import com.aggin.carcost.presentation.common.displayName
import com.aggin.carcost.presentation.common.formatDateLong
import com.aggin.carcost.presentation.navigation.navigateOnce
import androidx.compose.runtime.saveable.rememberSaveable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AddExpenseScreen(
    carId: String,
    plannedId: String? = null, // ✅ String UUID
    lockedCategory: Boolean = false,
    navController: NavController,
    viewModel: AddExpenseViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        rememberPermissionState(Manifest.permission.READ_MEDIA_IMAGES)
    else
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)

    val receiptLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.updateReceiptPhoto(it) }
    }

    // --- НОВЫЙ БЛОК: ЛОГИКА ПОЛУЧЕНИЯ ДАННЫХ СО СКАНЕРА ---
    // Получаем доступ к savedStateHandle, чтобы читать данные, переданные с другого экрана
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle

    // Этот эффект сработает каждый раз, когда экран становится активным (включая возврат со сканера)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // Пробуем получить значение "scanned_amount"
        val scannedAmount = savedStateHandle?.get<Double>("scanned_amount")
        if (scannedAmount != null) {
            // Если значение есть, обновляем ViewModel
            viewModel.updateAmount(scannedAmount.toString())
            // Очищаем значение, чтобы оно не применилось повторно
            savedStateHandle.remove<Double>("scanned_amount")
        }

        // То же самое для даты
        val scannedDate = savedStateHandle?.get<Long>("scanned_date")
        if (scannedDate != null) {
            viewModel.updateDate(scannedDate)
            savedStateHandle.remove<Long>("scanned_date")
        }

        // Данные заправки с чека
        val scannedLiters = savedStateHandle?.get<Double>("scanned_liters")
        if (scannedLiters != null) {
            viewModel.updateFuelLiters(scannedLiters.toString())
            viewModel.updateCategory(ExpenseCategory.FUEL)
            savedStateHandle.remove<Double>("scanned_liters")
        }

        val scannedOdometer = savedStateHandle?.get<Int>("scanned_odometer")
        if (scannedOdometer != null) {
            viewModel.updateOdometer(scannedOdometer.toString())
            savedStateHandle.remove<Int>("scanned_odometer")
        }

        val scannedStation = savedStateHandle?.get<String>("scanned_station")
        if (scannedStation != null) {
            viewModel.updateLocation(scannedStation)
            savedStateHandle.remove<String>("scanned_station")
        }

        // Марка топлива с чека. Раньше ключ просто удалялся, не читая —
        // распознавание работало, а до формы значение не доходило.
        val scannedFuelType = savedStateHandle?.get<String>("scanned_fuel_type")
        if (scannedFuelType != null) {
            viewModel.updateFuelGrade(scannedFuelType)
            savedStateHandle.remove<String>("scanned_fuel_type")
        }
    }
    // --- КОНЕЦ НОВОГО БЛОКА ---

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isFromPlannedExpense) stringResource(R.string.addexp_vypolnit_plan) else stringResource(R.string.cardetail_dobavit_rashod)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Индикатор если из плана
            if (uiState.isFromPlannedExpense) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            stringResource(R.string.addexp_dannye_iz_zaplanirovannoy_pokupki),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Ошибка
            if (uiState.showError) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = uiState.errorMessage,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Категория
            if (uiState.lockedCategory) {
                // Механик: только MAINTENANCE, категория зафиксирована
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.addexp_kategoriya),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            // Название категории должно совпадать с тем, что стоит
                            // в выборе категории ниже, иначе это выглядит как две разные
                            text = stringResource(R.string.cardetail_to),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.addexp_kategoriya_2),
                    style = MaterialTheme.typography.titleMedium
                )
                CategorySelector(
                    selectedCategory = uiState.category,
                    onCategorySelected = { viewModel.updateCategory(it) },
                    enabled = !uiState.isSaving,
                    fuelType = uiState.fuelType
                )
                // Подсказка автоопределения категории
                val detectedCat = uiState.autoDetectedCategory
                if (detectedCat != null) {
                    AutoCategoryHint(
                        category = detectedCat,
                        onDismiss = { viewModel.clearAutoDetectedCategory() }
                    )
                }
            }

            // --- НОВАЯ КНОПКА "СКАНИРОВАТЬ ЧЕК" ---
            OutlinedButton(
                onClick = { navController.navigateOnce(Screen.ReceiptScan.createRoute(carId)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ReceiptLong, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.addexp_skanirovat_chek))
            }
            // --- КОНЕЦ НОВОЙ КНОПКИ ---

            HorizontalDivider()

            // Повтор предыдущей записи этой категории: заправка почти всегда
            // повторяет прошлую по АЗС и описанию, и перепечатывать это руками
            // каждый раз — главная причина, по которой учёт забрасывают
            uiState.lastSimilar?.let { last ->
                OutlinedButton(
                    onClick = { viewModel.repeatLast() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSaving
                ) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = last.location?.takeIf { it.isNotBlank() }
                            ?.let { stringResource(R.string.addexp_kak_v_proshlyy_raz, it) }
                            ?: stringResource(R.string.addexp_kak_v_proshlyy_raz_2),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            // Основная информация
            Text(
                text = stringResource(R.string.addexp_osnovnaya_informatsiya),
                style = MaterialTheme.typography.titleMedium
            )

            // Сумма
            OutlinedTextField(
                value = uiState.amount,
                onValueChange = { viewModel.updateAmount(it) },
                label = { Text(stringResource(R.string.addexp_summa)) },
                placeholder = { Text("100.00") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = !uiState.isSaving,
                suffix = { Text("₽") }
            )

            // Быстрый выбор суммы
            QuickAmountRow(
                onAmountSelected = { viewModel.applyQuickAmount(it) },
                enabled = !uiState.isSaving
            )

            // Пробег
            OutlinedTextField(
                value = uiState.odometer,
                onValueChange = { viewModel.updateOdometer(it) },
                label = { Text(stringResource(R.string.addexp_probeg_km)) },
                placeholder = { Text("50000") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !uiState.isSaving
            )
            uiState.suggestedOdometer?.let { suggested ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.addexp_gps_podskazka_km, suggested),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { viewModel.applySuggestedOdometer() }) {
                        Text(stringResource(R.string.action_apply), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Дата
            var showDatePicker by rememberSaveable { mutableStateOf(false) }

            OutlinedTextField(
                value = formatDate(uiState.date),
                onValueChange = { },
                label = { Text(stringResource(R.string.cardetail_data)) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                enabled = !uiState.isSaving,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarToday, null)
                    }
                }
            )

            if (showDatePicker) {
                DatePickerDialog(
                    selectedDate = uiState.date,
                    onDateSelected = { viewModel.updateDate(it) },
                    onDismiss = { showDatePicker = false }
                )
            }

            // Описание
            OutlinedTextField(
                value = uiState.description,
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text(stringResource(R.string.cardetail_opisanie)) },
                placeholder = { Text(stringResource(R.string.addexp_zapravka_na_shell)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                enabled = !uiState.isSaving
            )

            // Место — с подсказками из истории этой машины
            com.aggin.carcost.presentation.components.SuggestField(
                value = uiState.location,
                onValueChange = { viewModel.updateLocation(it) },
                suggestions = uiState.recentLocations
                    .filter { it.contains(uiState.location.trim(), ignoreCase = true) }
                    .take(6),
                label = stringResource(R.string.cardetail_mesto),
                placeholder = stringResource(R.string.addexp_shell_ul_lenina),
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving
            )

            HorizontalDivider()

            // Теги
            TagSelector(
                availableTags = uiState.availableTags,
                selectedTags = uiState.selectedTags,
                onTagSelected = { viewModel.addTag(it) },
                onTagRemoved = { viewModel.removeTag(it) },
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth()
            )

            // Специфичные поля для категорий
            when (uiState.category) {
                ExpenseCategory.FUEL -> {
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.addexp_detali_zapravki),
                        style = MaterialTheme.typography.titleMedium
                    )

                    // Литры и цена за литр рядом: вместе с суммой это три
                    // числа, из которых независимы любые два — третье
                    // подставляется само, считать в уме на колонке не нужно
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.fuelLiters,
                            onValueChange = { viewModel.updateFuelLiters(it) },
                            label = { Text(stringResource(R.string.addexp_litrov)) },
                            placeholder = { Text("45.5") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            enabled = !uiState.isSaving,
                            suffix = { Text(stringResource(R.string.addexp_l)) }
                        )
                        OutlinedTextField(
                            value = uiState.pricePerUnit,
                            onValueChange = { viewModel.updatePricePerUnit(it) },
                            label = { Text(stringResource(R.string.addexp_tsena_za_litr)) },
                            placeholder = { Text("58.90") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            enabled = !uiState.isSaving
                        )
                    }

                    com.aggin.carcost.presentation.components.SuggestField(
                        value = uiState.fuelGrade,
                        onValueChange = { viewModel.updateFuelGrade(it) },
                        suggestions = fuelGradesFor(uiState.fuelType)
                            .filter { it.contains(uiState.fuelGrade.trim(), ignoreCase = true) },
                        label = stringResource(R.string.cardetail_marka_topliva),
                        placeholder = stringResource(R.string.addexp_ai_95),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isSaving
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.addexp_polnyy_bak))
                        Switch(
                            checked = uiState.isFullTank,
                            onCheckedChange = { viewModel.updateIsFullTank(it) },
                            enabled = !uiState.isSaving
                        )
                    }
                }

                ExpenseCategory.CHARGING -> {
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.addexp_detali_zaryadki),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.energyKwh,
                            onValueChange = { viewModel.updateEnergyKwh(it) },
                            label = { Text(stringResource(R.string.addexp_kilovatt_chasov)) },
                            placeholder = { Text("42.0") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            enabled = !uiState.isSaving,
                            suffix = { Text(stringResource(R.string.addexp_kvt_ch)) }
                        )
                        OutlinedTextField(
                            value = uiState.pricePerUnit,
                            onValueChange = { viewModel.updatePricePerUnit(it) },
                            label = { Text(stringResource(R.string.addexp_tsena_za_kvt_ch)) },
                            placeholder = { Text("12.50") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            enabled = !uiState.isSaving
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        // Тот же смысл, что «полный бак» у заправки: расход
                        // считается по отрезкам между полными зарядками
                        Text(stringResource(R.string.addexp_zaryad_do_100))
                        Switch(
                            checked = uiState.isFullTank,
                            onCheckedChange = { viewModel.updateIsFullTank(it) },
                            enabled = !uiState.isSaving
                        )
                    }
                }

                ExpenseCategory.MAINTENANCE -> {
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.addexp_detali_obsluzhivaniya),
                        style = MaterialTheme.typography.titleMedium
                    )

                    ServiceTypeDropdown(
                        fuelType = uiState.fuelType,
                        vehicleType = uiState.vehicleType,
                        selectedServiceType = uiState.serviceType,
                        onServiceTypeSelected = { viewModel.updateServiceType(it) },
                        enabled = !uiState.isSaving
                    )

                    OutlinedTextField(
                        value = uiState.workshopName,
                        onValueChange = { viewModel.updateWorkshopName(it) },
                        label = { Text(stringResource(R.string.addexp_nazvanie_sto)) },
                        placeholder = { Text(stringResource(R.string.addexp_avtoservis_1)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !uiState.isSaving
                    )

                    OutlinedTextField(
                        value = uiState.maintenanceParts,
                        onValueChange = { viewModel.updateMaintenanceParts(it) },
                        label = { Text(stringResource(R.string.addexp_zapchasti_i_raboty)) },
                        placeholder = { Text(stringResource(R.string.addexp_maslo_5w_40_filtr_maslyanyy_prokladka)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        enabled = !uiState.isSaving
                    )
                }

                ExpenseCategory.REPAIR -> {
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.addexp_detali_remonta),
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = uiState.workshopName,
                        onValueChange = { viewModel.updateWorkshopName(it) },
                        label = { Text(stringResource(R.string.addexp_nazvanie_sto)) },
                        placeholder = { Text(stringResource(R.string.addexp_avtoservis_1)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !uiState.isSaving
                    )
                }

                else -> { /* Нет специфичных полей */ }
            }

            HorizontalDivider()

            // Фото чека
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.addexp_foto_cheka), style = MaterialTheme.typography.titleSmall)
                OutlinedButton(
                    onClick = {
                        if (!uiState.isUploadingReceipt) {
                            if (mediaPermission.status.isGranted) {
                                receiptLauncher.launch("image/*")
                            } else {
                                mediaPermission.launchPermissionRequest()
                            }
                        }
                    },
                    enabled = !uiState.isSaving
                ) {
                    if (uiState.isUploadingReceipt) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.cardetail_zagruzka))
                    } else {
                        Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (uiState.receiptPhotoUri != null) stringResource(R.string.action_edit) else stringResource(R.string.addexp_prikrepit))
                    }
                }
            }
            if (uiState.receiptPhotoUri != null) {
                AsyncImage(
                    model = uiState.receiptPhotoUri,
                    contentDescription = stringResource(R.string.addexp_foto_cheka),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Кнопка сохранения
            Button(
                onClick = {
                    viewModel.saveExpense {
                        navController.popBackStack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !uiState.isSaving
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (uiState.isFromPlannedExpense) stringResource(R.string.addexp_vypolnit_i_sohranit) else stringResource(R.string.action_save), style = MaterialTheme.typography.titleMedium)
                }
            }

            Text(
                text = stringResource(R.string.addexp_obyazatelnye_polya),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CategorySelector(
    selectedCategory: ExpenseCategory,
    onCategorySelected: (ExpenseCategory) -> Unit,
    enabled: Boolean,
    /**
     * Чем машина движется. От этого зависит набор категорий: у электромобиля нет
     * заправок, у гибрида без розетки — зарядок.
     */
    fuelType: FuelType = FuelType.GASOLINE
) {
    // Раньше здесь лежали четыре прибитых гвоздями ряда по три фишки. Добавить
    // категорию по условию было некуда, а любое изменение состава ломало
    // разметку. Теперь список считается, а раскладка расставляет сама.
    val categories = remember(fuelType) { expenseCategoriesFor(fuelType) }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3
    ) {
        categories.forEach { category ->
            CategoryChip(
                label = category.chipLabel(),
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
        }
        // Добивка до кратности трёх, чтобы последний ряд не растягивался на всю
        // ширину: без неё одинокая фишка «Другое» выглядит кнопкой во весь экран
        repeat((3 - categories.size % 3) % 3) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Короткая подпись для фишки.
 *
 * В [com.aggin.carcost.presentation.common.Labels] лежат полные названия — они
 * нужны в отчётах и списках, но в фишку шириной в треть экрана не помещаются.
 */
@Composable
private fun ExpenseCategory.chipLabel(): String = when (this) {
    ExpenseCategory.FUEL -> stringResource(R.string.cardetail_toplivo)
    ExpenseCategory.CHARGING -> stringResource(R.string.addexp_zaryadka)
    ExpenseCategory.MAINTENANCE -> stringResource(R.string.cardetail_to)
    ExpenseCategory.REPAIR -> stringResource(R.string.addexp_remont)
    ExpenseCategory.INSURANCE -> stringResource(R.string.addexp_strahovka)
    ExpenseCategory.TAX -> stringResource(R.string.addexp_nalog)
    ExpenseCategory.PARKING -> stringResource(R.string.cardetail_parkovka)
    ExpenseCategory.TOLL -> stringResource(R.string.addexp_doroga)
    ExpenseCategory.WASH -> stringResource(R.string.addexp_moyka)
    ExpenseCategory.FINE -> stringResource(R.string.addexp_shtraf)
    ExpenseCategory.ACCESSORIES -> stringResource(R.string.addexp_aksessuary)
    ExpenseCategory.OTHER -> stringResource(R.string.addexp_drugoe)
}

@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        modifier = modifier,
        enabled = enabled
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceTypeDropdown(
    selectedServiceType: ServiceType?,
    onServiceTypeSelected: (ServiceType?) -> Unit,
    enabled: Boolean,
    /** Виды работ зависят от машины: у электромобиля нет ни масла, ни свечей */
    fuelType: FuelType = FuelType.GASOLINE,
    /** У мотоцикла нет салонного фильтра и развала, зато есть цепь и вилка */
    vehicleType: VehicleType = VehicleType.CAR
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedServiceType?.let { getServiceTypeName(it) } ?: stringResource(R.string.addexp_vyberite_tip),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.addexp_tip_obsluzhivaniya)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            enabled = enabled
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            serviceTypesFor(fuelType, vehicleType).forEach { serviceType ->
                DropdownMenuItem(
                    text = { Text(getServiceTypeName(serviceType)) },
                    onClick = {
                        onServiceTypeSelected(serviceType)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun getServiceTypeName(serviceType: ServiceType) = serviceType.displayName()

/** Row of quick-preset amount chips for fast input. */
@Composable
fun QuickAmountRow(
    onAmountSelected: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val presets = listOf("500", "1000", "1500", "2000", "3000", "5000")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberHScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { amount ->
            SuggestionChip(
                onClick = { onAmountSelected(amount) },
                label = { Text("$amount ₽", style = MaterialTheme.typography.labelMedium) },
                enabled = enabled
            )
        }
    }
}

/** Small hint card shown when the category was auto-detected from the description. */
@Composable
fun AutoCategoryHint(
    category: ExpenseCategory,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Намеренно НЕ из Labels.kt: в ряд чипов нужны укороченные подписи
    // («Дорога» вместо «Платная дорога»), иначе строка не помещается.
    val categoryLabel = when (category) {
        ExpenseCategory.FUEL -> stringResource(R.string.cardetail_toplivo)
        ExpenseCategory.MAINTENANCE -> stringResource(R.string.cardetail_to)
        ExpenseCategory.REPAIR -> stringResource(R.string.addexp_remont)
        ExpenseCategory.INSURANCE -> stringResource(R.string.addexp_strahovka)
        ExpenseCategory.TAX -> stringResource(R.string.addexp_nalog)
        ExpenseCategory.PARKING -> stringResource(R.string.cardetail_parkovka)
        ExpenseCategory.TOLL -> stringResource(R.string.addexp_doroga)
        ExpenseCategory.WASH -> stringResource(R.string.addexp_moyka)
        ExpenseCategory.FINE -> stringResource(R.string.addexp_shtraf)
        ExpenseCategory.ACCESSORIES -> stringResource(R.string.addexp_aksessuary)
        else -> stringResource(R.string.home_drugoe)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = stringResource(R.string.addexp_opredeleno_avtomaticheski, categoryLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(stringResource(R.string.action_edit), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

fun formatDate(timestamp: Long): String = formatDateLong(timestamp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(
    selectedDate: Long,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { onDateSelected(it) }
                    onDismiss()
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

/**
 * Марки топлива, которые имеет смысл предлагать этой машине.
 *
 * Дизелю не нужен АИ-95, бензину — ДТ. Список короткий и закрытый: марок
 * топлива на заправках в самом деле пять-шесть, и набирать их руками каждый
 * раз незачем.
 */
@Composable
private fun fuelGradesFor(
    fuelType: com.aggin.carcost.data.local.database.entities.FuelType
): List<String> = when (fuelType) {
    com.aggin.carcost.data.local.database.entities.FuelType.DIESEL ->
        listOf(stringResource(R.string.addexp_dt), stringResource(R.string.addexp_dt_zimnee), stringResource(R.string.addexp_dt_evro), stringResource(R.string.addexp_dt_premium))
    com.aggin.carcost.data.local.database.entities.FuelType.GAS ->
        listOf(stringResource(R.string.addexp_propan), stringResource(R.string.addexp_metan))
    else ->
        listOf(stringResource(R.string.addexp_ai_92), stringResource(R.string.addexp_ai_95), stringResource(R.string.addexp_ai_98), stringResource(R.string.addexp_ai_100), stringResource(R.string.addexp_ai_95_premium), stringResource(R.string.addexp_propan))
}
