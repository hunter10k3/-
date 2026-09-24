package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.DocumentContentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentContentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DocumentContentEntity>)

    @Query("DELETE FROM document_content WHERE documentUri = :documentUri")
    suspend fun deleteByDocumentUri(documentUri: String)

    @Query("DELETE FROM document_content")
    suspend fun deleteAll()

    @Query("""
        SELECT * FROM document_content 
        WHERE text LIKE '%' || :query || '%' 
           OR documentTitle LIKE '%' || :query || '%'
           OR chapterTitle LIKE '%' || :query || '%'
        ORDER BY 
           CASE WHEN documentTitle LIKE '%' || :query || '%' THEN 1 ELSE 2 END,
           id ASC
        LIMIT 300
    """)
    suspend fun searchContent(query: String): List<DocumentContentEntity>

    @Query("""
        SELECT * FROM document_content 
        WHERE sourceType = :sourceType 
          AND (text LIKE '%' || :query || '%' OR chapterTitle LIKE '%' || :query || '%')
        ORDER BY id ASC
        LIMIT 300
    """)
    suspend fun searchBySourceType(query: String, sourceType: String): List<DocumentContentEntity>

    @Query("""
        SELECT * FROM document_content 
        WHERE documentUri = :documentUri
          AND (text LIKE '%' || :query || '%' OR chapterTitle LIKE '%' || :query || '%')
        ORDER BY page ASC, charOffset ASC
    """)
    suspend fun searchInDocument(documentUri: String, query: String): List<DocumentContentEntity>

    @Query("SELECT COUNT(*) FROM document_content WHERE documentUri = :documentUri")
    suspend fun getIndexedChunkCount(documentUri: String): Int

    @Query("SELECT COUNT(DISTINCT documentUri) FROM document_content")
    fun getIndexedDocumentCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM document_content")
    fun getTotalIndexedChunksFlow(): Flow<Int>

    @Query("SELECT DISTINCT documentUri FROM document_content")
    suspend fun getIndexedDocumentUris(): List<String>
}
