package com.warrantykeeper.presentation.details

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.warrantykeeper.domain.model.Document
import com.warrantykeeper.domain.model.DocumentType
import com.warrantykeeper.domain.model.WarrantyStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: DocumentDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var showFullscreenPhoto by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (val s = uiState) {
                            is DetailUiState.Success -> if (s.document.type == DocumentType.WARRANTY) "Гарантия" else "Чек"
                            else -> "Документ"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                actions = {
                    if (uiState is DetailUiState.Success) {
                        // Sync status icon
                        val doc = (uiState as DetailUiState.Success).document
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(end = 4.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = { viewModel.syncToDrive() }) {
                                Icon(
                                    imageVector = if (doc.isSynced) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                                    contentDescription = if (doc.isSynced) "Синхронизировано" else "Загрузить в облако",
                                    tint = if (doc.isSynced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(onClick = { isEditing = !isEditing }) {
                            Icon(
                                imageVector = if (isEditing) Icons.Default.Close else Icons.Default.Edit,
                                contentDescription = if (isEditing) "Отмена" else "Редактировать"
                            )
                        }

                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is DetailUiState.Loading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is DetailUiState.Error -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }

            is DetailUiState.Success -> {
                DocumentContent(
                    document = state.document,
                    isEditing = isEditing,
                    isSyncing = isSyncing,
                    paddingValues = paddingValues,
                    onPhotoClick = { showFullscreenPhoto = true },
                    onSave = { title, store, purchaseDate, warrantyDate, notes ->
                        viewModel.updateDocument(title, store, purchaseDate, warrantyDate, notes)
                        isEditing = false
                    }
                )

                if (showFullscreenPhoto) {
                    FullscreenPhotoViewer(
                        imagePath = state.document.photoLocalPath,
                        onDismiss = { showFullscreenPhoto = false }
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить документ?") },
            text = { Text("Документ будет удалён с устройства и из облака. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteDocument(onDeleted = onNavigateBack)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun DocumentContent(
    document: Document,
    isEditing: Boolean,
    isSyncing: Boolean,
    paddingValues: PaddingValues,
    onPhotoClick: () -> Unit,
    onSave: (String, String?, Date?, Date?, String?) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    // Edit state
    var editTitle by remember(document) { mutableStateOf(document.title) }
    var editStore by remember(document) { mutableStateOf(document.storeName ?: "") }
    var editNotes by remember(document) { mutableStateOf(document.notes ?: "") }
    var editPurchaseDateStr by remember(document) {
        mutableStateOf(document.purchaseDate?.let { dateFormat.format(it) } ?: "")
    }
    var editWarrantyDateStr by remember(document) {
        mutableStateOf(document.warrantyEndDate?.let { dateFormat.format(it) } ?: "")
    }
    var dateError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
    ) {
        // Photo section — clickable only on the photo itself
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(Color.Black)
                .clickable(onClick = onPhotoClick)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(document.photoLocalPath)
                    .crossfade(true)
                    .build(),
                contentDescription = "Фото документа",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            // Cloud sync badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (document.isSynced)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                        } else {
                            Icon(
                                imageVector = if (document.isSynced) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (document.isSynced)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = when {
                                isSyncing -> "Синхронизация..."
                                document.isSynced -> "В облаке"
                                else -> "Не синхронизировано"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (document.isSynced)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Tap hint overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "Нажмите для просмотра",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Warranty status banner (only for warranties)
        if (document.type == DocumentType.WARRANTY) {
            val status = document.getWarrantyStatus()
            val daysLeft = document.getDaysUntilExpiry()

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = when (status) {
                    WarrantyStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
                    WarrantyStatus.EXPIRING_SOON -> MaterialTheme.colorScheme.errorContainer
                    WarrantyStatus.EXPIRED -> MaterialTheme.colorScheme.surfaceVariant
                    null -> MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = when (status) {
                            WarrantyStatus.ACTIVE -> Icons.Default.CheckCircle
                            WarrantyStatus.EXPIRING_SOON -> Icons.Default.Warning
                            WarrantyStatus.EXPIRED -> Icons.Default.Cancel
                            null -> Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = when (status) {
                            WarrantyStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                            WarrantyStatus.EXPIRING_SOON -> MaterialTheme.colorScheme.error
                            WarrantyStatus.EXPIRED -> MaterialTheme.colorScheme.outline
                            null -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Column {
                        Text(
                            text = when (status) {
                                WarrantyStatus.ACTIVE -> "Гарантия активна"
                                WarrantyStatus.EXPIRING_SOON -> "Гарантия скоро истечёт!"
                                WarrantyStatus.EXPIRED -> "Гарантия истекла"
                                null -> "Дата окончания не указана"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        daysLeft?.let {
                            Text(
                                text = when {
                                    it < 0 -> "Истекла ${Math.abs(it)} дней назад"
                                    it == 0 -> "Истекает сегодня"
                                    else -> "Осталось $it дней"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // Fields
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            if (isEditing) {
                // ======= EDIT MODE =======
                Text("Редактирование", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    label = { Text("Название товара *") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Label, null) },
                    singleLine = true
                )

                OutlinedTextField(
                    value = editStore,
                    onValueChange = { editStore = it },
                    label = { Text("Магазин / Производитель") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Store, null) },
                    singleLine = true
                )

                OutlinedTextField(
                    value = editPurchaseDateStr,
                    onValueChange = { editPurchaseDateStr = it },
                    label = { Text("Дата покупки (дд.мм.гггг)") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.CalendarToday, null) },
                    placeholder = { Text("01.01.2024") },
                    singleLine = true
                )

                if (document.type == DocumentType.WARRANTY) {
                    OutlinedTextField(
                        value = editWarrantyDateStr,
                        onValueChange = { editWarrantyDateStr = it },
                        label = { Text("Дата окончания гарантии (дд.мм.гггг)") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.EventBusy, null) },
                        placeholder = { Text("01.01.2025") },
                        singleLine = true,
                        isError = dateError != null
                    )
                    dateError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }

                OutlinedTextField(
                    value = editNotes,
                    onValueChange = { editNotes = it },
                    label = { Text("Заметки") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp),
                    leadingIcon = { Icon(Icons.Default.Notes, null) },
                    maxLines = 4
                )

                Button(
                    onClick = {
                        dateError = null
                        val purchaseDate = editPurchaseDateStr.takeIf { it.isNotBlank() }?.let {
                            runCatching { dateFormat.parse(it) }.getOrNull()
                        }
                        val warrantyDate = editWarrantyDateStr.takeIf { it.isNotBlank() }?.let {
                            runCatching { dateFormat.parse(it) }.getOrNull()
                        }
                        if (editWarrantyDateStr.isNotBlank() && warrantyDate == null) {
                            dateError = "Неверный формат даты. Используйте дд.мм.гггг"
                            return@Button
                        }
                        onSave(
                            editTitle,
                            editStore.ifBlank { null },
                            purchaseDate,
                            warrantyDate,
                            editNotes.ifBlank { null }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Сохранить изменения")
                }

            } else {
                // ======= VIEW MODE =======
                InfoSection(title = "Информация о товаре") {
                    InfoRow(Icons.Default.Label, "Название", document.title)
                    document.storeName?.let {
                        InfoRow(Icons.Default.Store, "Магазин", it)
                    }
                    document.purchaseDate?.let {
                        InfoRow(Icons.Default.CalendarToday, "Дата покупки", dateFormat.format(it))
                    }
                    if (document.type == DocumentType.WARRANTY) {
                        document.warrantyEndDate?.let {
                            InfoRow(Icons.Default.EventBusy, "Конец гарантии", dateFormat.format(it))
                        }
                    }
                }

                document.notes?.let {
                    InfoSection(title = "Заметки") {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                InfoSection(title = "Метаданные") {
                    InfoRow(
                        Icons.Default.FolderOpen,
                        "Тип",
                        if (document.type == DocumentType.WARRANTY) "Гарантийный документ" else "Чек"
                    )
                    InfoRow(
                        Icons.Default.Schedule,
                        "Добавлен",
                        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(document.createdAt)
                    )
                    InfoRow(
                        imageVector = if (document.isSynced) Icons.Default.CloudDone else Icons.Default.CloudOff,
                        label = "Облако",
                        value = if (document.isSynced) "Синхронизировано" else "Не загружено"
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun InfoRow(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun FullscreenPhotoViewer(
    imagePath: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        val newOffset = offset + pan
                        offset = newOffset
                    }
                }
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imagePath)
                    .crossfade(true)
                    .build(),
                contentDescription = "Фото документа",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                contentScale = ContentScale.Fit
            )

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, "Закрыть", tint = Color.White)
            }

            // Reset zoom button
            if (scale != 1f || offset != Offset.Zero) {
                IconButton(
                    onClick = { scale = 1f; offset = Offset.Zero },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.FitScreen, "Сбросить зум", tint = Color.White)
                }
            }
        }
    }
}
