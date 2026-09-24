package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.local.entity.DocumentTextEntity
import com.example.data.local.entity.FtsDocument

@Dao
interface DocumentSearchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocumentText(entity: DocumentTextEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFtsDocument(entity: FtsDocument)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocumentTexts(entities: List<DocumentTextEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFtsList(list: List<FtsDocument>)

    @Query("DELETE FROM document_texts WHERE fileUri = :fileUri")
    suspend fun deleteTextsForFile(fileUri: String)

    @Query("DELETE FROM fts_documents WHERE fileUri = :fileUri")
    suspend fun deleteFtsForFile(fileUri: String)

    @Transaction
    suspend fun replaceDocumentIndex(
        fileUri: String,
        texts: List<DocumentTextEntity>,
        ftsList: List<FtsDocument>
    ) {
        deleteTextsForFile(fileUri)
        deleteFtsForFile(fileUri)
        if (texts.isNotEmpty()) {
            insertDocumentTexts(texts)
        }
        if (ftsList.isNotEmpty()) {
            insertFtsList(ftsList)
        }
    }

    @Query("SELECT contentHash FROM document_texts WHERE fileUri = :fileUri LIMIT 1")
    suspend fun getContentHash(fileUri: String): String?

    @Query("SELECT rowid, fileUri, pageNumber, title, body FROM fts_documents WHERE fts_documents MATCH :query")
    suspend fun searchFts(query: String): List<FtsDocument>

    @Query("SELECT * FROM document_texts WHERE fullText LIKE '%' || :query || '%'")
    suspend fun searchFallback(query: String): List<DocumentTextEntity>

    @Query("SELECT DISTINCT fileUri FROM document_texts")
    suspend fun getIndexedFileUris(): List<String>

    @Query("DELETE FROM document_texts")
    suspend fun clearAllTexts()

    @Query("DELETE FROM fts_documents")
    suspend fun clearAllFts()

    @Transaction
    suspend fun clearAllIndex() {
        clearAllTexts()
        clearAllFts()
    }
}
