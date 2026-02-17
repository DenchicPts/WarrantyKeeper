package com.warrantykeeper.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warrantykeeper.data.local.prefs.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Initial)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onGoogleSignInSuccess(email: String?, name: String?, photoUrl: String?) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                preferencesManager.setLoginStatus(true)
                preferencesManager.setUserData(email, name, photoUrl)
                _uiState.value = LoginUiState.Success
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "Неизвестная ошибка")
            }
        }
    }

    fun onGoogleSignInError(error: String) {
        _uiState.value = LoginUiState.Error(error)
    }

    fun resetState() {
        _uiState.value = LoginUiState.Initial
    }
}

sealed class LoginUiState {
    object Initial : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}
