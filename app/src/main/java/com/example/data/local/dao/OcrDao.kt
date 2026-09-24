package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.OcrResultEntity

@Dao
interface OcrDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOcrResult(result: OcrResultEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOcrResults(results: List<OcrResultEntity>)

    @Query("SELECT * FROM ocr_results WHERE fileUri = :fileUri ORDER BY pageNumber ASC")
    suspend fun getOcrForFile(fileUri: String): List<OcrResultEntity>

    @Query("SELECT * FROM ocr_results WHERE fileUri = :fileUri ORDER BY pageNumber ASC")
    fun getOcrForFileFlow(fileUri: String): kotlinx.coroutines.flow.Flow<List<OcrResultEntity>>

    @Query("SELECT * FROM ocr_results WHERE fileUri = :fileUri AND pageNumber = :pageNumber LIMIT 1")
    suspend fun getOcrForPage(fileUri: String, pageNumber: Int): OcrResultEntity?

    @Query("SELECT COUNT(*) FROM ocr_results WHERE fileUri = :fileUri")
    suspend fun getOcrPageCountForFile(fileUri: String): Int

    @Query("SELECT * FROM ocr_results WHERE text LIKE '%' || :query || '%'")
    suspend fun searchOcr(query: String): List<OcrResultEntity>

    @Query("DELETE FROM ocr_results WHERE fileUri = :fileUri")
    suspend fun deleteOcrForFile(fileUri: String)

    @Query("DELETE FROM ocr_results")
    suspend fun clearAllOcr()
}
