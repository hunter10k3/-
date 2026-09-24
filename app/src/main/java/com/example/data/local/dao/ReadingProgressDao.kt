package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ReadingProgress
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for ReadingProgress persistence with reactive Flow queries.
 */
@Dao
interface ReadingProgressDao {

    @Query("SELECT * FROM reading_progress WHERE fileUri = :fileUri LIMIT 1")
    fun getReadingProgressFlow(fileUri: String): Flow<ReadingProgress?>

    @Query("SELECT * FROM reading_progress WHERE fileUri = :fileUri LIMIT 1")
    suspend fun getReadingProgress(fileUri: String): ReadingProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReadingProgress(progress: ReadingProgress)

    @Query("DELETE FROM reading_progress WHERE fileUri = :fileUri")
    suspend fun deleteReadingProgress(fileUri: String)

    @Query("DELETE FROM reading_progress")
    suspend fun clearAllReadingProgress()
}
