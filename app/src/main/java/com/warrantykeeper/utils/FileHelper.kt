package com.warrantykeeper.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
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
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "DOC_${timeStamp}.webp"
        return File(documentsDir, fileName)
    }

    fun saveImage(uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            saveImage(bitmap)
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

    fun getImageFile(path: String): File {
        return File(path)
    }

    fun deleteImage(path: String): Boolean {
        return try {
            val file = File(path)
            file.delete()
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

    fun getDocumentsDirectory(): File {
        return documentsDir
    }

    fun getAllDocumentFiles(): List<File> {
        return documentsDir.listFiles()?.toList() ?: emptyList()
    }

    fun getDocumentFileSize(path: String): Long {
        return try {
            File(path).length()
        } catch (e: Exception) {
            0L
        }
    }
}
