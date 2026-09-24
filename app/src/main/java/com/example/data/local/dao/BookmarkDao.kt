package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.Bookmark
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Bookmark persistence with reactive Flow queries.
 */
@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE fileUri = :fileUri ORDER BY page ASC, createdAt DESC")
    fun getBookmarksForFile(fileUri: String): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE fileUri = :documentUri ORDER BY page ASC, createdAt DESC")
    fun getBookmarksForDocument(documentUri: String): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE fileUri = :fileUri AND page = :page LIMIT 1")
    fun getBookmarkForPageFlow(fileUri: String, page: Int): Flow<Bookmark?>

    @Query("SELECT * FROM bookmarks WHERE fileUri = :documentUri AND page = :pageNumber LIMIT 1")
    suspend fun getBookmarkForPage(documentUri: String, pageNumber: Int): Bookmark?

    @Query("SELECT COUNT(*) FROM bookmarks WHERE fileUri = :fileUri")
    fun getBookmarksCountForFile(fileUri: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)

    @Query("DELETE FROM bookmarks WHERE fileUri = :documentUri AND page = :pageNumber")
    suspend fun deleteBookmarkForPage(documentUri: String, pageNumber: Int)

    @Query("DELETE FROM bookmarks WHERE fileUri = :documentUri")
    suspend fun deleteBookmarksForDocument(documentUri: String)

    @Query("DELETE FROM bookmarks")
    suspend fun clearAllBookmarks()
}
