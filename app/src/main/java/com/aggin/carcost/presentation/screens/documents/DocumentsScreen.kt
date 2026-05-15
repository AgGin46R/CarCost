package com.aggin.carcost.presentation.screens.documents

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aggin.carcost.data.local.database.entities.CarDocument
import com.aggin.carcost.data.local.database.entities.DocumentType
import com.aggin.carcost.presentation.screens.insurance.AddInsurancePolicyDialog
import com.aggin.carcost.presentation.screens.insurance.InsurancePoliciesViewModel
import com.aggin.carcost.presentation.screens.insurance.InsurancePoliciesViewModelFactory
import com.aggin.carcost.presentation.screens.insurance.InsurancePolicyCard
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.aggin.carcost.presentation.components.SkeletonCardList
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    carId: String,
    navController: NavController,
    viewModel: DocumentsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showAddDocDialog by remember { mutableStateOf(false) }
    var editingDocument by remember { mutableStateOf<CarDocument?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    // Insurance ViewModel
    val insuranceViewModel: InsurancePoliciesViewModel = viewModel(
        factory = InsurancePoliciesViewModelFactory(
            context.applicationContext as Application, carId
        )
    )
    val policies by insuranceViewModel.policies.collectAsState()
    var showAddPolicyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(carId) {
        viewModel.loadDocuments(carId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Документы") },
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
            FloatingActionButton(
                onClick = {
                    if (selectedTab == 0) showAddDocDialog = true
                    else showAddPolicyDialog = true
                }
            ) {
                Icon(Icons.Default.Add, "Добавить")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Документы") },
                    icon = { Icon(Icons.Default.Folder, null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Страховки") },
                    icon = { Icon(Icons.Default.Security, null, modifier = Modifier.size(18.dp)) }
                )
            }

            when (selectedTab) {
                0 -> {
                    // Documents tab
                    if (uiState.isLoading) {
                        SkeletonCardList(count = 4, cardHeight = 90.dp)
                    } else if (uiState.documents.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    null,
                                    modifier = Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text("Нет документов", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Добавьте ПТС, СТС и другие документы",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.documents, key = { it.id }) { doc ->
                                DocumentCard(
                                    document = doc,
                                    onEdit = { editingDocument = doc },
                                    onDelete = { viewModel.deleteDocument(doc.id) }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // Insurance tab
                    if (policies.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Security,
                                    null,
                                    modifier = Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text("Нет полисов", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Нажмите + чтобы добавить ОСАГО или КАСКО",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(policies, key = { it.id }) { policy ->
                                InsurancePolicyCard(
                                    policy = policy,
                                    onDelete = { insuranceViewModel.deletePolicy(policy) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Диалог добавления документа
    if (showAddDocDialog) {
        DocumentFormDialog(
            title = "Добавить документ",
            onDismiss = { showAddDocDialog = false },
            onSave = { type, title, fileUri, expiryDate, notes ->
                viewModel.addDocument(carId, type, title, fileUri, expiryDate, notes)
                showAddDocDialog = false
            }
        )
    }

    // Диалог редактирования документа
    editingDocument?.let { doc ->
        DocumentFormDialog(
            title = "Редактировать документ",
            initialDocument = doc,
            onDismiss = { editingDocument = null },
            onSave = { type, title, fileUri, expiryDate, notes ->
                viewModel.updateDocument(doc, type, title, fileUri, expiryDate, notes)
                editingDocument = null
            }
        )
    }

    // Диалог добавления страхового полиса
    if (showAddPolicyDialog) {
        AddInsurancePolicyDialog(
            onDismiss = { showAddPolicyDialog = false },
            onConfirm = { type, company, number, start, end, cost, notes ->
                insuranceViewModel.addPolicy(type, company, number, start, end, cost, notes)
                showAddPolicyDialog = false
            }
        )
    }
}

@Composable
fun DocumentCard(
    document: CarDocument,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val isExpired = document.expiryDate != null && document.expiryDate < System.currentTimeMillis()
    val expiresSoon = document.expiryDate != null &&
            document.expiryDate > System.currentTimeMillis() &&
            document.expiryDate < System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000

    fun openFile() {
        val uri = document.fileUri?.let { Uri.parse(it) } ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, context.contentResolver.getType(uri) ?: "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (document.fileUri != null) Modifier.clickable { openFile() } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isExpired -> MaterialTheme.colorScheme.errorContainer
                expiresSoon -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                getDocumentIcon(document.type),
                null,
                modifier = Modifier.size(40.dp),
                tint = if (isExpired) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(document.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    document.type.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                document.expiryDate?.let { expiry ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            isExpired -> "Истёк: ${dateFormat.format(Date(expiry))}"
                            expiresSoon -> "Истекает: ${dateFormat.format(Date(expiry))}"
                            else -> "До: ${dateFormat.format(Date(expiry))}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            isExpired -> MaterialTheme.colorScheme.error
                            expiresSoon -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        },
                        fontWeight = if (isExpired || expiresSoon) FontWeight.Bold else FontWeight.Normal
                    )
                }
                document.notes?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (document.fileUri != null) {
                IconButton(onClick = { openFile() }) {
                    Icon(Icons.Default.OpenInNew, "Открыть файл", tint = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "Редактировать", tint = MaterialTheme.colorScheme.secondary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun DocumentFormDialog(
    title: String,
    initialDocument: CarDocument? = null,
    onDismiss: () -> Unit,
    onSave: (DocumentType, String, String?, Long?, String?) -> Unit
) {
    var selectedType by remember { mutableStateOf(initialDocument?.type ?: DocumentType.INSURANCE) }
    var docTitle by remember { mutableStateOf(initialDocument?.title ?: "") }
    var notes by remember { mutableStateOf(initialDocument?.notes ?: "") }
    var fileUri by remember { mutableStateOf<String?>(initialDocument?.fileUri) }
    var showDatePicker by remember { mutableStateOf(false) }
    var expiryDate by remember { mutableStateOf<Long?>(initialDocument?.expiryDate) }
    var typeExpanded by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        rememberPermissionState(Manifest.permission.READ_MEDIA_IMAGES)
    else
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) fileUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Тип документа
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Тип документа") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        DocumentType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    selectedType = type
                                    if (docTitle.isEmpty()) docTitle = type.displayName
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = docTitle,
                    onValueChange = { docTitle = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Дата истечения
                OutlinedTextField(
                    value = expiryDate?.let { dateFormat.format(Date(it)) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Дата истечения (необязательно)") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Row {
                            if (expiryDate != null) {
                                IconButton(onClick = { expiryDate = null }) {
                                    Icon(Icons.Default.Clear, "Очистить")
                                }
                            }
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.CalendarMonth, "Выбрать дату")
                            }
                        }
                    }
                )

                // Файл
                OutlinedButton(
                    onClick = {
                        if (mediaPermission.status.isGranted) {
                            filePicker.launch("image/*")
                        } else {
                            mediaPermission.launchPermissionRequest()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            fileUri != null && initialDocument?.fileUri != fileUri -> "Новый файл выбран"
                            fileUri != null -> "Файл прикреплён · заменить"
                            else -> "Прикрепить фото/скан"
                        }
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Заметки (необязательно)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (docTitle.isNotBlank()) {
                        onSave(selectedType, docTitle, fileUri, expiryDate, notes.takeIf { it.isNotBlank() })
                    }
                },
                enabled = docTitle.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = expiryDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    expiryDate = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun getDocumentIcon(type: DocumentType) = when (type) {
    DocumentType.INSURANCE -> Icons.Default.Security
    DocumentType.REGISTRATION -> Icons.Default.Assignment
    DocumentType.TITLE -> Icons.Default.Description
    DocumentType.DIAGNOSTIC_CARD -> Icons.Default.Build
    DocumentType.WARRANTY -> Icons.Default.Verified
    DocumentType.PURCHASE_AGREEMENT -> Icons.Default.Handshake
    DocumentType.OTHER -> Icons.Default.Article
}
