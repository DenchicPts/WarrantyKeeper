package com.warrantykeeper.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileHelper @Inject constructor(
    private val context: Context
) {

    private val documentsDir: File
        get() {
            val dir = File(context.filesDir, "documents")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(documentsDir, "DOC_${timeStamp}.webp")
    }

    /**
     * Save image from URI, auto-correcting rotation via EXIF so it always displays upright.
     */
    fun saveImage(uri: Uri): File? {
        return try {
            val bitmap = loadAndRotateBitmap(uri) ?: return null
            saveImage(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Load bitmap and rotate it according to EXIF orientation tag.
     */
    private fun loadAndRotateBitmap(uri: Uri): Bitmap? {
        return try {
            val stream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(stream)
            stream.close()

            val exifStream: InputStream? = context.contentResolver.openInputStream(uri)
            val degrees = exifStream?.use { s ->
                try {
                    val exif = ExifInterface(s)
                    val orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> 0f // handle separately if needed
                        else -> 0f
                    }
                } catch (e: Exception) { 0f }
            } ?: 0f

            if (degrees != 0f) {
                val matrix = Matrix().apply { postRotate(degrees) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveImage(bitmap: Bitmap): File {
        val file = createImageFile()
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.WEBP, 85, out)
        }
        return file
    }

    fun getImageFile(path: String): File = File(path)

    fun deleteImage(path: String): Boolean {
        return try {
            File(path).delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getUriForFile(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun loadBitmap(path: String): Bitmap? {
        return try {
            BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getDocumentsDirectory(): File = documentsDir

    fun getAllDocumentFiles(): List<File> = documentsDir.listFiles()?.toList() ?: emptyList()

    fun getDocumentFileSize(path: String): Long {
        return try { File(path).length() } catch (e: Exception) { 0L }
    }

    /**
     * Строит локальный путь для документа восстановленного из Drive.
     * Используется DriveRestoreWorker.
     */
    fun buildLocalPath(docId: Long, driveFileId: String): String {
        val name = "DOC_restored_${docId}.jpg"
        return File(documentsDir, name).absolutePath
    }
}
