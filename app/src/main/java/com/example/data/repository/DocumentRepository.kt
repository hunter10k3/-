package com.example.data.repository

import android.net.Uri
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.DocumentTocEntity
import com.example.data.local.entity.ReadingProgressEntity
import com.example.data.local.entity.RecentDocumentEntity
import com.example.data.model.DocumentItem
import com.example.data.model.SearchFilterType
import com.example.data.model.SearchResultItem
import com.example.data.scanner.ScanProgressState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DocumentRepository {
    val allDocuments: StateFlow<List<DocumentItem>>
    val isScanning: StateFlow<Boolean>
    val scanProgress: StateFlow<ScanProgressState>
    val isIndexing: StateFlow<Boolean>
    val indexingProgress: StateFlow<String>
    val indexedDocsCount: Flow<Int>
    val recentDocuments: Flow<List<RecentDocumentEntity>>
    val allBookmarks: Flow<List<BookmarkEntity>>

    fun scanStorageFlow(): Flow<List<DocumentItem>>
    suspend fun scanStorage(): List<DocumentItem>
    suspend fun scanSafFolder(treeUri: Uri): List<DocumentItem>
    suspend fun addPickedDocument(uri: Uri): DocumentItem?
    suspend fun generateSampleLibrary(): List<DocumentItem>

    // Full-Text Indexing & Search
    suspend fun indexAllDocuments()
    suspend fun indexDocument(document: DocumentItem)
    suspend fun search(query: String, filterType: SearchFilterType = SearchFilterType.ALL): List<SearchResultItem>
    fun getTocForDocument(documentUri: String): Flow<List<DocumentTocEntity>>
    suspend fun getTocForDocumentSync(documentUri: String): List<DocumentTocEntity>

    // Chapter & TOC Extraction
    suspend fun extractChapters(uri: Uri, mime: String): List<com.example.data.model.Chapter>
    fun getChaptersFlow(fileUri: String): Flow<List<com.example.data.local.entity.ChapterEntity>>
    suspend fun getChaptersSync(fileUri: String): List<com.example.data.local.entity.ChapterEntity>

    // Content Search (Room FTS4 / Chapters / Filename / OCR)
    fun searchContentFlow(query: String, filters: com.example.data.model.SearchFilters): Flow<List<com.example.data.model.SearchHit>>
    suspend fun searchContent(query: String, filters: com.example.data.model.SearchFilters): Flow<List<com.example.data.model.SearchHit>>

    suspend fun getDocumentByUri(uri: String): DocumentItem?
    suspend fun recordDocumentOpened(document: DocumentItem, currentPage: Int = 1, totalPages: Int = 1)
    suspend fun saveReadingProgress(uri: String, currentPage: Int, totalPages: Int, scrollOffset: Int = 0)
    fun getReadingProgressFlow(uri: String): Flow<ReadingProgressEntity?>
    suspend fun getReadingProgress(uri: String): ReadingProgressEntity?

    fun getBookmarksForDocument(uri: String): Flow<List<BookmarkEntity>>
    suspend fun getBookmarkForPage(uri: String, page: Int): BookmarkEntity?
    suspend fun toggleBookmark(documentUri: String, documentTitle: String, pageNumber: Int, title: String, snippet: String = ""): Boolean
    suspend fun deleteBookmark(id: Long)
    suspend fun removeRecentDocument(uri: String)
    suspend fun clearAllRecent()
}

