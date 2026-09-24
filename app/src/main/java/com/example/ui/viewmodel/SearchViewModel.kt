package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.indexer.IndexingManager
import com.example.data.indexer.IndexingProgress
import com.example.data.model.DocumentFormat
import com.example.data.model.MatchType
import com.example.data.model.SearchFilters
import com.example.data.model.SearchHit
import com.example.data.repository.DocumentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val selectedMatchType: MatchType? = null, // null = All
    val selectedFormat: DocumentFormat? = null,
    val isLoading: Boolean = false,
    val searchHits: List<SearchHit> = emptyList(),
    val indexingProgress: IndexingProgress = IndexingProgress()
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val repository: DocumentRepository,
    private val indexingManager: IndexingManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    private val filterFlow = MutableStateFlow(SearchFilters())

    init {
        // Observe indexing progress
        viewModelScope.launch {
            indexingManager.progress.collectLatest { progress ->
                _uiState.update { it.copy(indexingProgress = progress) }
            }
        }

        // Debounced reactive search (300ms)
        viewModelScope.launch {
            combine(
                queryFlow.debounce(300).distinctUntilChanged(),
                filterFlow
            ) { query, filters ->
                Pair(query, filters)
            }.flatMapLatest { (query, filters) ->
                if (query.isBlank()) {
                    _uiState.update { it.copy(isLoading = false, searchHits = emptyList()) }
                    flowOf(emptyList())
                } else {
                    _uiState.update { it.copy(isLoading = true) }
                    repository.searchContentFlow(query, filters)
                }
            }.collectLatest { hits ->
                _uiState.update { it.copy(isLoading = false, searchHits = hits) }
            }
        }
    }

    fun onQueryChanged(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        queryFlow.value = newQuery
    }

    fun onMatchTypeFilterSelected(matchType: MatchType?) {
        _uiState.update { it.copy(selectedMatchType = matchType) }
        val currentFormats = _uiState.value.selectedFormat?.let { setOf(it) } ?: emptySet()
        val types = matchType?.let { setOf(it) } ?: MatchType.entries.toSet()
        filterFlow.value = SearchFilters(matchTypes = types, formats = currentFormats)
    }

    fun onFormatFilterSelected(format: DocumentFormat?) {
        _uiState.update { it.copy(selectedFormat = format) }
        val currentTypes = _uiState.value.selectedMatchType?.let { setOf(it) } ?: MatchType.entries.toSet()
        val formats = format?.let { setOf(it) } ?: emptySet()
        filterFlow.value = SearchFilters(matchTypes = currentTypes, formats = formats)
    }

    fun clearQuery() {
        onQueryChanged("")
    }

    fun triggerReindex(force: Boolean = true) {
        indexingManager.triggerIndexing(force = force)
    }
}
