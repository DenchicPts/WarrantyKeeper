package com.warrantykeeper.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warrantykeeper.data.local.prefs.PreferencesManager
import com.warrantykeeper.data.remote.GoogleDriveManager
import com.warrantykeeper.data.repository.DocumentRepository
import com.warrantykeeper.utils.NotificationHelper
import com.warrantykeeper.workers.DriveSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val documentRepository: DocumentRepository,
    private val googleDriveManager: GoogleDriveManager,
    private val notificationHelper: NotificationHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val userEmail = preferencesManager.userEmail
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val userName = preferencesManager.userName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val notificationsEnabled = preferencesManager.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val notificationDaysBefore = preferencesManager.notificationDaysBefore
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    private val _isDriveConnected = MutableStateFlow(googleDriveManager.isDrivePermissionGranted())
    val isDriveConnected: StateFlow<Boolean> = _isDriveConnected.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    /** Вызывается из SettingsScreen после успешного получения Drive scope */
    fun onDrivePermissionGranted() {
        _isDriveConnected.value = googleDriveManager.isDrivePermissionGranted()
    }

    fun refreshDriveStatus() {
        _isDriveConnected.value = googleDriveManager.isDrivePermissionGranted()
    }

    /** Ручная синхронизация — запускает one-shot DriveSyncWorker */
    fun syncNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = null
            try {
                DriveSyncWorker.runOnce(context)
                _syncMessage.value = "✓ Синхронизация запущена"
            } catch (e: Exception) {
                _syncMessage.value = "Ошибка: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun clearSyncMessage() { _syncMessage.value = null }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setNotificationsEnabled(enabled) }
    }

    fun setNotificationDaysBefore(days: Int) {
        viewModelScope.launch { preferencesManager.setNotificationDaysBefore(days) }
    }

    /** Отправляет тестовое уведомление чтобы убедиться что всё работает */
    fun sendTestNotification() {
        val days = notificationDaysBefore.value
        val testDate = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, days)
        }.time
        val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(testDate)
        notificationHelper.showWarrantyExpiryNotification(
            documentId    = -1L,
            productName   = "Тест товар",
            daysUntilExpiry = days,
            expiryDate    = dateStr
        )
    }

    fun logout() {
        viewModelScope.launch {
            val email = preferencesManager.userEmail.first() ?: ""
            if (email.isNotEmpty()) documentRepository.clearUserData(email)
            preferencesManager.clearUserData()
            _isDriveConnected.value = false
        }
    }
}
