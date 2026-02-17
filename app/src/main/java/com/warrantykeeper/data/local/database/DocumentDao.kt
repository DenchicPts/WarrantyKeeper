package com.warrantykeeper.data.local.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    
    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>
    
    @Query("SELECT * FROM documents WHERE type = :type ORDER BY createdAt DESC")
    fun getDocumentsByType(type: String): Flow<List<DocumentEntity>>
    
    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): DocumentEntity?
    
    @Query("SELECT * FROM documents WHERE title LIKE '%' || :query || '%' OR storeName LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchDocuments(query: String): Flow<List<DocumentEntity>>
    
    @Query("SELECT * FROM documents WHERE type = 'WARRANTY' AND warrantyEndDate IS NOT NULL AND warrantyEndDate > :currentTime ORDER BY warrantyEndDate ASC")
    fun getActiveWarranties(currentTime: Long): Flow<List<DocumentEntity>>
    
    @Query("SELECT * FROM documents WHERE type = 'WARRANTY' AND warrantyEndDate IS NOT NULL AND warrantyEndDate < :currentTime ORDER BY warrantyEndDate DESC")
    fun getExpiredWarranties(currentTime: Long): Flow<List<DocumentEntity>>
    
    @Query("SELECT * FROM documents WHERE type = 'WARRANTY' AND warrantyEndDate IS NOT NULL AND warrantyEndDate BETWEEN :startTime AND :endTime ORDER BY warrantyEndDate ASC")
    fun getWarrantiesExpiringSoon(startTime: Long, endTime: Long): Flow<List<DocumentEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long
    
    @Update
    suspend fun updateDocument(document: DocumentEntity)
    
    @Delete
    suspend fun deleteDocument(document: DocumentEntity)
    
    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)
    
    @Query("SELECT * FROM documents WHERE isSynced = 0")
    suspend fun getUnsyncedDocuments(): List<DocumentEntity>
}
