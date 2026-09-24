package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.DocumentTocEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentTocDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DocumentTocEntity>)

    @Query("DELETE FROM document_toc WHERE documentUri = :documentUri")
    suspend fun deleteByDocumentUri(documentUri: String)

    @Query("DELETE FROM document_toc")
    suspend fun deleteAll()

    @Query("SELECT * FROM document_toc WHERE documentUri = :documentUri ORDER BY page ASC, charOffset ASC, id ASC")
    fun getTocForDocument(documentUri: String): Flow<List<DocumentTocEntity>>

    @Query("SELECT * FROM document_toc WHERE documentUri = :documentUri ORDER BY page ASC, charOffset ASC, id ASC")
    suspend fun getTocForDocumentSync(documentUri: String): List<DocumentTocEntity>

    @Query("""
        SELECT * FROM document_toc 
        WHERE title LIKE '%' || :query || '%'
        ORDER BY id ASC
        LIMIT 200
    """)
    suspend fun searchToc(query: String): List<DocumentTocEntity>
}
