package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.DocumentTocEntity
import com.example.data.model.Chapter
import com.example.data.model.DocumentFormat
import com.example.data.model.DocumentItem
import com.example.data.model.EpubRenderMode
import com.example.data.model.PdfScrollMode
import com.example.data.model.ReaderFontFamily
import com.example.data.model.ReaderLineSpacing
import com.example.data.model.ReadingTheme
import com.example.data.reader.DocxDocument
import com.example.data.reader.DocxReaderEngine
import com.example.data.reader.EpubBook
import com.example.data.reader.EpubReaderEngine
import com.example.data.reader.PdfReaderEngine
import com.example.data.reader.PptxPresentation
import com.example.data.reader.PptxReaderEngine
import com.example.data.reader.TextDocument
import com.example.data.reader.TextReaderEngine
import com.example.data.reader.TextSearchResult
import com.example.data.repository.DocumentRepository
import com.example.LairikApplication
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ReaderUiState(
    val document: DocumentItem? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val progressPercent: Float = 0f,
    val readingTheme: ReadingTheme = ReadingTheme.SEPIA,
    val fontSizeSp: Float = 16f,
    val readerFontFamily: ReaderFontFamily = ReaderFontFamily.SYSTEM,
    val lineSpacing: ReaderLineSpacing = ReaderLineSpacing.NORMAL,
    val isBookmarked: Boolean = false,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val tocItems: List<DocumentTocEntity> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val targetOffset: Int = 0,
    val highlightQuery: String? = null,
    val showControls: Boolean = true,
    val showThemeSheet: Boolean = false,
    val showBookmarksSheet: Boolean = false,
    val showTocSheet: Boolean = false,
    val showJumpToPageDialog: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val searchResults: List<TextSearchResult> = emptyList(),
    val currentSearchIndex: Int = 0,
    // PDF-specific settings
    val currentPdfBitmap: Bitmap? = null,
    val pdfScrollMode: PdfScrollMode = PdfScrollMode.HORIZONTAL,
    val isPdfNightMode: Boolean = false,
    // EPUB-specific settings
    val epubBook: EpubBook? = null,
    val currentEpubChapterIndex: Int = 0,
    val epubRenderMode: EpubRenderMode = EpubRenderMode.COMPOSE_TEXT,
    // Other formats
    val docxDocument: DocxDocument? = null,
    val pptxPresentation: PptxPresentation? = null,
    val textDocument: TextDocument? = null,
    // OCR fields
    val ocrText: String? = null,
    val isOcrOverlayVisible: Boolean = false,
    val isOcrProcessing: Boolean = false,
    val ocrConfidence: Float? = null
)

