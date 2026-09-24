package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "document_texts",
    indices = [
        Index(value = ["fileUri"]),
        Index(value = ["fileUri", "pageNumber"])
    ]
)
data class DocumentTextEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileUri: String,
    val pageNumber: Int = 1,
    val fullText: String,
    val language: String = "en",
    val indexedAt: Long = System.currentTimeMillis(),
    val contentHash: String = ""
)
