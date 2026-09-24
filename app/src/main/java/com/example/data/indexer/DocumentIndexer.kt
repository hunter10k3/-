package com.example.data.indexer

import android.content.Context
import android.net.Uri
import com.example.data.local.dao.DocumentContentDao
import com.example.data.local.dao.DocumentTocDao
import com.example.data.local.entity.DocumentContentEntity
import com.example.data.local.entity.DocumentTocEntity
import com.example.data.model.DocumentFormat
import com.example.data.model.DocumentItem
import com.example.data.model.SearchFilterType
import com.example.data.model.SearchMatchType
import com.example.data.model.SearchResultItem
import com.example.data.ocr.OcrEngine
import com.example.data.reader.DocxParagraphStyle
import com.example.data.reader.DocxReaderEngine
import com.example.data.reader.EpubReaderEngine
import com.example.data.reader.PdfContentExtractor
import com.example.data.reader.PptxReaderEngine
import com.example.data.reader.TextReaderEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class DocumentIndexer(
    private val context: Context,
    private val contentDao: DocumentContentDao,
    private val tocDao: DocumentTocDao,
    private val ocrEngine: OcrEngine
) {

    private val pdfExtractor = PdfContentExtractor(context, ocrEngine)
    private val epubEngine = EpubReaderEngine(context)
    private val docxEngine = DocxReaderEngine(context)
    private val pptxEngine = PptxReaderEngine(context)
    private val textEngine = TextReaderEngine(context)

    val totalIndexedChunksFlow: Flow<Int> = contentDao.getTotalIndexedChunksFlow()
    val indexedDocumentCountFlow: Flow<Int> = contentDao.getIndexedDocumentCountFlow()

    suspend fun isDocumentIndexed(uri: String): Boolean = withContext(Dispatchers.IO) {
        contentDao.getIndexedChunkCount(uri) > 0
    }

    suspend fun indexDocument(document: DocumentItem) = withContext(Dispatchers.IO) {
        val uri = Uri.parse(document.uri)
        val contentList = mutableListOf<DocumentContentEntity>()
        val tocList = mutableListOf<DocumentTocEntity>()

        try {
            when (document.format) {
                DocumentFormat.PDF -> {
                    val result = pdfExtractor.extract(uri, document.title)
                    for (page in result.pages) {
                        contentList.add(
                            DocumentContentEntity(
                                documentUri = document.uri,
                                documentTitle = document.title,
                                format = document.format.name,
                                page = page.pageNumber,
                                text = page.text,
                                sourceType = if (page.isOcr) DocumentContentEntity.SOURCE_OCR else DocumentContentEntity.SOURCE_TEXT
                            )
                        )
                    }
                    tocList.addAll(result.tocItems)
                }

                DocumentFormat.EPUB -> {
                    val book = epubEngine.parseEpub(uri)
                    for ((index, chapter) in book.chapters.withIndex()) {
                        val pageNum = index + 1
                        val fullChapterText = chapter.paragraphs.joinToString("\n\n")

                        contentList.add(
                            DocumentContentEntity(
                                documentUri = document.uri,
                                documentTitle = document.title,
                                format = document.format.name,
                                page = pageNum,
                                chapterTitle = chapter.title,
                                text = fullChapterText.ifBlank { chapter.title },
                                sourceType = DocumentContentEntity.SOURCE_TEXT
                            )
                        )

                        tocList.add(
                            DocumentTocEntity(
                                documentUri = document.uri,
                                title = chapter.title,
                                page = pageNum,
                                level = 1
                            )
                        )
                    }
                }

                DocumentFormat.DOC, DocumentFormat.DOCX -> {
                    val docx = docxEngine.parseDocument(uri, document.name)
                    var currentHeading = "Beginning"
                    val chunkBuilder = StringBuilder()
                    var currentEstimatedPage = 1
                    var wordsInPage = 0

                    for (p in docx.paragraphs) {
                        if (p.style == DocxParagraphStyle.TITLE ||
                            p.style == DocxParagraphStyle.HEADING_1 ||
                            p.style == DocxParagraphStyle.HEADING_2
                        ) {
                            currentHeading = p.text
                            tocList.add(
                                DocumentTocEntity(
                                    documentUri = document.uri,
                                    title = p.text,
                                    page = currentEstimatedPage,
                                    level = if (p.style == DocxParagraphStyle.HEADING_2) 2 else 1
                                )
                            )
                        }

                        chunkBuilder.append(p.text).append("\n")
                        wordsInPage += p.text.split(Regex("\\s+")).size

                        if (wordsInPage >= 350) {
                            contentList.add(
                                DocumentContentEntity(
                                    documentUri = document.uri,
                                    documentTitle = document.title,
                                    format = document.format.name,
                                    page = currentEstimatedPage,
                                    chapterTitle = currentHeading,
                                    text = chunkBuilder.toString().trim(),
                                    sourceType = DocumentContentEntity.SOURCE_TEXT
                                )
                            )
                            chunkBuilder.setLength(0)
                            currentEstimatedPage++
                            wordsInPage = 0
                        }
                    }

                    if (chunkBuilder.isNotBlank()) {
                        contentList.add(
                            DocumentContentEntity(
                                documentUri = document.uri,
                                documentTitle = document.title,
                                format = document.format.name,
                                page = currentEstimatedPage,
                                chapterTitle = currentHeading,
                                text = chunkBuilder.toString().trim(),
                                sourceType = DocumentContentEntity.SOURCE_TEXT
                            )
                        )
                    }
                }

                DocumentFormat.PPT, DocumentFormat.PPTX -> {
                    val presentation = pptxEngine.parsePresentation(uri, document.name)
                    for (slide in presentation.slides) {
                        val slideText = "${slide.title}\n${slide.bulletPoints.joinToString("\n")}"
                        contentList.add(
                            DocumentContentEntity(
                                documentUri = document.uri,
                                documentTitle = document.title,
                                format = document.format.name,
                                page = slide.slideNumber,
                                chapterTitle = slide.title,
                                text = slideText,
                                sourceType = DocumentContentEntity.SOURCE_TEXT
                            )
                        )

                        tocList.add(
                            DocumentTocEntity(
                                documentUri = document.uri,
                                title = slide.title,
                                page = slide.slideNumber,
                                level = 1
                            )
                        )
                    }
                }

                DocumentFormat.TXT -> {
                    val txt = textEngine.readText(uri, document.name)
                    val lines = txt.lines
                    var pageNum = 1
                    val chunkLines = mutableListOf<String>()

                    for (line in lines) {
                        if (line.trim().startsWith("Chapter", ignoreCase = true) ||
                            line.trim().startsWith("Section", ignoreCase = true) ||
                            line.trim().startsWith("==", ignoreCase = true)
                        ) {
                            tocList.add(
                                DocumentTocEntity(
                                    documentUri = document.uri,
                                    title = line.trim().take(50),
                                    page = pageNum,
                                    level = 1
                                )
                            )
                        }

                        chunkLines.add(line)
                        if (chunkLines.size >= 40) {
                            contentList.add(
                                DocumentContentEntity(
                                    documentUri = document.uri,
                                    documentTitle = document.title,
                                    format = document.format.name,
                                    page = pageNum,
                                    text = chunkLines.joinToString("\n"),
                                    sourceType = DocumentContentEntity.SOURCE_TEXT
                                )
                            )
                            chunkLines.clear()
                            pageNum++
                        }
                    }

                    if (chunkLines.isNotEmpty()) {
                        contentList.add(
                            DocumentContentEntity(
                                documentUri = document.uri,
                                documentTitle = document.title,
                                format = document.format.name,
                                page = pageNum,
                                text = chunkLines.joinToString("\n"),
                                sourceType = DocumentContentEntity.SOURCE_TEXT
                            )
                        )
                    }
                }
            }

            // Fallback: If document is an image (or unsupported format like photo note), run OCR
            val ext = document.extension.lowercase()
            if (ext in listOf("png", "jpg", "jpeg", "webp", "bmp") && contentList.isEmpty()) {
                val ocrResult = ocrEngine.recognizeTextFromUri(uri)
                if (ocrResult.isNotBlank()) {
                    contentList.add(
                        DocumentContentEntity(
                            documentUri = document.uri,
                            documentTitle = document.title,
                            format = "IMAGE",
                            page = 1,
                            text = ocrResult.trim(),
                            sourceType = DocumentContentEntity.SOURCE_OCR
                        )
                    )
                }
            }

            // Save transactionally in Room
            contentDao.deleteByDocumentUri(document.uri)
            tocDao.deleteByDocumentUri(document.uri)

            if (contentList.isNotEmpty()) {
                contentDao.insertAll(contentList)
            }
            if (tocList.isNotEmpty()) {
                tocDao.insertAll(tocList)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun indexAllDocuments(
        documents: List<DocumentItem>,
        onProgress: ((current: Int, total: Int, docTitle: String) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val total = documents.size
        for ((idx, doc) in documents.withIndex()) {
            onProgress?.invoke(idx + 1, total, doc.title)
            indexDocument(doc)
        }
    }

    /**
     * Executes unified search across:
     * 1. Filenames
     * 2. Extracted document text
     * 3. Chapter / TOC headings
     * 4. OCR text from scanned pages & images
     */
    suspend fun search(
        query: String,
        allDocuments: List<DocumentItem>,
        filterType: SearchFilterType = SearchFilterType.ALL
    ): List<SearchResultItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val cleanQuery = query.trim()
        val queryLower = cleanQuery.lowercase()
        val results = mutableListOf<SearchResultItem>()
        val docMap = allDocuments.associateBy { it.uri }

        // 1. Search Filenames
        if (filterType == SearchFilterType.ALL || filterType == SearchFilterType.FILENAMES) {
            for (doc in allDocuments) {
                if (doc.name.lowercase().contains(queryLower)) {
                    results.add(
                        SearchResultItem(
                            id = "filename_${doc.uri}",
                            documentUri = doc.uri,
                            documentTitle = doc.title,
                            format = doc.format,
                            matchType = SearchMatchType.FILENAME,
                            pageNumber = 1,
                            snippet = "File name match: ${doc.name} (${doc.formattedSize})",
                            highlightTerm = cleanQuery
                        )
                    )
                }
            }
        }

        // 2. Search TOC / Chapter Outlines
        if (filterType == SearchFilterType.ALL || filterType == SearchFilterType.CHAPTERS) {
            val tocMatches = tocDao.searchToc(cleanQuery)
            for (item in tocMatches) {
                val doc = docMap[item.documentUri]
                val format = doc?.format ?: DocumentFormat.PDF
                val title = doc?.title ?: "Document"
                results.add(
                    SearchResultItem(
                        id = "toc_${item.id}",
                        documentUri = item.documentUri,
                        documentTitle = title,
                        format = format,
                        matchType = SearchMatchType.CHAPTER,
                        pageNumber = item.page,
                        chapterTitle = item.title,
                        snippet = "Chapter / Outline: ${item.title} (Page ${item.page})",
                        highlightTerm = cleanQuery
                    )
                )
            }
        }

        // 3. Search Full-Text Content and OCR Output
        if (filterType == SearchFilterType.ALL ||
            filterType == SearchFilterType.CONTENT ||
            filterType == SearchFilterType.OCR
        ) {
            val contentMatches = if (filterType == SearchFilterType.OCR) {
                contentDao.searchBySourceType(cleanQuery, DocumentContentEntity.SOURCE_OCR)
            } else {
                contentDao.searchContent(cleanQuery)
            }

            for (entry in contentMatches) {
                if (filterType == SearchFilterType.CONTENT && entry.sourceType == DocumentContentEntity.SOURCE_OCR) {
                    continue
                }

                val doc = docMap[entry.documentUri]
                val format = doc?.format ?: DocumentFormat.fromExtension(entry.format) ?: DocumentFormat.PDF
                val title = doc?.title ?: entry.documentTitle
                val matchType = if (entry.sourceType == DocumentContentEntity.SOURCE_OCR) {
                    SearchMatchType.OCR
                } else {
                    SearchMatchType.CONTENT
                }

                val snippet = generateSnippet(entry.text, cleanQuery)

                results.add(
                    SearchResultItem(
                        id = "content_${entry.id}",
                        documentUri = entry.documentUri,
                        documentTitle = title,
                        format = format,
                        matchType = matchType,
                        pageNumber = entry.page,
                        chapterTitle = entry.chapterTitle,
                        charOffset = entry.charOffset,
                        snippet = snippet,
                        highlightTerm = cleanQuery
                    )
                )
            }
        }

        results
    }

    private fun generateSnippet(text: String, query: String): String {
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        val index = lowerText.indexOf(lowerQuery)
        if (index == -1) {
            return text.take(120).replace("\n", " ").trim()
        }

        val start = (index - 45).coerceAtLeast(0)
        val end = (index + query.length + 65).coerceAtMost(text.length)

        val prefix = if (start > 0) "... " else ""
        val suffix = if (end < text.length) " ..." else ""

        val rawSnippet = text.substring(start, end).replace(Regex("""\s+"""), " ")
        return "$prefix$rawSnippet$suffix".trim()
    }
}
