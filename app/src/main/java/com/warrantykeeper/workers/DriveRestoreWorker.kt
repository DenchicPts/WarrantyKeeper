package com.warrantykeeper.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.gson.Gson
import com.warrantykeeper.data.local.prefs.PreferencesManager
import com.warrantykeeper.data.remote.GoogleDriveManager
import com.warrantykeeper.data.repository.DocumentRepository
import com.warrantykeeper.domain.model.Document
import com.warrantykeeper.domain.model.DocumentType
import com.warrantykeeper.utils.FileHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.*

/**
 * Запускается один раз при первом входе (или при смене аккаунта).
 * Скачивает metadata.json из Drive и восстанавливает записи в Room.
 * Фотографии загружаются лениво — только при открытии документа.
 */
@HiltWorker
class DriveRestoreWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val googleDriveManager: GoogleDriveManager,
    private val documentRepository: DocumentRepository,
    private val preferencesManager: PreferencesManager,
    private val fileHelper: FileHelper
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "DriveRestoreWorker"
        const val WORK_NAME = "DriveRestoreWorker"

        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<DriveRestoreWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
            Log.d(TAG, "DriveRestoreWorker enqueued")
        }
    }

    private val gson = Gson()

    override suspend fun doWork(): Result {
        if (!googleDriveManager.isAvailable()) {
            Log.d(TAG, "Drive not available — skipping restore")
            return Result.success()
        }

        val email = preferencesManager.userEmail.first()
        if (email.isNullOrEmpty()) {
            Log.d(TAG, "No email — skipping restore")
            return Result.success()
        }

        return try {
            val json = googleDriveManager.downloadMetadata(email)
            if (json.isNullOrEmpty()) {
                Log.d(TAG, "No metadata.json in Drive — nothing to restore")
                return Result.success()
            }

            val metadata = gson.fromJson(json, MetadataJson::class.java)
            Log.i(TAG, "Restoring ${metadata.documents.size} documents from Drive")

            // Получаем существующие документы чтобы не дублировать
            val existing = documentRepository.getAllDocuments().first()
            val existingIds = existing.map { it.id }.toSet()

            for (meta in metadata.documents) {
                if (meta.id in existingIds) continue  // уже есть локально

                // Создаём placeholder запись — фото будет загружено по требованию
                val localPhotoPath = fileHelper.buildLocalPath(meta.id, meta.driveFileId ?: "")

                val doc = Document(
                    id              = meta.id,
                    userId          = email,
                    title           = meta.title,
                    type            = runCatching { DocumentType.valueOf(meta.type) }.getOrElse { DocumentType.RECEIPT },
                    photoLocalPath  = localPhotoPath,
                    photoCloudPath  = null,
                    purchaseDate    = meta.purchaseDate?.let { Date(it) },
                    warrantyEndDate = meta.warrantyEndDate?.let { Date(it) },
                    storeName       = meta.storeName,
                    notes           = meta.notes,
                    totalAmount     = meta.totalAmount,
                    currency        = meta.currency,
                    createdAt       = Date(meta.createdAt),
                    updatedAt       = Date(),
                    isSynced        = true,
                    googleDriveFileId = meta.driveFileId
                )
                documentRepository.insertDocument(doc)

                // Сразу загружаем фото в фоне если есть driveFileId
                if (meta.driveFileId != null) {
                    googleDriveManager.downloadPhoto(meta.driveFileId, localPhotoPath)
                }
            }

            Log.i(TAG, "Restore complete: ${metadata.documents.size} docs processed")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed: ${e.message}", e)
            Result.retry()
        }
    }
}
