package com.warrantykeeper.presentation.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warrantykeeper.data.remote.GoogleDriveManager
import com.warrantykeeper.data.repository.DocumentRepository
import com.warrantykeeper.domain.model.Document
import com.warrantykeeper.utils.FileHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val fileHelper: FileHelper,
    private val googleDriveManager: GoogleDriveManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val documentId: Long = checkNotNull(savedStateHandle["documentId"])

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        loadDocument()
    }

    private fun loadDocument() {
        viewModelScope.launch {
            val document = repository.getDocumentById(documentId)
            _uiState.value = if (document != null) {
                DetailUiState.Success(document)
            } else {
                DetailUiState.Error("Документ не найден")
            }
        }
    }

    fun updateDocument(
        title: String,
        storeName: String?,
        purchaseDate: Date?,
        warrantyEndDate: Date?,
        notes: String?
    ) {
        val currentState = _uiState.value as? DetailUiState.Success ?: return
        viewModelScope.launch {
            val updatedDoc = currentState.document.copy(
                title = title.ifBlank { currentState.document.title },
                storeName = storeName?.ifBlank { null },
                purchaseDate = purchaseDate,
                warrantyEndDate = warrantyEndDate,
                notes = notes?.ifBlank { null },
                updatedAt = Date(),
                isSynced = false
            )
            repository.updateDocument(updatedDoc)
            _uiState.value = DetailUiState.Success(updatedDoc)
            syncToDrive(updatedDoc)
        }
    }

    fun deleteDocument(onDeleted: () -> Unit) {
        val currentState = _uiState.value as? DetailUiState.Success ?: return
        viewModelScope.launch {
            val doc = currentState.document
            doc.googleDriveFileId?.let { fileId ->
                googleDriveManager.deleteFile(fileId)
            }
            fileHelper.deleteImage(doc.photoLocalPath)
            repository.deleteDocument(doc)
            onDeleted()
        }
    }

    fun syncToDrive(document: Document? = null) {
        val doc = document ?: (_uiState.value as? DetailUiState.Success)?.document ?: return
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val result = googleDriveManager.uploadDocument(doc)
                if (result != null) {
                    val syncedDoc = doc.copy(
                        isSynced = true,
                        googleDriveFileId = result.fileId,
                        photoCloudPath = result.webViewLink
                    )
                    repository.updateDocument(syncedDoc)
                    _uiState.value = DetailUiState.Success(syncedDoc)
                }
            } catch (e: Exception) {
                // Sync failed silently
            } finally {
                _isSyncing.value = false
            }
        }
    }
}

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Success(val document: Document) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}
