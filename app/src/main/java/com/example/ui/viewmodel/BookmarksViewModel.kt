package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.BookmarkEntity
import com.example.data.repository.DocumentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BookmarksUiState(
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val filteredBookmarks: List<BookmarkEntity> = emptyList(),
    val searchQuery: String = ""
)

class BookmarksViewModel(
    private val repository: DocumentRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<BookmarksUiState> = combine(
        repository.allBookmarks,
        _searchQuery
    ) { bookmarks, query ->
        val filtered = if (query.isBlank()) {
            bookmarks
        } else {
            val q = query.trim().lowercase()
            bookmarks.filter {
                it.documentTitle.lowercase().contains(q) ||
                it.title.lowercase().contains(q) ||
                it.snippet.lowercase().contains(q)
            }
        }
        BookmarksUiState(
            bookmarks = bookmarks,
            filteredBookmarks = filtered,
            searchQuery = query
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BookmarksUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            repository.deleteBookmark(id)
        }
    }
}
