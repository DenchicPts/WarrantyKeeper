package com.warrantykeeper.domain.usecase

import android.net.Uri
import com.warrantykeeper.data.repository.DocumentRepository
import com.warrantykeeper.domain.model.Document
import com.warrantykeeper.domain.model.DocumentType
import com.warrantykeeper.utils.FileHelper
import com.warrantykeeper.utils.OCRProcessor
import java.util.*
import javax.inject.Inject

class AddDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository,
    private val fileHelper: FileHelper,
    private val ocrProcessor: OCRProcessor
) {
    
    suspend operator fun invoke(
        imageUri: Uri,
        isWarranty: Boolean
    ): Result<Long> {
        return try {
            // Сохраняем изображение
            val savedFile = fileHelper.saveImage(imageUri)
                ?: return Result.failure(Exception("Failed to save image"))

            if (isWarranty) {
                // Обрабатываем с помощью OCR
                val warrantyInfo = ocrProcessor.processImage(imageUri)
                
                val document = Document(
                    title = warrantyInfo.productName ?: "Документ ${Date().time}",
                    type = DocumentType.WARRANTY,
                    photoLocalPath = savedFile.absolutePath,
                    purchaseDate = warrantyInfo.purchaseDate,
                    warrantyEndDate = warrantyInfo.warrantyEndDate,
                    storeName = warrantyInfo.storeName,
                    createdAt = Date(),
                    updatedAt = Date()
                )
                
                val id = repository.insertDocument(document)
                Result.success(id)
            } else {
                // Простой чек без обработки
                val document = Document(
                    title = "Чек ${Date().time}",
                    type = DocumentType.RECEIPT,
                    photoLocalPath = savedFile.absolutePath,
                    createdAt = Date(),
                    updatedAt = Date()
                )
                
                val id = repository.insertDocument(document)
                Result.success(id)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
