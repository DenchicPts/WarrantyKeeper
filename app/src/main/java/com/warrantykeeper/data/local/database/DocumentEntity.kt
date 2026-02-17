package com.warrantykeeper.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.warrantykeeper.domain.model.Document
import com.warrantykeeper.domain.model.DocumentType
import java.util.Date

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val type: String, // RECEIPT or WARRANTY
    val photoLocalPath: String,
    val photoCloudPath: String? = null,
    val purchaseDate: Long? = null, // timestamp
    val warrantyEndDate: Long? = null, // timestamp
    val storeName: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val googleDriveFileId: String? = null
)

// Extension functions for mapping
fun DocumentEntity.toDomain(): Document {
    return Document(
        id = id,
        title = title,
        type = DocumentType.valueOf(type),
        photoLocalPath = photoLocalPath,
        photoCloudPath = photoCloudPath,
        purchaseDate = purchaseDate?.let { Date(it) },
        warrantyEndDate = warrantyEndDate?.let { Date(it) },
        storeName = storeName,
        notes = notes,
        createdAt = Date(createdAt),
        updatedAt = Date(updatedAt),
        isSynced = isSynced,
        googleDriveFileId = googleDriveFileId
    )
}

fun Document.toEntity(): DocumentEntity {
    return DocumentEntity(
        id = id,
        title = title,
        type = type.name,
        photoLocalPath = photoLocalPath,
        photoCloudPath = photoCloudPath,
        purchaseDate = purchaseDate?.time,
        warrantyEndDate = warrantyEndDate?.time,
        storeName = storeName,
        notes = notes,
        createdAt = createdAt.time,
        updatedAt = updatedAt.time,
        isSynced = isSynced,
        googleDriveFileId = googleDriveFileId
    )
}
