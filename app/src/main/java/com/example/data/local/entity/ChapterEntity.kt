package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.Chapter

/**
 * Room entity for persisting extracted chapters and table of contents.
 */
@Entity(
    tableName = "chapters",
    indices = [
        Index(value = ["fileUri"]),
        Index(value = ["fileUri", "order"])
    ]
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileUri: String,
    val title: String,
    val level: Int = 1,
    val anchor: String = "",
    @ColumnInfo(name = "order")
    val order: Int = 0,
    val startPage: Int? = null,
    val startOffset: Long? = null,
    val startTimeMs: Long? = null
) {
    fun toChapter(): Chapter = Chapter(
        title = title,
        level = level,
        startPage = startPage,
        startOffset = startOffset,
        startTimeMs = startTimeMs
    )
}
