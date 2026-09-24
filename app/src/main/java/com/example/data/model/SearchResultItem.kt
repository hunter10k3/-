package com.example.data.model

enum class SearchMatchType(val displayName: String, val badgeColor: Long) {
    FILENAME("Filename", 0xFF1976D2),
    CONTENT("Full-Text Content", 0xFF2E7D32),
    CHAPTER("Chapter / Outline", 0xFFF57C00),
    OCR("OCR Scanned Text", 0xFF7B1FA2)
}

enum class SearchFilterType(val label: String) {
    ALL("All Matches"),
    CONTENT("Content"),
    CHAPTERS("Chapters / TOC"),
    OCR("OCR Text"),
    FILENAMES("Filenames")
}

data class SearchResultItem(
    val id: String,
    val documentUri: String,
    val documentTitle: String,
    val format: DocumentFormat,
    val matchType: SearchMatchType,
    val pageNumber: Int = 1,
    val chapterTitle: String? = null,
    val charOffset: Int = 0,
    val snippet: String,
    val highlightTerm: String
)
