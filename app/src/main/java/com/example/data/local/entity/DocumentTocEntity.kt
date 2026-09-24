package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity storing extracted Table of Contents (TOC), outline, headings,
 * and slide titles for fast jumping and structural navigation.
 */
@Entity(
    tableName = "document_toc",
    indices = [
        Index(value = ["documentUri"]),
        Index(value = ["documentUri", "page"])
    ]
)
data class DocumentTocEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentUri: String,
    val title: String,
    val page: Int = 1,
    val level: Int = 1,
    val targetLocation: String = "",
    val charOffset: Int = 0
)
