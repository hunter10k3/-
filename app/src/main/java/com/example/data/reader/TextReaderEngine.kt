package com.example.data.reader

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.charset.StandardCharsets

data class TextSearchResult(
    val lineIndex: Int,
    val text: String,
    val matchStart: Int,
    val matchLength: Int
)

data class TextDocument(
    val title: String,
    val fullText: String,
    val lines: List<String>,
    val wordCount: Int,
    val charCount: Int,
    val estimatedMinutes: Int
)

class TextReaderEngine(private val context: Context) {

    suspend fun readText(uri: Uri, fileName: String): TextDocument = withContext(Dispatchers.IO) {
        val inputStream: InputStream? = if (uri.scheme == "file") {
            java.io.FileInputStream(java.io.File(uri.path ?: ""))
        } else {
            context.contentResolver.openInputStream(uri)
        }

        val text = inputStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
        val lines = text.lines()
        val wordCount = text.split(Regex("\\s+")).count { it.isNotBlank() }
        val charCount = text.length
        val estimatedMinutes = (wordCount / 200).coerceAtLeast(1)

        TextDocument(
            title = fileName.substringBeforeLast('.'),
            fullText = text,
            lines = lines,
            wordCount = wordCount,
            charCount = charCount,
            estimatedMinutes = estimatedMinutes
        )
    }

    fun search(text: String, query: String): List<TextSearchResult> {
        if (query.isBlank()) return emptyList()
        val results = mutableListOf<TextSearchResult>()
        val lines = text.lines()
        for ((lineIdx, line) in lines.withIndex()) {
            var index = line.indexOf(query, ignoreCase = true)
            while (index >= 0) {
                results.add(
                    TextSearchResult(
                        lineIndex = lineIdx,
                        text = line.trim(),
                        matchStart = index,
                        matchLength = query.length
                    )
                )
                index = line.indexOf(query, index + query.length, ignoreCase = true)
            }
        }
        return results
    }
}
