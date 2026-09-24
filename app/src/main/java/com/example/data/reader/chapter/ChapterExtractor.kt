package com.example.data.reader.chapter

import android.content.Context
import android.net.Uri
import com.example.data.local.dao.ChapterDao
import com.example.data.local.entity.ChapterEntity
import com.example.data.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Universal Chapter and Table of Contents Extractor for documents.
 */
interface ChapterExtractor {
    /**
     * Extracts chapters for the given document URI and MIME type.
     * Caches results in Room database for fast repeated lookups.
     */
    suspend fun extract(uri: Uri, mime: String): List<Chapter>
}

class DefaultChapterExtractor(
    private val context: Context,
    private val chapterDao: ChapterDao
) : ChapterExtractor {

    private val epubExtractor = EpubChapterExtractor(context)
    private val pdfExtractor = PdfChapterExtractor(context)
    private val pptxExtractor = PptxChapterExtractor(context)
    private val docxExtractor = DocxChapterExtractor(context)
    private val textExtractor = TextChapterExtractor(context)

    override suspend fun extract(uri: Uri, mime: String): List<Chapter> = withContext(Dispatchers.IO) {
        val uriString = uri.toString()

        // 1. Check cached results in Room database
        val cached = chapterDao.getChaptersForFileSync(uriString)
        if (cached.isNotEmpty()) {
            return@withContext cached.map { it.toChapter() }
        }

        // 2. Perform format-specific chapter extraction
        val rawChapters = try {
            when {
                mime.contains("pdf", ignoreCase = true) || uriString.endsWith(".pdf", ignoreCase = true) -> {
                    pdfExtractor.extract(uri)
                }
                mime.contains("epub", ignoreCase = true) || uriString.endsWith(".epub", ignoreCase = true) -> {
                    epubExtractor.extract(uri)
                }
                mime.contains("presentation", ignoreCase = true) ||
                mime.contains("powerpoint", ignoreCase = true) ||
                uriString.endsWith(".pptx", ignoreCase = true) ||
                uriString.endsWith(".ppt", ignoreCase = true) -> {
                    pptxExtractor.extract(uri)
                }
                mime.contains("word", ignoreCase = true) ||
                mime.contains("officedocument.wordprocessingml", ignoreCase = true) ||
                uriString.endsWith(".docx", ignoreCase = true) ||
                uriString.endsWith(".doc", ignoreCase = true) -> {
                    docxExtractor.extract(uri)
                }
                mime.contains("markdown", ignoreCase = true) ||
                mime.contains("text", ignoreCase = true) ||
                uriString.endsWith(".txt", ignoreCase = true) ||
                uriString.endsWith(".md", ignoreCase = true) -> {
                    textExtractor.extract(uri)
                }
                else -> {
                    // Fallback to text extractor
                    textExtractor.extract(uri)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }

        // 3. Cache extracted results in Room
        if (rawChapters.isNotEmpty()) {
            val entities = rawChapters.mapIndexed { index, chapter ->
                val anchor = when {
                    chapter.startOffset != null -> "offset_${chapter.startOffset}"
                    chapter.startPage != null -> "page_${chapter.startPage}"
                    else -> "item_$index"
                }
                ChapterEntity(
                    fileUri = uriString,
                    title = chapter.title,
                    level = chapter.level,
                    anchor = anchor,
                    order = index,
                    startPage = chapter.startPage,
                    startOffset = chapter.startOffset,
                    startTimeMs = chapter.startTimeMs
                )
            }
            chapterDao.insertAll(entities)
        }

        rawChapters
    }
}
