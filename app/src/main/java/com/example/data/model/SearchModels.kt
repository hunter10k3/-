package com.example.data.model

import android.net.Uri

enum class MatchType {
    FILENAME,
    CONTENT,
    CHAPTER,
    OCR
}

data class SearchLocation(
    val page: Int? = null,
    val chapterIndex: Int? = null,
    val charOffset: Long? = null,
    val label: String = ""
)

data class SearchHit(
    val fileUri: Uri,
    val fileName: String,
    val matchType: MatchType,
    val snippet: String,
    val location: SearchLocation,
    val score: Float
)

data class SearchFilters(
    val matchTypes: Set<MatchType> = MatchType.entries.toSet(),
    val formats: Set<DocumentFormat> = emptySet()
)
