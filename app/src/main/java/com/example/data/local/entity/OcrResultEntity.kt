package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ocr_results",
    indices = [
        Index(value = ["fileUri"]),
        Index(value = ["fileUri", "pageNumber"])
    ]
)
data class OcrResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileUri: String,
    val pageNumber: Int = 1,
    val text: String,
    val confidence: Float = 1.0f,
    val engine: String = "MLKit",
    val createdAt: Long = System.currentTimeMillis()
)
