package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/**
 * Room entity representing a user-saved bookmark within a document.
 * Core fields: fileUri, page/chapter, note, createdAt.
 */
@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileUri: String,
    val page: Int = 1,
    val chapter: String? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val documentTitle: String = ""
) {
    val documentUri: String get() = fileUri
    val pageNumber: Int get() = page
    val title: String get() = if (note.isNotBlank()) note else (chapter ?: "Page $page")
    val snippet: String get() = note

    @Ignore
    constructor(
        id: Long = 0,
        documentUri: String,
        documentTitle: String = "",
        pageNumber: Int = 1,
        title: String = "",
        snippet: String = "",
        createdAt: Long = System.currentTimeMillis()
    ) : this(
        id = id,
        fileUri = documentUri,
        page = pageNumber,
        chapter = title,
        note = snippet,
        createdAt = createdAt,
        documentTitle = documentTitle
    )
}

typealias BookmarkEntity = Bookmark
