package com.warrantykeeper.domain.usecase

import android.net.Uri
import com.warrantykeeper.data.local.prefs.PreferencesManager
import com.warrantykeeper.data.remote.GoogleDriveManager
import com.warrantykeeper.data.repository.DocumentRepository
import com.warrantykeeper.domain.model.Document
import com.warrantykeeper.domain.model.DocumentType
import com.warrantykeeper.utils.FileHelper
import com.warrantykeeper.utils.OCRProcessor
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class AddDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository,
    private val fileHelper: FileHelper,
    private val ocrProcessor: OCRProcessor,
    private val googleDriveManager: GoogleDriveManager,
    private val preferencesManager: PreferencesManager
) {
    suspend operator fun invoke(imageUri: Uri, isWarranty: Boolean): Result<Long> {
        return try {
            val userId = preferencesManager.userEmail.first() ?: ""
            val dateLabel = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())

            // Сохраняем изображение локально (с исправлением поворота через EXIF)
            val savedFile = fileHelper.saveImage(imageUri)
                ?: return Result.failure(Exception("Не удалось сохранить изображение"))

            val document = if (isWarranty) {
                val warrantyInfo = ocrProcessor.processImage(imageUri)
                Document(
                    userId = userId,
                    title = warrantyInfo.productName ?: "Гарантия $dateLabel",
                    type = DocumentType.WARRANTY,
                    photoLocalPath = savedFile.absolutePath,
                    purchaseDate = warrantyInfo.purchaseDate,
                    warrantyEndDate = warrantyInfo.warrantyEndDate,
                    storeName = warrantyInfo.storeName,
                    createdAt = Date(),
                    updatedAt = Date(),
                    isSynced = false
                )
            } else {
                Document(
                    userId = userId,
                    title = "Чек $dateLabel",
                    type = DocumentType.RECEIPT,
                    photoLocalPath = savedFile.absolutePath,
                    createdAt = Date(),
                    updatedAt = Date(),
                    isSynced = false
                )
            }

            val id = repository.insertDocument(document)

            // Авто-синхронизация с Google Drive (некритичная, не блокирует)
            try {
                if (googleDriveManager.isAvailable()) {
                    val savedDoc = document.copy(id = id)
                    val driveResult = googleDriveManager.uploadDocument(savedDoc)
                    if (driveResult != null) {
                        repository.updateDocument(
                            savedDoc.copy(
                                isSynced = true,
                                googleDriveFileId = driveResult.fileId,
                                photoCloudPath = driveResult.webViewLink
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Ошибка синхронизации некритична — документ уже сохранён локально
            }

            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
