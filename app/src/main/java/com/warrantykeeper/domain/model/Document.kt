package com.warrantykeeper.domain.model

import java.util.Date

enum class DocumentType {
    RECEIPT,
    WARRANTY
}

enum class WarrantyStatus {
    ACTIVE,
    EXPIRING_SOON, // < 30 days
    EXPIRED
}

data class Document(
    val id: Long = 0,
    val title: String,
    val type: DocumentType,
    val photoLocalPath: String,
    val photoCloudPath: String? = null,
    val purchaseDate: Date? = null,
    val warrantyEndDate: Date? = null,
    val storeName: String? = null,
    val notes: String? = null,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val isSynced: Boolean = false,
    val googleDriveFileId: String? = null
) {
    fun getWarrantyStatus(): WarrantyStatus? {
        if (type != DocumentType.WARRANTY || warrantyEndDate == null) return null
        
        val now = Date()
        val daysUntilExpiry = ((warrantyEndDate.time - now.time) / (1000 * 60 * 60 * 24)).toInt()
        
        return when {
            daysUntilExpiry < 0 -> WarrantyStatus.EXPIRED
            daysUntilExpiry <= 30 -> WarrantyStatus.EXPIRING_SOON
            else -> WarrantyStatus.ACTIVE
        }
    }
    
    fun getDaysUntilExpiry(): Int? {
        if (warrantyEndDate == null) return null
        val now = Date()
        return ((warrantyEndDate.time - now.time) / (1000 * 60 * 60 * 24)).toInt()
    }
}
