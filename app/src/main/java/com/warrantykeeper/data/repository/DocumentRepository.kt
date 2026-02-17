package com.warrantykeeper.data.repository

import com.warrantykeeper.data.local.database.DocumentDao
import com.warrantykeeper.data.local.database.DocumentEntity
import com.warrantykeeper.data.local.database.toDomain
import com.warrantykeeper.data.local.database.toEntity
import com.warrantykeeper.data.local.prefs.PreferencesManager
import com.warrantykeeper.domain.model.Document
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DocumentRepository @Inject constructor(
    private val documentDao: DocumentDao,
    private val preferencesManager: PreferencesManager
) {
    private suspend fun currentUserId(): String =
        preferencesManager.userEmail.first() ?: ""

    /**
     * flatMapLatest: при смене email — Flow немедленно пересоздаётся.
     * Это исправляет баг с фантомными чеками после удаления.
     */
    fun getAllDocuments(): Flow<List<Document>> =
        preferencesManager.userEmail.flatMapLatest { email ->
            documentDao.getAllDocuments(email ?: "")
                .map { list -> list.map { it.toDomain() } }
        }

    fun searchDocuments(query: String): Flow<List<Document>> =
        preferencesManager.userEmail.flatMapLatest { email ->
            documentDao.searchDocuments(email ?: "", query)
                .map { list -> list.map { it.toDomain() } }
        }

    suspend fun getDocumentById(id: Long): Document? {
        val userId = currentUserId()
        return documentDao.getDocumentById(id, userId)?.toDomain()
    }

    suspend fun insertDocument(document: Document): Long {
        val userId = currentUserId()
        return documentDao.insertDocument(document.copy(userId = userId).toEntity())
    }

    suspend fun updateDocument(document: Document) {
        documentDao.updateDocument(document.toEntity())
    }

    suspend fun deleteDocument(document: Document) {
        documentDao.deleteDocument(document.toEntity())
    }

    suspend fun deleteDocumentById(id: Long) {
        val userId = currentUserId()
        documentDao.deleteDocumentById(id, userId)
    }

    suspend fun getUnsyncedDocuments(): List<Document> {
        val userId = currentUserId()
        return documentDao.getUnsyncedDocuments(userId).map { it.toDomain() }
    }

    suspend fun clearUserData(userId: String) {
        documentDao.deleteAllForUser(userId)
    }
}
