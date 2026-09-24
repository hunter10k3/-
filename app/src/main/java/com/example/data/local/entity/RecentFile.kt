package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/**
 * Room entity representing a recently opened document file.
 * Core fields: uri, name, openedAt, type.
 */
@Entity(tableName = "recent_files")
data class RecentFile(
    @PrimaryKey
    val uri: String,
    val name: String,
    val openedAt: Long = System.currentTimeMillis(),
    val type: String = "DOCUMENT",
    val path: String? = null,
    val sizeBytes: Long = 0L,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val progressPercent: Float = 0f
) {
    val title: String get() = name
    val formatName: String get() = type
    val extension: String get() = name.substringAfterLast('.', "")
    val mimeType: String get() = type
    val lastModified: Long get() = openedAt
    val lastReadTimestamp: Long get() = openedAt

    @Ignore
    constructor(
        uri: String,
        title: String,
        path: String? = null,
        extension: String = "",
        formatName: String = "",
        mimeType: String = "",
        sizeBytes: Long = 0L,
        lastModified: Long = System.currentTimeMillis(),
        lastReadTimestamp: Long = System.currentTimeMillis(),
        currentPage: Int = 1,
        totalPages: Int = 1,
        progressPercent: Float = 0f
    ) : this(
        uri = uri,
        name = title,
        openedAt = lastReadTimestamp,
        type = if (formatName.isNotBlank()) formatName else (if (mimeType.isNotBlank()) mimeType else extension),
        path = path,
        sizeBytes = sizeBytes,
        currentPage = currentPage,
        totalPages = totalPages,
        progressPercent = progressPercent
    )
}

typealias RecentDocumentEntity = RecentFile
