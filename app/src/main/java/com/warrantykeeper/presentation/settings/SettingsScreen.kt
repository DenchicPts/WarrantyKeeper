package com.warrantykeeper.presentation.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.warrantykeeper.data.remote.GoogleDriveManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val userEmail           by viewModel.userEmail.collectAsState()
    val userName            by viewModel.userName.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val notificationDaysBefore by viewModel.notificationDaysBefore.collectAsState()
    val isDriveConnected    by viewModel.isDriveConnected.collectAsState()
    val isSyncing           by viewModel.isSyncing.collectAsState()
    val syncMessage         by viewModel.syncMessage.collectAsState()

    var showLogoutDialog  by remember { mutableStateOf(false) }
    var showDaysDialog    by remember { mutableStateOf(false) }
    var driveMessage      by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    // Drive scope launcher
    val driveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            task.getResult(ApiException::class.java)
            viewModel.onDrivePermissionGranted()
            driveMessage = Pair(true, "✓ Google Drive подключён успешно!")
        } catch (e: ApiException) {
            driveMessage = Pair(false, "Ошибка ${e.statusCode}: не удалось подключить Drive")
        }
    }

    fun requestDrivePermission() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(GoogleDriveManager.DRIVE_SCOPE))
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        driveLauncher.launch(client.signInIntent)
    }

    // Launcher для запроса разрешения уведомлений (Android 13+)
    var showNotificationRationale by remember { mutableStateOf(false) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.sendTestNotification()
        } else {
            showNotificationRationale = true
        }
    }

    fun sendTestOrRequestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED -> viewModel.sendTestNotification()
                else -> notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            viewModel.sendTestNotification()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
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
        ) {
            // ── Аккаунт ────────────────────────────────────────────────
            SectionHeader("Аккаунт")

            ListItem(
                headlineContent  = { Text(userName ?: "Пользователь") },
                supportingContent = { Text(userEmail ?: "") },
                leadingContent   = {
                    Icon(Icons.Default.AccountCircle, null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ListItem(
                headlineContent  = { Text("Выйти из аккаунта") },
                supportingContent = { Text("Войти с другим аккаунтом") },
                leadingContent   = { Icon(Icons.Default.Logout, null, tint = MaterialTheme.colorScheme.error) },
                modifier         = Modifier.clickable { showLogoutDialog = true }
            )

            // ── Облако ─────────────────────────────────────────────────
            SectionHeader("Облако")

            ListItem(
                headlineContent  = {
                    Text(if (isDriveConnected) "Google Drive подключён" else "Подключить Google Drive")
                },
                supportingContent = {
                    Text(
                        if (isDriveConnected)
                            "Фото синхронизируются в WarrantyKeeper/. Нажмите для переподключения."
                        else
                            "Нажмите чтобы разрешить сохранение в Google Drive"
                    )
                },
                leadingContent   = {
                    Icon(
                        if (isDriveConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                        null,
                        tint = if (isDriveConnected) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.error
                    )
                },
                trailingContent  = { Icon(Icons.Default.ChevronRight, null) },
                modifier         = Modifier.clickable { requestDrivePermission() }
            )

            driveMessage?.let { (isSuccess, msg) ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSuccess) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 72.dp, end = 16.dp, bottom = 4.dp)
                )
            }

            // Кнопка "Синхронизировать сейчас"
            if (isDriveConnected) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent  = { Text("Синхронизировать сейчас") },
                    supportingContent = {
                        if (syncMessage != null) {
                            Text(syncMessage!!, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text("Загрузить все несинхронизированные документы")
                        }
                    },
                    leadingContent   = {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Sync, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    trailingContent  = { Icon(Icons.Default.ChevronRight, null) },
                    modifier         = Modifier.clickable(enabled = !isSyncing) { viewModel.syncNow() }
                )
            }

            // ── Уведомления ────────────────────────────────────────────
            SectionHeader("Уведомления")

            ListItem(
                headlineContent  = { Text("Уведомления о гарантии") },
                supportingContent = { Text("Напоминания об истечении срока") },
                leadingContent   = { Icon(Icons.Default.Notifications, null) },
                trailingContent  = {
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = viewModel::toggleNotifications
                    )
                }
            )

            if (notificationsEnabled) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent  = { Text("Напоминать за") },
                    supportingContent = { Text("$notificationDaysBefore дней до истечения гарантии") },
                    leadingContent   = { Icon(Icons.Default.Schedule, null) },
                    trailingContent  = { Icon(Icons.Default.ChevronRight, null) },
                    modifier         = Modifier.clickable { showDaysDialog = true }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent  = { Text("Тест уведомления") },
                    supportingContent = { Text("Отправить тестовое оповещение прямо сейчас") },
                    leadingContent   = { Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent  = { Icon(Icons.Default.ChevronRight, null) },
                    modifier         = Modifier.clickable { sendTestOrRequestPermission() }
                )
            }

            // ── О приложении ───────────────────────────────────────────
            SectionHeader("О приложении")

            ListItem(
                headlineContent  = { Text("Версия") },
                supportingContent = { Text("1.0.0") },
                leadingContent   = { Icon(Icons.Default.Info, null) }
            )
        }
    }

    // ── Диалог разрешения уведомлений ─────────────────────────────────
    if (showNotificationRationale) {
        AlertDialog(
            onDismissRequest = { showNotificationRationale = false },
            title = { Text("Уведомления отключены") },
            text  = { Text("Чтобы получать напоминания о гарантиях, разрешите уведомления в настройках телефона.") },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationRationale = false
                    // Открываем настройки приложения
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) { Text("Открыть настройки") }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationRationale = false }) { Text("Отмена") }
            }
        )
    }

    // ── Диалог выхода ──────────────────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Выйти из аккаунта?") },
            text  = { Text("Локальные данные будут удалены. Документы в Google Drive останутся.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail().build()
                        val client = GoogleSignIn.getClient(context, gso)
                        client.signOut().addOnCompleteListener {
                            client.revokeAccess().addOnCompleteListener {
                                viewModel.logout()
                                onLogout()
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Выйти") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Отмена") }
            }
        )
    }

    // ── Диалог выбора дней ─────────────────────────────────────────────
    if (showDaysDialog) {
        var selected   by remember { mutableStateOf(notificationDaysBefore) }
        var customText by remember { mutableStateOf("") }
        var customError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showDaysDialog = false },
            title = { Text("Напоминать за...") },
            text  = {
                Column {
                    // Быстрые варианты
                    listOf(1, 3, 7, 14, 30).forEach { days ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = days; customText = "" }
                        ) {
                            RadioButton(selected = selected == days && customText.isEmpty(), onClick = {
                                selected = days; customText = ""
                            })
                            Spacer(Modifier.width(8.dp))
                            val label = when {
                                days == 1 -> "1 день"
                                days < 5  -> "$days дня"
                                else      -> "$days дней"
                            }
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Произвольное число дней
                    Text("Своё значение:", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp))
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { v ->
                            customText = v.filter { it.isDigit() }.take(3)
                            customError = false
                            val n = customText.toIntOrNull()
                            if (n != null && n in 1..365) selected = n
                        },
                        label = { Text("Дней (1–365)") },
                        isError = customError,
                        supportingText = if (customError) {{ Text("Введите число от 1 до 365") }} else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // Проверяем кастомный ввод если он непустой
                    if (customText.isNotEmpty()) {
                        val n = customText.toIntOrNull()
                        if (n == null || n !in 1..365) {
                            customError = true
                            return@TextButton
                        }
                        selected = n
                    }
                    viewModel.setNotificationDaysBefore(selected)
                    showDaysDialog = false
                }) { Text("ОК") }
            },
            dismissButton = {
                TextButton(onClick = { showDaysDialog = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}
