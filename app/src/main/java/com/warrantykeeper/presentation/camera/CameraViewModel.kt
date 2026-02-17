package com.warrantykeeper.presentation.camera

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warrantykeeper.domain.usecase.AddDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val addDocumentUseCase: AddDocumentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.Initial)
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _capturedImageUri = MutableStateFlow<Uri?>(null)
    val capturedImageUri: StateFlow<Uri?> = _capturedImageUri.asStateFlow()

    fun onImageCaptured(uri: Uri) {
        _capturedImageUri.value = uri
        _uiState.value = CameraUiState.ImageCaptured
    }

    fun onRetakePhoto() {
        _capturedImageUri.value = null
        _uiState.value = CameraUiState.Initial
    }

    fun onSaveDocument(isWarranty: Boolean) {
        val uri = _capturedImageUri.value ?: return
        
        viewModelScope.launch {
            _uiState.value = CameraUiState.Processing
            
            val result = addDocumentUseCase(uri, isWarranty)
            
            _uiState.value = if (result.isSuccess) {
                CameraUiState.Success(result.getOrNull() ?: 0)
            } else {
                CameraUiState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun resetState() {
        _uiState.value = CameraUiState.Initial
        _capturedImageUri.value = null
    }
}

sealed class CameraUiState {
    object Initial : CameraUiState()
    object ImageCaptured : CameraUiState()
    object Processing : CameraUiState()
    data class Success(val documentId: Long) : CameraUiState()
    data class Error(val message: String) : CameraUiState()
}
