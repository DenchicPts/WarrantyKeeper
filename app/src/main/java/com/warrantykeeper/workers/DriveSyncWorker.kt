package com.warrantykeeper.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.warrantykeeper.data.local.prefs.PreferencesManager
import com.warrantykeeper.data.remote.GoogleDriveManager
import com.warrantykeeper.data.repository.DocumentRepository
import com.warrantykeeper.domain.model.Document
import com.warrantykeeper.domain.model.DocumentType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.*
import java.util.concurrent.TimeUnit

// ─── JSON model for metadata.json ────────────────────────────────────────────

data class MetadataJson(
    val version: Int = 1,
    val lastSync: String = "",
    val documents: List<DocumentMeta> = emptyList()
)

data class DocumentMeta(
    val id: Long,
    val title: String,
    val type: String,
    val driveFileId: String?,
    val purchaseDate: Long?,
    val warrantyEndDate: Long?,
    val storeName: String?,
    val notes: String?,
    val totalAmount: Double?,
    val currency: String?,
    val createdAt: Long
)

// ─── Worker ───────────────────────────────────────────────────────────────────

@HiltWorker
class DriveSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val googleDriveManager: GoogleDriveManager,
    private val documentRepository: DocumentRepository,
    private val preferencesManager: PreferencesManager
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "DriveSyncWorker"
        const val WORK_NAME = "DriveSyncWorker"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DriveSyncWorker>(
                30, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "DriveSyncWorker scheduled (every 30 min)")
        }

        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<DriveSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
            Log.d(TAG, "DriveSyncWorker one-shot enqueued")
        }
    }

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    override suspend fun doWork(): Result {
        if (!googleDriveManager.isAvailable()) {
            Log.d(TAG, "Drive not available — skipping sync")
            return Result.success()
        }

        val email = preferencesManager.userEmail.first()
        if (email.isNullOrEmpty()) {
            Log.d(TAG, "No user email — skipping sync")
            return Result.success()
        }

        return try {
            // 1. Upload all unsynced documents
            val unsynced = documentRepository.getUnsyncedDocuments()
            Log.d(TAG, "Found ${unsynced.size} unsynced documents")

            for (doc in unsynced) {
                val result = googleDriveManager.uploadDocument(doc)
                if (result != null) {
                    documentRepository.updateDocument(
                        doc.copy(
                            isSynced = true,
                            googleDriveFileId = result.fileId,
                            photoCloudPath = result.webViewLink
                        )
                    )
                    Log.d(TAG, "Synced doc ${doc.id}: ${doc.title}")
                }
            }

            // 2. Upload metadata.json with ALL current documents
            val allDocs = documentRepository.getAllDocuments().first()
            val metadata = buildMetadata(allDocs)
            googleDriveManager.uploadMetadata(email, metadata)

            Log.i(TAG, "Sync complete for $email")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}", e)
            Result.retry()
        }
    }

    private fun buildMetadata(documents: List<Document>): String {
        val metas = documents.map { doc ->
            DocumentMeta(
                id             = doc.id,
                title          = doc.title,
                type           = doc.type.name,
                driveFileId    = doc.googleDriveFileId,
                purchaseDate   = doc.purchaseDate?.time,
                warrantyEndDate = doc.warrantyEndDate?.time,
                storeName      = doc.storeName,
                notes          = doc.notes,
                totalAmount    = doc.totalAmount,
                currency       = doc.currency,
                createdAt      = doc.createdAt.time
            )
        }
        val payload = MetadataJson(
            version   = 1,
            lastSync  = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .also { it.timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date()),
            documents = metas
        )
        return gson.toJson(payload)
    }
}
