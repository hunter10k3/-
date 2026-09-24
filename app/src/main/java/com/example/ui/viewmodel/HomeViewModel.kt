package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.RecentDocumentEntity
import com.example.data.model.DocumentFormat
import com.example.data.model.DocumentItem
import com.example.data.model.SearchFilterType
import com.example.data.model.SearchResultItem
import com.example.data.repository.DocumentRepository
import com.example.data.scanner.ScanProgressState
import com.example.util.PermissionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortOption(val label: String) {
    DATE_MODIFIED("Date"),
    NAME("Name"),
    SIZE("Size")
}

enum class ViewMode {
    LIST, GRID
}

enum class HomeBottomTab {
    HOME, SEARCH, RECENT, BOOKMARKS, SETTINGS
}

data class FilterCriteria(
    val format: DocumentFormat?,
    val query: String,
    val searchFilterType: SearchFilterType,
    val sort: SortOption,
    val viewMode: ViewMode
)

private data class RepoState(
    val docs: List<DocumentItem>,
    val isScanning: Boolean,
    val scanProgress: ScanProgressState,
    val recent: List<RecentDocumentEntity>,
    val isIndexing: Boolean,
    val indexingProgress: String,
    val indexedDocsCount: Int
)

private data class PermState(
    val hasStoragePermission: Boolean,
    val permissionDeniedMessage: String?
)

data class HomeUiState(
    val documents: List<DocumentItem> = emptyList(),
    val filteredDocuments: List<DocumentItem> = emptyList(),
    val recentDocuments: List<RecentDocumentEntity> = emptyList(),
    val searchResults: List<SearchResultItem> = emptyList(),
    val isScanning: Boolean = false,
    val scanProgress: ScanProgressState = ScanProgressState("Ready", 0, isComplete = true),
    val isIndexing: Boolean = false,
    val indexingProgress: String = "Ready",
    val indexedDocsCount: Int = 0,
    val isSearching: Boolean = false,
    val selectedFormat: DocumentFormat? = null,
    val searchQuery: String = "",
    val searchFilterType: SearchFilterType = SearchFilterType.ALL,
    val sortOption: SortOption = SortOption.DATE_MODIFIED,
    val viewMode: ViewMode = ViewMode.LIST,
    val currentTab: HomeBottomTab = HomeBottomTab.HOME,
    val totalDocumentCount: Int = 0,
    val formatCounts: Map<DocumentFormat, Int> = emptyMap(),
    val hasStoragePermission: Boolean = false,
    val permissionDeniedMessage: String? = null
)

