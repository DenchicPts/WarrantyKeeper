package com.warrantykeeper.data.repository

import com.warrantykeeper.data.local.database.DocumentDao
import com.warrantykeeper.data.local.database.toDomain
import com.warrantykeeper.data.local.database.toEntity
import com.warrantykeeper.domain.model.Document
import com.warrantykeeper.domain.model.DocumentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor(
    private val documentDao: DocumentDao
) {
    
    fun getAllDocuments(): Flow<List<Document>> {
        return documentDao.getAllDocuments().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    fun getDocumentsByType(type: DocumentType): Flow<List<Document>> {
        return documentDao.getDocumentsByType(type.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    suspend fun getDocumentById(id: Long): Document? {
        return documentDao.getDocumentById(id)?.toDomain()
    }
    
    fun searchDocuments(query: String): Flow<List<Document>> {
        return documentDao.searchDocuments(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    fun getActiveWarranties(): Flow<List<Document>> {
        val currentTime = System.currentTimeMillis()
        return documentDao.getActiveWarranties(currentTime).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    fun getExpiredWarranties(): Flow<List<Document>> {
        val currentTime = System.currentTimeMillis()
        return documentDao.getExpiredWarranties(currentTime).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    fun getWarrantiesExpiringSoon(daysAhead: Int = 30): Flow<List<Document>> {
        val currentTime = System.currentTimeMillis()
        val futureTime = currentTime + (daysAhead * 24 * 60 * 60 * 1000L)
        return documentDao.getWarrantiesExpiringSoon(currentTime, futureTime).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    suspend fun insertDocument(document: Document): Long {
        return documentDao.insertDocument(document.toEntity())
    }
    
    suspend fun updateDocument(document: Document) {
        documentDao.updateDocument(document.toEntity())
    }
    
    suspend fun deleteDocument(document: Document) {
        documentDao.deleteDocument(document.toEntity())
    }
    
    suspend fun deleteDocumentById(id: Long) {
        documentDao.deleteDocumentById(id)
    }
    
    suspend fun getUnsyncedDocuments(): List<Document> {
        return documentDao.getUnsyncedDocuments().map { it.toDomain() }
    }
}
