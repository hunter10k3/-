package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<ChapterEntity>)

    @Query("DELETE FROM chapters WHERE fileUri = :fileUri")
    suspend fun deleteByFileUri(fileUri: String)

    @Query("DELETE FROM chapters")
    suspend fun deleteAll()

    @Query("SELECT * FROM chapters WHERE fileUri = :fileUri ORDER BY `order` ASC, id ASC")
    fun getChaptersForFile(fileUri: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE fileUri = :fileUri ORDER BY `order` ASC, id ASC")
    suspend fun getChaptersForFileSync(fileUri: String): List<ChapterEntity>

    @Query("""
        SELECT * FROM chapters 
        WHERE title LIKE '%' || :query || '%'
        ORDER BY `order` ASC
        LIMIT 200
    """)
    suspend fun searchChapters(query: String): List<ChapterEntity>
}
