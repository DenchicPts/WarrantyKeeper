package com.warrantykeeper.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warrantykeeper.data.local.prefs.PreferencesManager
import com.warrantykeeper.data.remote.GoogleDriveManager
import com.warrantykeeper.data.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val documentRepository: DocumentRepository,
    private val googleDriveManager: GoogleDriveManager,
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

    /** Вызывается из SettingsScreen после успешного получения Drive scope */
    fun onDrivePermissionGranted() {
        _isDriveConnected.value = googleDriveManager.isDrivePermissionGranted()
    }

    fun refreshDriveStatus() {
        _isDriveConnected.value = googleDriveManager.isDrivePermissionGranted()
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setNotificationsEnabled(enabled) }
    }

    fun setNotificationDaysBefore(days: Int) {
        viewModelScope.launch { preferencesManager.setNotificationDaysBefore(days) }
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
