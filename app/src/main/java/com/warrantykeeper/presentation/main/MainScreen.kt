package com.warrantykeeper.presentation.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.warrantykeeper.domain.model.Document
import com.warrantykeeper.domain.model.DocumentType
import com.warrantykeeper.domain.model.WarrantyStatus
import com.warrantykeeper.presentation.details.FullscreenPhotoViewer
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToCamera: () -> Unit,
    onNavigateToDocument: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val documents by viewModel.documents.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    var showFullscreenPhoto by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WarrantyKeeper") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Настройки")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCamera,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "Добавить документ")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Поиск документов...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, "Очистить")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )

            // Filter chips
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterType == FilterType.ALL,
                    onClick = { viewModel.onFilterTypeChange(FilterType.ALL) },
                    label = { Text("Все") }
                )
                FilterChip(
                    selected = filterType == FilterType.WARRANTIES,
                    onClick = { viewModel.onFilterTypeChange(FilterType.WARRANTIES) },
                    label = { Text("Гарантии") }
                )
                FilterChip(
                    selected = filterType == FilterType.RECEIPTS,
                    onClick = { viewModel.onFilterTypeChange(FilterType.RECEIPTS) },
                    label = { Text("Чеки") }
                )
                FilterChip(
                    selected = filterType == FilterType.ACTIVE_WARRANTIES,
                    onClick = { viewModel.onFilterTypeChange(FilterType.ACTIVE_WARRANTIES) },
                    label = { Text("Активные") }
                )
            }

            // Documents list
            if (documents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(120.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Нет документов",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Нажмите + чтобы добавить первый документ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(documents, key = { it.id }) { document ->
                        DocumentCard(
                            document = document,
                            onCardClick = { onNavigateToDocument(document.id) },
                            onPhotoClick = { showFullscreenPhoto = document.photoLocalPath }
                        )
                    }
                }
            }
        }
    }

    // Fullscreen photo from main list
    showFullscreenPhoto?.let { photoPath ->
        FullscreenPhotoViewer(
            imagePath = photoPath,
            onDismiss = { showFullscreenPhoto = null }
        )
    }
}

@Composable
fun DocumentCard(
    document: Document,
    onCardClick: () -> Unit,
    onPhotoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Clicking anywhere on the card (text/empty space) goes to details
                .clickable(onClick = onCardClick)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail — clicking only the photo opens fullscreen view
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    // Override the parent clickable: photo click opens fullscreen
                    .clickable(onClick = onPhotoClick)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(document.photoLocalPath)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Фото документа",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Small cloud sync icon over thumbnail
                if (document.isSynced) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Синхронизировано",
                            modifier = Modifier.size(14.dp),
                            tint = Color.White
                        )
                    }
                }
            }

            // Info area (text + status)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Title + sync indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!document.isSynced) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "Не синхронизировано",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }

                document.storeName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (document.type == DocumentType.WARRANTY && document.warrantyEndDate != null) {
                    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                    val status = document.getWarrantyStatus()
                    val daysLeft = document.getDaysUntilExpiry() ?: 0

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = when (status) {
                                WarrantyStatus.ACTIVE -> Icons.Default.CheckCircle
                                WarrantyStatus.EXPIRING_SOON -> Icons.Default.Warning
                                WarrantyStatus.EXPIRED -> Icons.Default.Error
                                null -> Icons.Default.Info
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = when (status) {
                                WarrantyStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                                WarrantyStatus.EXPIRING_SOON -> MaterialTheme.colorScheme.error
                                WarrantyStatus.EXPIRED -> MaterialTheme.colorScheme.outline
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = when {
                                daysLeft < 0 -> "Истекла ${dateFormat.format(document.warrantyEndDate)}"
                                daysLeft == 0 -> "Истекает сегодня"
                                daysLeft <= 30 -> "Осталось $daysLeft дн."
                                else -> "До ${dateFormat.format(document.warrantyEndDate)}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (status) {
                                WarrantyStatus.EXPIRING_SOON -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                } else if (document.type == DocumentType.RECEIPT) {
                    Text(
                        text = "Чек",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Arrow hint
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterVertically),
                tint = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}
