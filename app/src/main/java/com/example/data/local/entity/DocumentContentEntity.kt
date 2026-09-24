package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity storing full-text indexed content chunks of documents.
 * Enables fast search across document text, OCR output, and chapter titles.
 */
@Entity(
    tableName = "document_content",
    indices = [
        Index(value = ["documentUri"]),
        Index(value = ["format"]),
        Index(value = ["sourceType"]),
        Index(value = ["documentUri", "page"])
    ]
)
data class DocumentContentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentUri: String,
    val documentTitle: String,
    val format: String,
    val page: Int = 1,
    val chapterTitle: String? = null,
    val charOffset: Int = 0,
    val text: String,
    val sourceType: String = SOURCE_TEXT,
    val indexedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SOURCE_TEXT = "TEXT"
        const val SOURCE_OCR = "OCR"
        const val SOURCE_CHAPTER = "CHAPTER"
        const val SOURCE_METADATA = "METADATA"
    }
}