class HomeViewModel(
    private val repository: DocumentRepository
) : ViewModel() {

    private val _selectedFormat = MutableStateFlow<DocumentFormat?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _searchFilterType = MutableStateFlow(SearchFilterType.ALL)
    private val _searchResults = MutableStateFlow<List<SearchResultItem>>(emptyList())
    private val _isSearching = MutableStateFlow(false)
    private val _sortOption = MutableStateFlow(SortOption.DATE_MODIFIED)
    private val _viewMode = MutableStateFlow(ViewMode.LIST)
    private val _currentTab = MutableStateFlow(HomeBottomTab.HOME)
    private val _hasStoragePermission = MutableStateFlow(false)
    private val _permissionDeniedMessage = MutableStateFlow<String?>(null)

    private var searchJob: Job? = null

    private val filterCriteriaFlow = combine(
        _selectedFormat,
        _searchQuery,
        _searchFilterType,
        _sortOption,
        _viewMode
    ) { format, query, filterType, sort, mode ->
        FilterCriteria(format, query, filterType, sort, mode)
    }

    private val scanStateFlow = combine(
        repository.isScanning,
        repository.scanProgress
    ) { scanning, progress ->
        Pair(scanning, progress)
    }

    private val indexStateFlow = combine(
        repository.isIndexing,
        repository.indexingProgress,
        repository.indexedDocsCount
    ) { indexing, indexProg, indexCount ->
        Triple(indexing, indexProg, indexCount)
    }

    private val repoStateFlow = combine(
        repository.allDocuments,
        repository.recentDocuments,
        scanStateFlow,
        indexStateFlow
    ) { docs, recent, scanState, indexState ->
        RepoState(
            docs = docs,
            isScanning = scanState.first,
            scanProgress = scanState.second,
            recent = recent,
            isIndexing = indexState.first,
            indexingProgress = indexState.second,
            indexedDocsCount = indexState.third
        )
    }

    private val permStateFlow = combine(
        _hasStoragePermission,
        _permissionDeniedMessage
    ) { hasPerm, msg ->
        PermState(hasPerm, msg)
    }

    private val searchStateFlow = combine(
        _searchResults,
        _isSearching
    ) { results, searching ->
        Pair(results, searching)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        repoStateFlow,
        filterCriteriaFlow,
        searchStateFlow,
        _currentTab,
        permStateFlow
    ) { repo, criteria, searchState, tab, perm ->
        val docs = repo.docs
        val formatCounts = mutableMapOf<DocumentFormat, Int>()
        for (f in DocumentFormat.values()) {
            formatCounts[f] = docs.count { it.format == f }
        }

        var filtered = docs

        // Format filter
        if (criteria.format != null) {
            filtered = when {
                criteria.format.isPresentation -> filtered.filter { it.format.isPresentation }
                criteria.format.isWordDoc -> filtered.filter { it.format.isWordDoc }
                else -> filtered.filter { it.format == criteria.format }
            }
        }

        // Search query filter by filename if not in deep search mode
        if (criteria.query.isNotBlank()) {
            val q = criteria.query.trim().lowercase()
            filtered = filtered.filter { it.name.lowercase().contains(q) || it.extension.lowercase().contains(q) }
        }

        // Sorting by Name, Date, Size
        filtered = when (criteria.sort) {
            SortOption.DATE_MODIFIED -> filtered.sortedByDescending { it.lastModified }
            SortOption.NAME -> filtered.sortedBy { it.name.lowercase() }
            SortOption.SIZE -> filtered.sortedByDescending { it.size }
        }

        HomeUiState(
            documents = docs,
            filteredDocuments = filtered,
            recentDocuments = repo.recent,
            searchResults = searchState.first,
            isScanning = repo.isScanning,
            scanProgress = repo.scanProgress,
            isIndexing = repo.isIndexing,
            indexingProgress = repo.indexingProgress,
            indexedDocsCount = repo.indexedDocsCount,
            isSearching = searchState.second,
            selectedFormat = criteria.format,
            searchQuery = criteria.query,
            searchFilterType = criteria.searchFilterType,
            sortOption = criteria.sort,
            viewMode = criteria.viewMode,
            currentTab = tab,
            totalDocumentCount = docs.size,
            formatCounts = formatCounts,
            hasStoragePermission = perm.hasStoragePermission,
            permissionDeniedMessage = perm.permissionDeniedMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isScanning = true)
    )

    init {
        scanStorage()
    }

    fun checkPermission(context: Context) {
        val hasAccess = PermissionManager.hasAnyStorageAccess(context)
        _hasStoragePermission.value = hasAccess
    }

    fun onPermissionDenied() {
        _permissionDeniedMessage.value = "Storage access is required to discover and read your internal PDF, PPT, EPUB, DOC, and TXT files."
    }

    fun onPermissionGranted() {
        _hasStoragePermission.value = true
        _permissionDeniedMessage.value = null
        scanStorage()
    }

    fun clearPermissionError() {
        _permissionDeniedMessage.value = null
    }

    fun scanStorage() {
        viewModelScope.launch {
            repository.scanStorage()
        }
    }

    fun onSafFolderSelected(folderTreeUri: Uri) {
        viewModelScope.launch {
            repository.scanSafFolder(folderTreeUri)
        }
    }

    fun onDocumentPicked(docUri: Uri, onOpened: (DocumentItem) -> Unit) {
        viewModelScope.launch {
            val doc = repository.addPickedDocument(docUri)
            if (doc != null) {
                onOpened(doc)
            }
        }
    }

    fun generateSampleLibrary() {
        viewModelScope.launch {
            repository.generateSampleLibrary()
        }
    }

    fun triggerReindex() {
        viewModelScope.launch {
            repository.indexAllDocuments()
        }
    }

    fun onFormatFilterSelected(format: DocumentFormat?) {
        _selectedFormat.value = if (_selectedFormat.value == format) null else format
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        performSearch(query, _searchFilterType.value)
    }

    fun onSearchFilterChanged(filterType: SearchFilterType) {
        _searchFilterType.value = filterType
        performSearch(_searchQuery.value, filterType)
    }

    private fun performSearch(query: String, filterType: SearchFilterType) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(200) // Debounce typing
            try {
                val results = repository.search(trimmed, filterType)
                _searchResults.value = results
            } catch (e: Exception) {
                e.printStackTrace()
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun onSortOptionChanged(option: SortOption) {
        _sortOption.value = option
    }

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
    }

    fun setBottomTab(tab: HomeBottomTab) {
        _currentTab.value = tab
    }

    fun removeRecentDocument(uri: String) {
        viewModelScope.launch {
            repository.removeRecentDocument(uri)
        }
    }

    fun clearAllRecent() {
        viewModelScope.launch {
            repository.clearAllRecent()
        }
    }
}
