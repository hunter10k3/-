package com.example.data.reader.chapter

import android.content.Context
import android.net.Uri
import com.example.data.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.charset.StandardCharsets

class TextChapterExtractor(private val context: Context) {

    suspend fun extract(uri: Uri): List<Chapter> = withContext(Dispatchers.IO) {
        val inputStream: InputStream? = if (uri.scheme == "file") {
            java.io.FileInputStream(java.io.File(uri.path ?: ""))
        } else {
            context.contentResolver.openInputStream(uri)
        }

        val text = inputStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
        if (text.isBlank()) return@withContext emptyList()

        val chapters = mutableListOf<Chapter>()
        val lines = text.lines()
        var currentOffset = 0L

        val mdHeadingRegex = Regex("""^(#{1,6})\s+(.+)$""")
        val structuredHeadingRegex = Regex("""^(Chapter|CHAPTER|Section|SECTION|Part|PART|Book|BOOK|Act|ACT|Scene|SCENE)\s+([0-9IVXLCDMivxlcdm]+|[A-Za-z]+)(.*)$""")

        for ((lineIndex, rawLine) in lines.withIndex()) {
            val trimmed = rawLine.trim()
            val lineLength = rawLine.length.toLong() + 1L // including newline

            if (trimmed.isNotEmpty()) {
                val mdMatch = mdHeadingRegex.find(trimmed)
                if (mdMatch != null) {
                    val hashes = mdMatch.groupValues[1]
                    val title = mdMatch.groupValues[2].trim()
                    chapters.add(
                        Chapter(
                            title = title,
                            level = hashes.length,
                            startPage = ((currentOffset / 2000L) + 1).toInt(),
                            startOffset = currentOffset,
                            startTimeMs = null
                        )
                    )
                } else if (structuredHeadingRegex.matches(trimmed)) {
                    val level = when {
                        trimmed.startsWith("Part", ignoreCase = true) || trimmed.startsWith("Book", ignoreCase = true) -> 1
                        trimmed.startsWith("Chapter", ignoreCase = true) || trimmed.startsWith("Act", ignoreCase = true) -> 1
                        else -> 2
                    }
                    chapters.add(
                        Chapter(
                            title = trimmed,
                            level = level,
                            startPage = ((currentOffset / 2000L) + 1).toInt(),
                            startOffset = currentOffset,
                            startTimeMs = null
                        )
                    )
                } else if (isAllCapsHeading(trimmed)) {
                    chapters.add(
                        Chapter(
                            title = trimmed,
                            level = 1,
                            startPage = ((currentOffset / 2000L) + 1).toInt(),
                            startOffset = currentOffset,
                            startTimeMs = null
                        )
                    )
                }
            }

            currentOffset += lineLength
        }

        chapters
    }

    private fun isAllCapsHeading(line: String): Boolean {
        if (line.length !in 3..80) return false
        val lettersOnly = line.filter { it.isLetter() }
        if (lettersOnly.length < 3) return false
        // Must be entirely uppercase for letters
        if (lettersOnly != lettersOnly.uppercase()) return false

        // Filter out lines that look like table rows or separator lines
        if (line.all { it.isUpperCase() || it.isWhitespace() || it == ':' || it == '-' || it == '.' || it == ',' }) {
            // Avoid standalone short acronyms unless formatted like heading
            val words = line.split(Regex("\\s+")).filter { it.isNotBlank() }
            return words.size in 1..10
        }
        return false
    }
}