class ReaderViewModel(
    private val context: Context,
    private val repository: DocumentRepository,
    private val documentUriString: String,
    private val initialTargetPage: Int? = null,
    private val initialTargetOffset: Int? = null,
    private val highlightSearchQuery: String? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ReaderUiState(
            targetOffset = initialTargetOffset ?: 0,
            highlightQuery = highlightSearchQuery?.takeIf { it.isNotBlank() }
        )
    )
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var pdfEngine: PdfReaderEngine? = null
    private val epubEngine = EpubReaderEngine(context)
    private val docxEngine = DocxReaderEngine(context)
    private val pptxEngine = PptxReaderEngine(context)
    private val textEngine = TextReaderEngine(context)

    private var renderJob: Job? = null

    init {
        loadDocument()
        observeBookmarks()
        observeToc()
        observeChapters()
    }

    private fun observeToc() {
        viewModelScope.launch {
            repository.getTocForDocument(documentUriString).collectLatest { tocs ->
                _uiState.value = _uiState.value.copy(tocItems = tocs)
            }
        }
    }

    private fun observeChapters() {
        viewModelScope.launch {
            repository.getChaptersFlow(documentUriString).collectLatest { entities ->
                if (entities.isNotEmpty()) {
                    val chapters = entities.map { it.toChapter() }
                    _uiState.value = _uiState.value.copy(chapters = chapters)
                }
            }
        }
    }

    private fun observeBookmarks() {
        viewModelScope.launch {
            repository.getBookmarksForDocument(documentUriString).collectLatest { bookmarks ->
                val currentPage = _uiState.value.currentPage
                val isBookmarked = bookmarks.any { it.pageNumber == currentPage }
                _uiState.value = _uiState.value.copy(
                    bookmarks = bookmarks,
                    isBookmarked = isBookmarked
                )
            }
        }
    }

    private fun loadDocument() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val uri = Uri.parse(documentUriString)
            val doc = repository.getDocumentByUri(documentUriString)

            if (doc == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Unable to locate document at $documentUriString"
                )
                return@launch
            }

            // Check existing reading progress or target jump page
            val existingProgress = repository.getReadingProgress(documentUriString)
            val initialPage = initialTargetPage?.takeIf { it > 0 } ?: existingProgress?.currentPage ?: 1

            // Load user preferences from DataStore
            var themeToApply = _uiState.value.readingTheme
            var fontSizeToApply = _uiState.value.fontSizeSp
            try {
                val app = context.applicationContext as? LairikApplication
                val settings = app?.container?.appSettingsDataStore
                if (settings != null) {
                    val appSettings = settings.settingsFlow.first()
                    themeToApply = appSettings.defaultReaderTheme
                    fontSizeToApply = appSettings.defaultFontSize
                }
            } catch (e: Exception) {
                // Keep default
            }

            _uiState.value = _uiState.value.copy(
                document = doc,
                readingTheme = themeToApply,
                fontSizeSp = fontSizeToApply
            )

            try {
                when (doc.format) {
                    DocumentFormat.PDF -> loadPdf(uri, initialPage)
                    DocumentFormat.EPUB -> loadEpub(uri, initialPage)
                    DocumentFormat.DOC, DocumentFormat.DOCX -> loadDocx(uri, doc.title)
                    DocumentFormat.PPT, DocumentFormat.PPTX -> loadPptx(uri, doc.title, initialPage)
                    DocumentFormat.TXT -> loadTxt(uri, doc.title)
                }

                repository.recordDocumentOpened(
                    document = doc,
                    currentPage = _uiState.value.currentPage,
                    totalPages = _uiState.value.totalPages
                )

                // Background chapter extraction using ChapterExtractor
                viewModelScope.launch {
                    try {
                        val extracted = repository.extractChapters(uri, doc.mimeType)
                        if (extracted.isNotEmpty()) {
                            _uiState.value = _uiState.value.copy(chapters = extracted)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error opening document: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    private suspend fun loadPdf(uri: Uri, initialPage: Int) {
        val engine = PdfReaderEngine(context, uri)
        val success = engine.initialize()
        if (success && engine.pageCount > 0) {
            pdfEngine = engine
            val total = engine.pageCount
            val page = initialPage.coerceIn(1, total)
            val bitmap = engine.renderPage(page - 1)

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                currentPage = page,
                totalPages = total,
                progressPercent = (page.toFloat() / total.toFloat()) * 100f,
                currentPdfBitmap = bitmap
            )
            checkBookmarkState(page)
        } else {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Failed to render PDF document. The file may be password protected or corrupted."
            )
        }
    }

    private suspend fun loadEpub(uri: Uri, initialPage: Int) {
        val book = epubEngine.parseEpub(uri)
        val totalChapters = book.chapters.size.coerceAtLeast(1)
        val chapterIdx = (initialPage - 1).coerceIn(0, totalChapters - 1)

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            epubBook = book,
            currentEpubChapterIndex = chapterIdx,
            currentPage = chapterIdx + 1,
            totalPages = totalChapters,
            progressPercent = ((chapterIdx + 1).toFloat() / totalChapters.toFloat()) * 100f
        )
        checkBookmarkState(chapterIdx + 1)
    }

    private suspend fun loadDocx(uri: Uri, title: String) {
        val docx = docxEngine.parseDocument(uri, title)
        val totalPages = (docx.paragraphs.size / 10).coerceAtLeast(1)

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            docxDocument = docx,
            currentPage = 1,
            totalPages = totalPages,
            progressPercent = 100f
        )
        checkBookmarkState(1)
    }

    private suspend fun loadPptx(uri: Uri, title: String, initialPage: Int) {
        val presentation = pptxEngine.parsePresentation(uri, title)
        val totalSlides = presentation.slides.size.coerceAtLeast(1)
        val slideIdx = (initialPage - 1).coerceIn(0, totalSlides - 1)

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            pptxPresentation = presentation,
            currentPage = slideIdx + 1,
            totalPages = totalSlides,
            progressPercent = ((slideIdx + 1).toFloat() / totalSlides.toFloat()) * 100f
        )
        checkBookmarkState(slideIdx + 1)
    }

    private suspend fun loadTxt(uri: Uri, title: String) {
        val textDoc = textEngine.readText(uri, title)
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            textDocument = textDoc,
            currentPage = 1,
            totalPages = 1,
            progressPercent = 100f
        )
        checkBookmarkState(1)
    }

    suspend fun getPageBitmap(pageIndex: Int, targetWidth: Int = 1200): Bitmap? {
        return pdfEngine?.renderPage(pageIndex, targetWidth)
    }

    fun isPageCached(pageIndex: Int): Boolean {
        return pdfEngine?.isPageCached(pageIndex) == true
    }

    fun goToPage(page: Int) {
        val state = _uiState.value
        val clampedPage = page.coerceIn(1, state.totalPages)
        val doc = state.document ?: return

        when (doc.format) {
            DocumentFormat.PDF -> {
                renderJob?.cancel()
                renderJob = viewModelScope.launch {
                    val bitmap = pdfEngine?.renderPage(clampedPage - 1)
                    _uiState.value = _uiState.value.copy(
                        currentPage = clampedPage,
                        currentPdfBitmap = bitmap,
                        progressPercent = (clampedPage.toFloat() / state.totalPages.toFloat()) * 100f
                    )
                    checkBookmarkState(clampedPage)
                    saveCurrentProgress(clampedPage)
                    loadOcrForCurrentPage()
                }
            }
            DocumentFormat.EPUB -> {
                val chapterIdx = clampedPage - 1
                _uiState.value = _uiState.value.copy(
                    currentPage = clampedPage,
                    currentEpubChapterIndex = chapterIdx,
                    progressPercent = (clampedPage.toFloat() / state.totalPages.toFloat()) * 100f
                )
                checkBookmarkState(clampedPage)
                saveCurrentProgress(clampedPage)
            }
            DocumentFormat.PPT, DocumentFormat.PPTX -> {
                _uiState.value = _uiState.value.copy(
                    currentPage = clampedPage,
                    progressPercent = (clampedPage.toFloat() / state.totalPages.toFloat()) * 100f
                )
                checkBookmarkState(clampedPage)
                saveCurrentProgress(clampedPage)
            }
            else -> {
                _uiState.value = _uiState.value.copy(currentPage = clampedPage)
                saveCurrentProgress(clampedPage)
            }
        }
    }

    fun onScrolledToPage(pageIndex: Int) {
        val pageNumber = pageIndex + 1
        if (pageNumber != _uiState.value.currentPage) {
            val total = _uiState.value.totalPages
            _uiState.value = _uiState.value.copy(
                currentPage = pageNumber,
                progressPercent = (pageNumber.toFloat() / total.toFloat()) * 100f
            )
            checkBookmarkState(pageNumber)
            saveCurrentProgress(pageNumber)
        }
    }

    fun nextPage() {
        goToPage(_uiState.value.currentPage + 1)
    }

    fun prevPage() {
        goToPage(_uiState.value.currentPage - 1)
    }

    fun togglePdfScrollMode() {
        val current = _uiState.value.pdfScrollMode
        val next = if (current == PdfScrollMode.HORIZONTAL) PdfScrollMode.VERTICAL else PdfScrollMode.HORIZONTAL
        _uiState.value = _uiState.value.copy(pdfScrollMode = next)
    }

    fun setPdfScrollMode(mode: PdfScrollMode) {
        _uiState.value = _uiState.value.copy(pdfScrollMode = mode)
    }

    fun togglePdfNightMode() {
        _uiState.value = _uiState.value.copy(isPdfNightMode = !_uiState.value.isPdfNightMode)
    }

    fun setEpubRenderMode(mode: EpubRenderMode) {
        _uiState.value = _uiState.value.copy(epubRenderMode = mode)
    }

    fun setReaderFontFamily(family: ReaderFontFamily) {
        _uiState.value = _uiState.value.copy(readerFontFamily = family)
    }

    fun setLineSpacing(spacing: ReaderLineSpacing) {
        _uiState.value = _uiState.value.copy(lineSpacing = spacing)
    }

    fun setShowJumpToPageDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showJumpToPageDialog = show)
    }

    fun toggleBookmark() {
        val state = _uiState.value
        val doc = state.document ?: return
        val page = state.currentPage
        val snippet = when (doc.format) {
            DocumentFormat.PDF -> "PDF Page $page"
            DocumentFormat.EPUB -> state.epubBook?.chapters?.getOrNull(page - 1)?.title ?: "Chapter $page"
            DocumentFormat.PPT, DocumentFormat.PPTX -> state.pptxPresentation?.slides?.getOrNull(page - 1)?.title ?: "Slide $page"
            DocumentFormat.DOC, DocumentFormat.DOCX -> state.docxDocument?.paragraphs?.firstOrNull()?.text?.take(50) ?: "Document mark"
            DocumentFormat.TXT -> state.textDocument?.lines?.firstOrNull { it.isNotBlank() }?.take(50) ?: "Text mark"
        }

        viewModelScope.launch {
            val title = when (doc.format) {
                DocumentFormat.EPUB -> state.epubBook?.chapters?.getOrNull(page - 1)?.title ?: "Chapter $page"
                DocumentFormat.PPT, DocumentFormat.PPTX -> state.pptxPresentation?.slides?.getOrNull(page - 1)?.title ?: "Slide $page"
                else -> "Page $page"
            }
            val added = repository.toggleBookmark(
                documentUri = doc.uri,
                documentTitle = doc.title,
                pageNumber = page,
                title = title,
                snippet = snippet
            )
            _uiState.value = _uiState.value.copy(isBookmarked = added)
        }
    }

    private fun checkBookmarkState(page: Int) {
        val isBookmarked = _uiState.value.bookmarks.any { it.pageNumber == page }
        _uiState.value = _uiState.value.copy(isBookmarked = isBookmarked)
    }

    private fun saveCurrentProgress(page: Int) {
        val state = _uiState.value
        val doc = state.document ?: return
        viewModelScope.launch {
            repository.saveReadingProgress(
                uri = doc.uri,
                currentPage = page,
                totalPages = state.totalPages
            )
        }
    }

    fun setReadingTheme(theme: ReadingTheme) {
        _uiState.value = _uiState.value.copy(readingTheme = theme)
    }

    fun setFontSize(size: Float) {
        _uiState.value = _uiState.value.copy(fontSizeSp = size.coerceIn(12f, 32f))
    }

    fun toggleControls() {
        _uiState.value = _uiState.value.copy(showControls = !_uiState.value.showControls)
    }

    fun setShowThemeSheet(show: Boolean) {
        _uiState.value = _uiState.value.copy(showThemeSheet = show)
    }

    fun setShowBookmarksSheet(show: Boolean) {
        _uiState.value = _uiState.value.copy(showBookmarksSheet = show)
    }

    fun setShowTocSheet(show: Boolean) {
        _uiState.value = _uiState.value.copy(showTocSheet = show)
    }

    fun jumpToTocItem(item: DocumentTocEntity) {
        _uiState.value = _uiState.value.copy(showTocSheet = false)
        goToPage(item.page)
    }

    fun jumpToChapter(chapter: Chapter) {
        _uiState.value = _uiState.value.copy(showTocSheet = false)
        when {
            chapter.startPage != null -> {
                goToPage(chapter.startPage)
            }
            chapter.startOffset != null -> {
                _uiState.value = _uiState.value.copy(targetOffset = chapter.startOffset.toInt())
                // For text or docx, estimate page if page-based
                val estimatedPage = ((chapter.startOffset / 2000L) + 1).toInt().coerceIn(1, _uiState.value.totalPages)
                goToPage(estimatedPage)
            }
            else -> {
                // fallback
            }
        }
    }

    fun setSearchQuery(query: String) {
        val txt = _uiState.value.textDocument?.fullText ?: ""
        val results = textEngine.search(txt, query)
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            searchResults = results,
            currentSearchIndex = 0
        )
    }

    fun toggleSearch() {
        val current = _uiState.value.isSearchActive
        _uiState.value = _uiState.value.copy(
            isSearchActive = !current,
            searchQuery = if (current) "" else _uiState.value.searchQuery,
            searchResults = if (current) emptyList() else _uiState.value.searchResults
        )
    }

    fun toggleOcrOverlay() {
        val current = _uiState.value.isOcrOverlayVisible
        _uiState.value = _uiState.value.copy(isOcrOverlayVisible = !current)
        if (!current && _uiState.value.ocrText == null) {
            loadOcrForCurrentPage()
        }
    }

    fun loadOcrForCurrentPage() {
        val app = context.applicationContext as? LairikApplication ?: return
        val ocrDao = app.container.database.ocrDao()
        val page = _uiState.value.currentPage
        viewModelScope.launch {
            try {
                val ocr = ocrDao.getOcrForPage(documentUriString, page)
                if (ocr != null) {
                    _uiState.value = _uiState.value.copy(
                        ocrText = ocr.text,
                        ocrConfidence = ocr.confidence
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        ocrText = null,
                        ocrConfidence = null
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun runOcrOnCurrentPage() {
        val app = context.applicationContext as? LairikApplication ?: return
        val ocrEngine = app.container.ocrEngine
        val ocrDao = app.container.database.ocrDao()
        val searchDao = app.container.database.documentSearchDao()
        val currentPage = _uiState.value.currentPage
        val bitmap = _uiState.value.currentPdfBitmap ?: return

        _uiState.value = _uiState.value.copy(isOcrProcessing = true)
        viewModelScope.launch {
            try {
                val text = ocrEngine.recognizeTextFromBitmap(bitmap)
                if (text.isNotBlank()) {
                    val result = com.example.data.local.entity.OcrResultEntity(
                        fileUri = documentUriString,
                        pageNumber = currentPage,
                        text = text,
                        confidence = 0.90f,
                        engine = "MLKit Text v2"
                    )
                    ocrDao.insertOcrResult(result)
                    searchDao.insertDocumentText(
                        com.example.data.local.entity.DocumentTextEntity(
                            fileUri = documentUriString,
                            pageNumber = currentPage,
                            fullText = text,
                            contentHash = "ocr_${currentPage}_${text.hashCode()}"
                        )
                    )
                    searchDao.insertFtsDocument(
                        com.example.data.local.entity.FtsDocument(
                            fileUri = documentUriString,
                            pageNumber = currentPage,
                            title = "[OCR] ${_uiState.value.document?.title ?: "Document"} (Page $currentPage)",
                            body = text
                        )
                    )
                    _uiState.value = _uiState.value.copy(
                        ocrText = text,
                        ocrConfidence = 0.90f,
                        isOcrOverlayVisible = true,
                        isOcrProcessing = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isOcrProcessing = false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isOcrProcessing = false)
            }
        }
    }

    fun runOcrOnEntireDocument() {
        val app = context.applicationContext as? LairikApplication ?: return
        val doc = _uiState.value.document ?: return
        app.container.ocrManager.startOcrForDocument(doc.uri, doc.title, forceAll = true)
    }

    override fun onCleared() {
        super.onCleared()
        pdfEngine?.close()
        pdfEngine = null
    }
}
