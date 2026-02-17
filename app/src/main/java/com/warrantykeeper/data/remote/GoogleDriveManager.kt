package com.warrantykeeper.data.remote

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.InputStreamContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.warrantykeeper.domain.model.Document
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class DriveUploadResult(
    val fileId: String,
    val webViewLink: String?
)

/**
 * Google Drive integration для WarrantyKeeper.
 *
 * ВАЖНО про drive.file scope:
 * - Это NON-SENSITIVE scope — не требует верификации от Google
 * - Приложение видит ТОЛЬКО файлы которые оно само создало
 * - files().list() возвращает только наши файлы (не чужие) — это безопасно и правильно
 * - При первом запуске папки нет → create() создаёт её
 *
 * НАСТРОЙКА в Firebase/Google Cloud Console:
 * 1. Google Cloud Console → APIs & Services → Credentials → OAuth consent screen
 *    → Add scope: https://www.googleapis.com/auth/drive.file
 * 2. Google Cloud Console → APIs & Services → Library → Google Drive API → Enable
 * 3. SHA-1 fingerprint приложения должен быть добавлен в OAuth 2.0 Android credentials
 *
 * Без п.2 и п.3 Drive API будет возвращать 403 даже при правильном scope.
 */
@Singleton
class GoogleDriveManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "GoogleDriveManager"
        const val FOLDER_NAME = "WarrantyKeeper"
        // drive.file — NON-SENSITIVE, достаточно для работы с нашими файлами
        const val DRIVE_SCOPE = DriveScopes.DRIVE_FILE
        private const val APP_NAME = "WarrantyKeeper"
        private const val METADATA_FILENAME = "metadata.json"
    }

    fun isDrivePermissionGranted(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(account, Scope(DRIVE_SCOPE))
    }

    private fun getDriveService(): Drive? {
        return try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account == null) {
                Log.w(TAG, "No signed-in Google account")
                return null
            }
            if (!GoogleSignIn.hasPermissions(account, Scope(DRIVE_SCOPE))) {
                Log.w(TAG, "Drive scope not granted")
                return null
            }
            val credential = GoogleAccountCredential.usingOAuth2(
                context, listOf(DRIVE_SCOPE)
            )
            credential.selectedAccount = account.account
            Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName(APP_NAME)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "getDriveService error: ${e.message}", e)
            null
        }
    }

    // ─── Folder management ────────────────────────────────────────────────────
    //
    // drive.file scope: files().list() возвращает только файлы созданные нашим приложением.
    // Это именно то что нам нужно — мы ищем нашу папку WarrantyKeeper.

    private suspend fun getOrCreateRootFolder(drive: Drive): String? =
        withContext(Dispatchers.IO) {
            try {
                // Ищем папку WarrantyKeeper среди наших файлов
                val q = "name='$FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false"
                val list = drive.files().list()
                    .setQ(q)
                    .setSpaces("drive")
                    .setFields("files(id,name)")
                    .execute()

                if (list.files.isNotEmpty()) {
                    Log.d(TAG, "Root folder exists: ${list.files[0].id}")
                    return@withContext list.files[0].id
                }

                // Создаём папку
                val meta = DriveFile().apply {
                    name = FOLDER_NAME
                    mimeType = "application/vnd.google-apps.folder"
                }
                val created = drive.files().create(meta).setFields("id").execute()
                Log.i(TAG, "Created root folder: ${created.id}")
                created.id
            } catch (e: Exception) {
                Log.e(TAG, "getOrCreateRootFolder: ${e.message}", e)
                null
            }
        }

    private suspend fun getUserFolder(drive: Drive, userEmail: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val rootId = getOrCreateRootFolder(drive) ?: return@withContext null
                val safeName = userEmail.replace("@", "_at_").replace(".", "_")

                val q = "name='$safeName' and mimeType='application/vnd.google-apps.folder' and '$rootId' in parents and trashed=false"
                val list = drive.files().list()
                    .setQ(q)
                    .setSpaces("drive")
                    .setFields("files(id,name)")
                    .execute()

                if (list.files.isNotEmpty()) {
                    return@withContext list.files[0].id
                }

                val meta = DriveFile().apply {
                    name = safeName
                    mimeType = "application/vnd.google-apps.folder"
                    parents = listOf(rootId)
                }
                drive.files().create(meta).setFields("id").execute().id
                    .also { Log.i(TAG, "Created user folder '$safeName': $it") }
            } catch (e: Exception) {
                Log.e(TAG, "getUserFolder: ${e.message}", e)
                null
            }
        }

    // ─── Upload document photo ────────────────────────────────────────────────

    suspend fun uploadDocument(document: Document): DriveUploadResult? =
        withContext(Dispatchers.IO) {
            try {
                val drive = getDriveService() ?: return@withContext null
                val email = GoogleSignIn.getLastSignedInAccount(context)?.email
                    ?: return@withContext null
                val folderId = getUserFolder(drive, email) ?: return@withContext null

                val photo = File(document.photoLocalPath)
                if (!photo.exists()) {
                    Log.e(TAG, "Photo not found: ${document.photoLocalPath}")
                    return@withContext null
                }

                // Удаляем старую версию файла если есть
                document.googleDriveFileId?.let { oldId ->
                    runCatching { drive.files().delete(oldId).execute() }
                        .onFailure { Log.w(TAG, "Could not delete old $oldId: ${it.message}") }
                }

                val mime = when {
                    document.photoLocalPath.endsWith(".webp", ignoreCase = true) -> "image/webp"
                    document.photoLocalPath.endsWith(".png",  ignoreCase = true) -> "image/png"
                    else -> "image/jpeg"
                }
                val fileName = "doc_${document.id}_${photo.name}"
                val meta    = DriveFile().apply { name = fileName; parents = listOf(folderId) }
                val content = FileContent(mime, photo)

                val uploaded = drive.files().create(meta, content)
                    .setFields("id,webViewLink").execute()

                Log.i(TAG, "Uploaded '${photo.name}' → fileId=${uploaded.id}")
                DriveUploadResult(fileId = uploaded.id, webViewLink = uploaded.webViewLink)
            } catch (e: Exception) {
                Log.e(TAG, "uploadDocument failed: ${e.message}", e)
                null
            }
        }

    // ─── Upload metadata.json ─────────────────────────────────────────────────

    suspend fun uploadMetadata(userEmail: String, json: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val drive = getDriveService() ?: return@withContext false
                val folderId = getUserFolder(drive, userEmail) ?: return@withContext false

                // Ищем существующий metadata.json в папке пользователя
                val existing = drive.files().list()
                    .setQ("name='$METADATA_FILENAME' and '$folderId' in parents and trashed=false")
                    .setSpaces("drive")
                    .setFields("files(id)")
                    .execute()

                val stream = InputStreamContent(
                    "application/json",
                    ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))
                )

                if (existing.files.isNotEmpty()) {
                    // Обновляем существующий файл (update не меняет parents)
                    drive.files().update(existing.files[0].id, DriveFile(), stream).execute()
                    Log.d(TAG, "Updated metadata.json")
                } else {
                    // Создаём новый
                    val meta = DriveFile().apply {
                        name = METADATA_FILENAME
                        parents = listOf(folderId)
                    }
                    drive.files().create(meta, stream).setFields("id").execute()
                    Log.d(TAG, "Created metadata.json")
                }
                Log.i(TAG, "Metadata uploaded for $userEmail")
                true
            } catch (e: Exception) {
                Log.e(TAG, "uploadMetadata failed: ${e.message}", e)
                false
            }
        }

    // ─── Download metadata.json ───────────────────────────────────────────────

    suspend fun downloadMetadata(userEmail: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val drive = getDriveService() ?: return@withContext null
                val folderId = getUserFolder(drive, userEmail) ?: return@withContext null

                val found = drive.files().list()
                    .setQ("name='$METADATA_FILENAME' and '$folderId' in parents and trashed=false")
                    .setSpaces("drive")
                    .setFields("files(id,name)")
                    .execute()

                if (found.files.isEmpty()) {
                    Log.d(TAG, "No metadata.json in Drive for $userEmail")
                    return@withContext null
                }

                val outputStream = ByteArrayOutputStream()
                drive.files().get(found.files[0].id).executeMediaAndDownloadTo(outputStream)
                val json = outputStream.toString(Charsets.UTF_8.name())
                Log.i(TAG, "Downloaded metadata.json (${json.length} chars)")
                json
            } catch (e: Exception) {
                Log.e(TAG, "downloadMetadata failed: ${e.message}", e)
                null
            }
        }

    // ─── Download photo from Drive ────────────────────────────────────────────

    suspend fun downloadPhoto(driveFileId: String, localPath: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val drive = getDriveService() ?: return@withContext false
                val localFile = File(localPath)
                localFile.parentFile?.mkdirs()
                FileOutputStream(localFile).use { fos ->
                    drive.files().get(driveFileId).executeMediaAndDownloadTo(fos)
                }
                Log.i(TAG, "Downloaded photo $driveFileId → $localPath")
                true
            } catch (e: Exception) {
                Log.e(TAG, "downloadPhoto $driveFileId failed: ${e.message}", e)
                false
            }
        }

    // ─── Delete ───────────────────────────────────────────────────────────────

    suspend fun deleteFile(fileId: String) = withContext(Dispatchers.IO) {
        runCatching {
            getDriveService()?.files()?.delete(fileId)?.execute()
            Log.d(TAG, "Deleted Drive file $fileId")
        }.onFailure { Log.w(TAG, "deleteFile $fileId failed: ${it.message}") }
    }

    fun isAvailable(): Boolean = isDrivePermissionGranted()

    fun getCurrentUserEmail(): String? =
        GoogleSignIn.getLastSignedInAccount(context)?.email
}
