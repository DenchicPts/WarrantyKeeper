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
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class DriveUploadResult(
    val fileId: String,
    val webViewLink: String?
)

@Singleton
class GoogleDriveManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "GoogleDriveManager"
        const val FOLDER_NAME = "WarrantyKeeper"
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val APP_NAME = "WarrantyKeeper"
    }

    /**
     * Проверяет что пользователь вошёл И выдал разрешение на Drive.
     * Это ключевая проверка — без неё API вернёт 403.
     */
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
                Log.w(TAG, "Drive scope not granted — user must re-login")
                return null
            }

            val credential = GoogleAccountCredential.usingOAuth2(
                context, listOf(DriveScopes.DRIVE_FILE)
            )
            credential.selectedAccount = account.account

            Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName(APP_NAME)
                .build()
                .also { Log.d(TAG, "Drive service ready for ${account.email}") }
        } catch (e: Exception) {
            Log.e(TAG, "getDriveService error: ${e.message}", e)
            null
        }
    }

    // ─── Folder management ───────────────────────────────────────────────────

    private suspend fun getUserFolder(drive: Drive, userEmail: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val root = getOrCreateFolder(drive, FOLDER_NAME, null)
                    ?: return@withContext null
                val safeName = userEmail.replace("@", "_at_").replace(".", "_")
                getOrCreateFolder(drive, safeName, root)
            } catch (e: Exception) {
                Log.e(TAG, "getUserFolder: ${e.message}")
                null
            }
        }

    private fun getOrCreateFolder(drive: Drive, name: String, parentId: String?): String? {
        val parentClause = if (parentId != null) " and '$parentId' in parents" else ""
        val q = "name='$name' and mimeType='application/vnd.google-apps.folder' and trashed=false$parentClause"
        val list = drive.files().list().setQ(q).setSpaces("drive").setFields("files(id)").execute()

        if (list.files.isNotEmpty()) {
            return list.files[0].id.also { Log.d(TAG, "Folder '$name' exists: $it") }
        }

        val meta = DriveFile().apply {
            this.name = name
            mimeType = "application/vnd.google-apps.folder"
            if (parentId != null) parents = listOf(parentId)
        }
        return drive.files().create(meta).setFields("id").execute().id
            .also { Log.d(TAG, "Created folder '$name': $it") }
    }

    // ─── Upload document photo ────────────────────────────────────────────────

    suspend fun uploadDocument(document: Document): DriveUploadResult? =
        withContext(Dispatchers.IO) {
            try {
                val drive = getDriveService() ?: run {
                    Log.w(TAG, "Drive unavailable (scope not granted?)")
                    return@withContext null
                }
                val email = GoogleSignIn.getLastSignedInAccount(context)?.email
                    ?: return@withContext null

                val folderId = getUserFolder(drive, email)
                    ?: return@withContext null.also { Log.e(TAG, "Folder creation failed") }

                val photo = File(document.photoLocalPath)
                if (!photo.exists()) {
                    Log.e(TAG, "Photo not found: ${document.photoLocalPath}")
                    return@withContext null
                }

                // Delete old version if exists
                document.googleDriveFileId?.let { oldId ->
                    runCatching { drive.files().delete(oldId).execute() }
                        .onFailure { Log.w(TAG, "Could not delete old file $oldId: ${it.message}") }
                }

                val mime = when {
                    document.photoLocalPath.endsWith(".webp") -> "image/webp"
                    document.photoLocalPath.endsWith(".png")  -> "image/png"
                    else -> "image/jpeg"
                }
                val fileName = "doc_${document.id}_${photo.name}"
                val meta = DriveFile().apply { name = fileName; parents = listOf(folderId) }
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

    // ─── Upload JSON metadata ─────────────────────────────────────────────────

    suspend fun uploadMetadata(userEmail: String, json: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val drive = getDriveService() ?: return@withContext false
                val folderId = getUserFolder(drive, userEmail) ?: return@withContext false
                val fname = "metadata.json"

                val existing = drive.files().list()
                    .setQ("name='$fname' and '$folderId' in parents and trashed=false")
                    .setSpaces("drive").setFields("files(id)").execute()

                val stream = InputStreamContent("application/json", ByteArrayInputStream(json.toByteArray()))

                if (existing.files.isNotEmpty()) {
                    drive.files().update(existing.files[0].id, DriveFile(), stream).execute()
                } else {
                    val meta = DriveFile().apply { name = fname; parents = listOf(folderId) }
                    drive.files().create(meta, stream).setFields("id").execute()
                }
                Log.i(TAG, "Metadata uploaded for $userEmail")
                true
            } catch (e: Exception) {
                Log.e(TAG, "uploadMetadata failed: ${e.message}")
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
