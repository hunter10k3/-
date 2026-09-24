package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/**
 * Room entity representing reading progress for a document.
 * Core fields: fileUri, page/position, percentage, updatedAt.
 */
@Entity(tableName = "reading_progress")
data class ReadingProgress(
    @PrimaryKey
    val fileUri: String,
    val page: Int = 1,
    val position: Int = 0,
    val percentage: Float = 0f,
    val updatedAt: Long = System.currentTimeMillis(),
    val totalPages: Int = 1
) {
    val documentUri: String get() = fileUri
    val currentPage: Int get() = page
    val scrollOffset: Int get() = position
    val progressPercent: Float get() = percentage

    @Ignore
    constructor(
        documentUri: String,
        currentPage: Int = 1,
        totalPages: Int = 1,
        scrollOffset: Int = 0,
        progressPercent: Float = 0f,
        updatedAt: Long = System.currentTimeMillis()
    ) : this(
        fileUri = documentUri,
        page = currentPage,
        position = scrollOffset,
        percentage = progressPercent,
        updatedAt = updatedAt,
        totalPages = totalPages
    )
}

typealias ReadingProgressEntity = ReadingProgress
