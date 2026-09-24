package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.indexer.DocumentIndexer
import com.example.data.local.dao.BookmarkDao
import com.example.data.local.dao.ChapterDao
import com.example.data.local.dao.DocumentContentDao
import com.example.data.local.dao.DocumentSearchDao
import com.example.data.local.dao.DocumentTocDao
import com.example.data.local.dao.OcrDao
import com.example.data.local.dao.ReadingProgressDao
import com.example.data.local.dao.RecentDocumentDao
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.DocumentTocEntity
import com.example.data.local.entity.ReadingProgressEntity
import com.example.data.local.entity.RecentDocumentEntity
import com.example.data.model.Chapter
import com.example.data.model.DocumentFormat
import com.example.data.model.DocumentItem
import com.example.data.model.SearchFilterType
import com.example.data.model.SearchFilters
import com.example.data.model.SearchHit
import com.example.data.model.SearchResultItem
import com.example.data.reader.chapter.ChapterExtractor
import com.example.data.reader.chapter.DefaultChapterExtractor
import com.example.data.scanner.SampleDocumentGenerator
import com.example.data.scanner.ScanProgressState
import com.example.data.scanner.StorageDocumentScanner
import com.example.data.search.DocumentSearchEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DocumentRepositoryImpl(
    private val context: Context,
    private val recentDao: RecentDocumentDao,
    private val bookmarkDao: BookmarkDao,
    private val progressDao: ReadingProgressDao,
    private val contentDao: DocumentContentDao,
    private val tocDao: DocumentTocDao,
    private val chapterDao: ChapterDao,
    private val searchDao: DocumentSearchDao,
    private val ocrDao: OcrDao,
    private val scanner: StorageDocumentScanner,
    private val indexer: DocumentIndexer,
    private val chapterExtractor: ChapterExtractor = DefaultChapterExtractor(context, chapterDao)
) : DocumentRepository {

    private val searchEngine by lazy {
        DocumentSearchEngine(
            repository = this,
            searchDao = searchDao,
            chapterDao = chapterDao,
            ocrDao = ocrDao
        )
    }

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    private val _allDocuments = MutableStateFlow<List<DocumentItem>>(emptyList())
    override val allDocuments: StateFlow<List<DocumentItem>> = _allDocuments.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(ScanProgressState("Ready", 0, isComplete = true))
    override val scanProgress: StateFlow<ScanProgressState> = _scanProgress.asStateFlow()

    private val _isIndexing = MutableStateFlow(false)
    override val isIndexing: StateFlow<Boolean> = _isIndexing.asStateFlow()

    private val _indexingProgress = MutableStateFlow("Ready")
    override val indexingProgress: StateFlow<String> = _indexingProgress.asStateFlow()

    override val indexedDocsCount: Flow<Int> = indexer.indexedDocumentCountFlow

    override val recentDocuments: Flow<List<RecentDocumentEntity>> = recentDao.getRecentDocuments()
    override val allBookmarks: Flow<List<BookmarkEntity>> = bookmarkDao.getAllBookmarks()

    override fun scanStorageFlow(): Flow<List<DocumentItem>> = channelFlow {
        _isScanning.value = true
        try {
            scanner.scanStorageFlow().collect { (progress, docs) ->
                _scanProgress.value = progress
                _allDocuments.value = docs
                send(docs)
            }

            // If empty, generate sample library so user has immediate docs
            if (_allDocuments.value.isEmpty()) {
                _scanProgress.value = ScanProgressState("Generating sample library...", 0)
                SampleDocumentGenerator.generateSampleLibrary(context)
                val sampleDocs = scanner.scanStorageDocuments()
                _allDocuments.value = sampleDocs
                _scanProgress.value = ScanProgressState("Scan complete. Loaded sample documents.", sampleDocs.size, isComplete = true)
                send(sampleDocs)
            }

            // Automatically trigger background indexing for scanned documents
            triggerAutoIndex(_allDocuments.value)
        } catch (e: Exception) {
            e.printStackTrace()
            _scanProgress.value = ScanProgressState("Scanning encountered an issue: ${e.localizedMessage}", _allDocuments.value.size, isComplete = true, error = e.localizedMessage)
            send(_allDocuments.value)
        } finally {
            _isScanning.value = false
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun scanStorage(): List<DocumentItem> = withContext(Dispatchers.IO) {
        _isScanning.value = true
        try {
            scanner.scanStorageFlow().collect { (progress, docs) ->
                _scanProgress.value = progress
                _allDocuments.value = docs
            }
            val docs = if (_allDocuments.value.isEmpty()) {
                SampleDocumentGenerator.generateSampleLibrary(context)
                val sampleDocs = scanner.scanStorageDocuments()
                _allDocuments.value = sampleDocs
                _scanProgress.value = ScanProgressState("Scan complete. Loaded sample documents.", sampleDocs.size, isComplete = true)
                sampleDocs
            } else {
                _allDocuments.value
            }
            triggerAutoIndex(docs)
            docs
        } catch (e: Exception) {
            e.printStackTrace()
            _allDocuments.value
        } finally {
            _isScanning.value = false
        }
    }

    override suspend fun scanSafFolder(treeUri: Uri): List<DocumentItem> = withContext(Dispatchers.IO) {
        _isScanning.value = true
        _scanProgress.value = ScanProgressState("Scanning selected folder...", _allDocuments.value.size)
        try {
            val safDocs = scanner.scanSafFolder(treeUri)
            val currentMap = _allDocuments.value.associateBy { it.uri }.toMutableMap()
            for (doc in safDocs) {
                currentMap[doc.uri] = doc
            }
            val updated = currentMap.values.sortedByDescending { it.lastModified }
            _allDocuments.value = updated
            _scanProgress.value = ScanProgressState("Found ${safDocs.size} documents in folder.", updated.size, isComplete = true)
            triggerAutoIndex(safDocs)
            updated
        } finally {
            _isScanning.value = false
        }
    }

    override suspend fun addPickedDocument(uri: Uri): DocumentItem? = withContext(Dispatchers.IO) {
        val doc = scanner.resolveSafDocument(uri) ?: return@withContext null
        val currentMap = _allDocuments.value.associateBy { it.uri }.toMutableMap()
        currentMap[doc.uri] = doc
        val updated = currentMap.values.sortedByDescending { it.lastModified }
        _allDocuments.value = updated
        repositoryScope.launch {
            indexer.indexDocument(doc)
        }
        doc
    }

    override suspend fun generateSampleLibrary(): List<DocumentItem> = withContext(Dispatchers.IO) {
        _isScanning.value = true
        _scanProgress.value = ScanProgressState("Generating sample documents...", 0)
        try {
            SampleDocumentGenerator.generateSampleLibrary(context)
            val updated = scanner.scanStorageDocuments()
            _allDocuments.value = updated
            _scanProgress.value = ScanProgressState("Generated ${updated.size} sample files.", updated.size, isComplete = true)
            triggerAutoIndex(updated)
            updated
        } finally {
            _isScanning.value = false
        }
    }

    private fun triggerAutoIndex(docs: List<DocumentItem>) {
        repositoryScope.launch {
            if (_isIndexing.value) return@launch
            _isIndexing.value = true
            try {
                indexer.indexAllDocuments(docs) { cur, tot, title ->
                    _indexingProgress.value = "Indexing $cur/$tot: $title"
                }
                _indexingProgress.value = "Library index updated (${docs.size} files)"
            } catch (e: Exception) {
                e.printStackTrace()
                _indexingProgress.value = "Indexing completed with warnings"
            } finally {
                _isIndexing.value = false
            }
        }
    }

    override suspend fun indexAllDocuments() = withContext(Dispatchers.IO) {
        if (_isIndexing.value) return@withContext
        _isIndexing.value = true
        try {
            val docs = _allDocuments.value
            indexer.indexAllDocuments(docs) { cur, tot, title ->
                _indexingProgress.value = "Indexing $cur/$tot: $title"
            }
            _indexingProgress.value = "Library index updated (${docs.size} files)"
        } finally {
            _isIndexing.value = false
        }
    }

    override suspend fun indexDocument(document: DocumentItem) = withContext(Dispatchers.IO) {
        indexer.indexDocument(document)
    }

    override suspend fun search(query: String, filterType: SearchFilterType): List<SearchResultItem> = withContext(Dispatchers.IO) {
        indexer.search(query, _allDocuments.value, filterType)
    }

    override fun getTocForDocument(documentUri: String): Flow<List<DocumentTocEntity>> {
        return tocDao.getTocForDocument(documentUri)
    }

    override suspend fun getTocForDocumentSync(documentUri: String): List<DocumentTocEntity> = withContext(Dispatchers.IO) {
        tocDao.getTocForDocumentSync(documentUri)
    }

    override suspend fun extractChapters(uri: Uri, mime: String): List<Chapter> = withContext(Dispatchers.IO) {
        chapterExtractor.extract(uri, mime)
    }

    override fun getChaptersFlow(fileUri: String): Flow<List<ChapterEntity>> {
        return chapterDao.getChaptersForFile(fileUri)
    }

    override suspend fun getChaptersSync(fileUri: String): List<ChapterEntity> = withContext(Dispatchers.IO) {
        chapterDao.getChaptersForFileSync(fileUri)
    }

    override fun searchContentFlow(query: String, filters: SearchFilters): Flow<List<SearchHit>> {
        return searchEngine.search(query, filters)
    }

    override suspend fun searchContent(query: String, filters: SearchFilters): Flow<List<SearchHit>> {
        return searchEngine.search(query, filters)
    }

    override suspend fun getDocumentByUri(uri: String): DocumentItem? = withContext(Dispatchers.IO) {
        _allDocuments.value.firstOrNull { it.uri == uri } ?: run {
            try {
                scanner.resolveSafDocument(Uri.parse(uri))
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun recordDocumentOpened(document: DocumentItem, currentPage: Int, totalPages: Int) = withContext(Dispatchers.IO) {
        val progress = if (totalPages > 0) (currentPage.toFloat() / totalPages.toFloat()) * 100f else 0f
        val entity = RecentDocumentEntity(
            uri = document.uri,
            title = document.title,
            path = document.path,
            extension = document.extension,
            formatName = document.format.name,
            mimeType = document.mimeType,
            sizeBytes = document.sizeBytes,
            lastModified = document.lastModified,
            lastReadTimestamp = System.currentTimeMillis(),
            currentPage = currentPage,
            totalPages = totalPages,
            progressPercent = progress
        )
        recentDao.insertRecentDocument(entity)
    }

    override suspend fun saveReadingProgress(uri: String, currentPage: Int, totalPages: Int, scrollOffset: Int) = withContext(Dispatchers.IO) {
        val progress = if (totalPages > 0) (currentPage.toFloat() / totalPages.toFloat()) * 100f else 0f
        val entity = ReadingProgressEntity(
            documentUri = uri,
            currentPage = currentPage,
            totalPages = totalPages,
            scrollOffset = scrollOffset,
            progressPercent = progress,
            updatedAt = System.currentTimeMillis()
        )
        progressDao.saveReadingProgress(entity)

        val recent = recentDao.getRecentDocument(uri)
        if (recent != null) {
            recentDao.insertRecentDocument(
                recent.copy(
                    currentPage = currentPage,
                    totalPages = totalPages,
                    progressPercent = progress,
                    openedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override fun getReadingProgressFlow(uri: String): Flow<ReadingProgressEntity?> = progressDao.getReadingProgressFlow(uri)

    override suspend fun getReadingProgress(uri: String): ReadingProgressEntity? = progressDao.getReadingProgress(uri)

    override fun getBookmarksForDocument(uri: String): Flow<List<BookmarkEntity>> = bookmarkDao.getBookmarksForDocument(uri)

    override suspend fun getBookmarkForPage(uri: String, page: Int): BookmarkEntity? = bookmarkDao.getBookmarkForPage(uri, page)

    override suspend fun toggleBookmark(
        documentUri: String,
        documentTitle: String,
        pageNumber: Int,
        title: String,
        snippet: String
    ): Boolean = withContext(Dispatchers.IO) {
        val existing = bookmarkDao.getBookmarkForPage(documentUri, pageNumber)
        if (existing != null) {
            bookmarkDao.deleteBookmarkById(existing.id)
            false
        } else {
            val bookmark = BookmarkEntity(
                documentUri = documentUri,
                documentTitle = documentTitle,
                pageNumber = pageNumber,
                title = title.ifBlank { "Page $pageNumber" },
                snippet = snippet,
                createdAt = System.currentTimeMillis()
            )
            bookmarkDao.insertBookmark(bookmark)
            true
        }
    }

    override suspend fun deleteBookmark(id: Long) = withContext(Dispatchers.IO) {
        bookmarkDao.deleteBookmarkById(id)
    }

    override suspend fun removeRecentDocument(uri: String) = withContext(Dispatchers.IO) {
        recentDao.deleteRecentDocument(uri)
    }

    override suspend fun clearAllRecent() = withContext(Dispatchers.IO) {
        recentDao.clearRecentDocuments()
    }
}
