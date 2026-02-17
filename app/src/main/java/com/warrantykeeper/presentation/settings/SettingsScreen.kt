package com.warrantykeeper.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val userEmail by viewModel.userEmail.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val notificationDaysBefore by viewModel.notificationDaysBefore.collectAsState()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDaysDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Account section
            SettingsSection(title = "Аккаунт") {
                SettingsItem(
                    title = userName ?: "Пользователь",
                    subtitle = userEmail,
                    icon = Icons.Default.Person
                )

                SettingsItem(
                    title = "Выйти из аккаунта",
                    icon = Icons.Default.Logout,
                    onClick = { showLogoutDialog = true }
                )
            }

            Divider()  // ✅ ИЗМЕНЕНО: HorizontalDivider → Divider

            // Notifications section
            SettingsSection(title = "Уведомления") {
                SettingsSwitchItem(
                    title = "Включить уведомления",
                    subtitle = "Напоминания об истечении гарантии",
                    icon = Icons.Default.Notifications,
                    checked = notificationsEnabled,
                    onCheckedChange = viewModel::toggleNotifications
                )

                if (notificationsEnabled) {
                    SettingsItem(
                        title = "Напоминать за",
                        subtitle = "$notificationDaysBefore дней до истечения",
                        icon = Icons.Default.Schedule,
                        onClick = { showDaysDialog = true }
                    )
                }
            }

            Divider()  // ✅ ИЗМЕНЕНО: HorizontalDivider → Divider

            // App info
            SettingsSection(title = "О приложении") {
                SettingsItem(
                    title = "Версия",
                    subtitle = "1.0.0",
                    icon = Icons.Default.Info
                )
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Выйти из аккаунта?") },
            text = { Text("Все данные останутся на устройстве") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.logout()
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text("Выйти")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showDaysDialog) {
        var selectedDays by remember { mutableStateOf(notificationDaysBefore) }

        AlertDialog(
            onDismissRequest = { showDaysDialog = false },
            title = { Text("Напоминать за...") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 7, 14, 30).forEach { days ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedDays == days,
                                onClick = { selectedDays = days }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("$days ${if (days == 1) "день" else if (days < 5) "дня" else "дней"}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setNotificationDaysBefore(selectedDays)
                    showDaysDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDaysDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (() -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = {
            Icon(icon, contentDescription = null)
        },
        trailingContent = onClick?.let {
            {
                Icon(Icons.Default.ChevronRight, null)
            }
        },
        modifier = if (onClick != null) {
            Modifier.clickableWithoutRipple(onClick = onClick)
        } else Modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = {
            Icon(icon, contentDescription = null)
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}

@Composable
fun Modifier.clickableWithoutRipple(onClick: () -> Unit): Modifier {
    return this.then(
        Modifier.clickable(
            onClick = onClick,
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        )
    )
}