package com.warrantykeeper.data.local.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    // Все запросы фильтруются по userId — изоляция пользователей
    @Query("SELECT * FROM documents WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAllDocuments(userId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE userId = :userId AND type = :type ORDER BY createdAt DESC")
    fun getDocumentsByType(userId: String, type: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id AND userId = :userId")
    suspend fun getDocumentById(id: Long, userId: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE userId = :userId AND (title LIKE '%' || :query || '%' OR storeName LIKE '%' || :query || '%') ORDER BY createdAt DESC")
    fun searchDocuments(userId: String, query: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE userId = :userId AND type = 'WARRANTY' AND warrantyEndDate IS NOT NULL AND warrantyEndDate > :currentTime ORDER BY warrantyEndDate ASC")
    fun getActiveWarranties(userId: String, currentTime: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE userId = :userId AND type = 'WARRANTY' AND warrantyEndDate IS NOT NULL AND warrantyEndDate < :currentTime ORDER BY warrantyEndDate DESC")
    fun getExpiredWarranties(userId: String, currentTime: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE userId = :userId AND type = 'WARRANTY' AND warrantyEndDate IS NOT NULL AND warrantyEndDate BETWEEN :startTime AND :endTime ORDER BY warrantyEndDate ASC")
    fun getWarrantiesExpiringSoon(userId: String, startTime: Long, endTime: Long): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id AND userId = :userId")
    suspend fun deleteDocumentById(id: Long, userId: String)

    @Query("SELECT * FROM documents WHERE userId = :userId AND isSynced = 0")
    suspend fun getUnsyncedDocuments(userId: String): List<DocumentEntity>

    // При выходе из аккаунта — очистить данные пользователя из БД
    @Query("DELETE FROM documents WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
