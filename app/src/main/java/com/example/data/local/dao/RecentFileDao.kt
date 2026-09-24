package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.RecentFile
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for RecentFile persistence with reactive Flow queries.
 */
@Dao
interface RecentFileDao {

    @Query("SELECT * FROM recent_files ORDER BY openedAt DESC")
    fun getAllRecentFiles(): Flow<List<RecentFile>>

    @Query("SELECT * FROM recent_files ORDER BY openedAt DESC")
    fun getRecentDocuments(): Flow<List<RecentFile>>

    @Query("SELECT * FROM recent_files ORDER BY openedAt DESC LIMIT :limit")
    fun getRecentFilesFlow(limit: Int = 20): Flow<List<RecentFile>>

    @Query("SELECT * FROM recent_files WHERE uri = :uri LIMIT 1")
    fun getRecentFileFlow(uri: String): Flow<RecentFile?>

    @Query("SELECT * FROM recent_files WHERE uri = :uri LIMIT 1")
    suspend fun getRecentFile(uri: String): RecentFile?

    @Query("SELECT * FROM recent_files WHERE uri = :uri LIMIT 1")
    suspend fun getRecentDocument(uri: String): RecentFile?

    @Query("SELECT COUNT(*) FROM recent_files")
    fun getRecentFilesCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentFile(file: RecentFile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentDocument(document: RecentFile)

    @Query("DELETE FROM recent_files WHERE uri = :uri")
    suspend fun deleteRecentFile(uri: String)

    @Query("DELETE FROM recent_files WHERE uri = :uri")
    suspend fun deleteRecentDocument(uri: String)

    @Query("DELETE FROM recent_files")
    suspend fun clearAllRecentFiles()

    @Query("DELETE FROM recent_files")
    suspend fun clearRecentDocuments()
}

/**
 * Typealias for seamless interoperability with existing code.
 */
typealias RecentDocumentDao = RecentFileDao
