package com.example.data.indexer

import android.content.Context
import android.net.Uri
import com.example.data.local.dao.DocumentSearchDao
import com.example.data.local.entity.DocumentTextEntity
import com.example.data.local.entity.FtsDocument
import com.example.data.model.DocumentFormat
import com.example.data.model.DocumentItem
import com.example.data.reader.DocxReaderEngine
import com.example.data.reader.EpubReaderEngine
import com.example.data.reader.PptxReaderEngine
import com.example.data.reader.TextReaderEngine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class DocumentContentIndexer(
    private val context: Context,
    private val searchDao: DocumentSearchDao,
    private val ocrDao: com.example.data.local.dao.OcrDao? = null
) {
    private val epubEngine = EpubReaderEngine(context)
    private val docxEngine = DocxReaderEngine(context)
    private val pptxEngine = PptxReaderEngine(context)
    private val textEngine = TextReaderEngine(context)
    private val ocrProcessor: com.example.data.ocr.OcrProcessor by lazy {
        val oDao = ocrDao ?: com.example.data.local.LairikDatabase.getDatabase(context).ocrDao()
        com.example.data.ocr.OcrProcessor(context, oDao, searchDao)
    }

    init {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Incrementally indexes a document. If [force] is true or file hash changed,
     * parses the file and updates the FTS index.
     */
    suspend fun indexDocument(doc: DocumentItem, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val uri = Uri.parse(doc.uri)
        val fileHash = computeFileHash(doc)

        if (!force) {
            val existingHash = searchDao.getContentHash(doc.uri)
            if (existingHash != null && existingHash == fileHash) {
                // Already indexed and unmodified
                return@withContext false
            }
        }

        val texts = mutableListOf<DocumentTextEntity>()
        val ftsList = mutableListOf<FtsDocument>()

        try {
            when (doc.format) {
                DocumentFormat.PDF -> indexPdf(uri, doc, fileHash, texts, ftsList)
                DocumentFormat.EPUB -> indexEpub(uri, doc, fileHash, texts, ftsList)
                DocumentFormat.DOC, DocumentFormat.DOCX -> indexDocx(uri, doc, fileHash, texts, ftsList)
                DocumentFormat.PPT, DocumentFormat.PPTX -> indexPptx(uri, doc, fileHash, texts, ftsList)
                DocumentFormat.TXT -> indexTxt(uri, doc, fileHash, texts, ftsList)
            }

            if (texts.isNotEmpty() || ftsList.isNotEmpty()) {
                searchDao.replaceDocumentIndex(doc.uri, texts, ftsList)
                return@withContext true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext false
    }

    private fun computeFileHash(doc: DocumentItem): String {
        return "${doc.sizeBytes}_${doc.lastModified}"
    }

    private fun indexPdf(
        uri: Uri,
        doc: DocumentItem,
        fileHash: String,
        texts: MutableList<DocumentTextEntity>,
        ftsList: MutableList<FtsDocument>
    ) {
        var pdDoc: PDDocument? = null
        var tempFile: File? = null

        try {
            val inputStream: InputStream? = if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (file.exists()) {
                    pdDoc = PDDocument.load(file)
                    null
                } else {
                    java.io.FileInputStream(file)
                }
            } else {
                context.contentResolver.openInputStream(uri)
            }

            if (pdDoc == null && inputStream != null) {
                val temp = File(context.cacheDir, "pdf_idx_${System.currentTimeMillis()}.pdf")
                tempFile = temp
                FileOutputStream(temp).use { out -> inputStream.copyTo(out) }
                pdDoc = PDDocument.load(temp)
            }

            if (pdDoc != null) {
                val stripper = PDFTextStripper()
                val totalPages = pdDoc.numberOfPages

                for (page in 1..totalPages) {
                    try {
                        stripper.startPage = page
                        stripper.endPage = page
                        val pageText = stripper.getText(pdDoc)?.trim() ?: ""

                        if (pageText.length >= 20) {
                            texts.add(
                                DocumentTextEntity(
                                    fileUri = doc.uri,
                                    pageNumber = page,
                                    fullText = pageText,
                                    indexedAt = System.currentTimeMillis(),
                                    contentHash = fileHash
                                )
                            )
                            ftsList.add(
                                FtsDocument(
                                    fileUri = doc.uri,
                                    pageNumber = page,
                                    title = doc.title,
                                    body = pageText
                                )
                            )
                        } else {
                            // Low text density / image-only page: process via OCR
                            val results = kotlinx.coroutines.runBlocking {
                                ocrProcessor.processPdf(uri, doc.title, forceAllPages = false)
                            }
                            break
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                pdDoc?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            tempFile?.delete()
        }
    }

    private suspend fun indexEpub(
        uri: Uri,
        doc: DocumentItem,
        fileHash: String,
        texts: MutableList<DocumentTextEntity>,
        ftsList: MutableList<FtsDocument>
    ) {
        val epubBook = epubEngine.parseEpub(uri)
        for ((index, chapter) in epubBook.chapters.withIndex()) {
            val pageNum = index + 1
            val bodyText = chapter.paragraphs.joinToString("\n\n")
            if (bodyText.isNotBlank()) {
                texts.add(
                    DocumentTextEntity(
                        fileUri = doc.uri,
                        pageNumber = pageNum,
                        fullText = "${chapter.title}\n\n$bodyText",
                        indexedAt = System.currentTimeMillis(),
                        contentHash = fileHash
                    )
                )
                ftsList.add(
                    FtsDocument(
                        fileUri = doc.uri,
                        pageNumber = pageNum,
                        title = "${doc.title} - ${chapter.title}",
                        body = bodyText
                    )
                )
            }
        }
    }

    private suspend fun indexDocx(
        uri: Uri,
        doc: DocumentItem,
        fileHash: String,
        texts: MutableList<DocumentTextEntity>,
        ftsList: MutableList<FtsDocument>
    ) {
        val docx = docxEngine.parseDocument(uri, doc.title)
        val paragraphs = docx.paragraphs
        if (paragraphs.isEmpty()) return

        // Chunk paragraphs into pages of ~10 paragraphs
        val chunkSize = 10
        val chunks = paragraphs.chunked(chunkSize)

        for ((index, chunk) in chunks.withIndex()) {
            val pageNum = index + 1
            val pageText = chunk.joinToString("\n") { it.text }
            if (pageText.isNotBlank()) {
                texts.add(
                    DocumentTextEntity(
                        fileUri = doc.uri,
                        pageNumber = pageNum,
                        fullText = pageText,
                        indexedAt = System.currentTimeMillis(),
                        contentHash = fileHash
                    )
                )
                ftsList.add(
                    FtsDocument(
                        fileUri = doc.uri,
                        pageNumber = pageNum,
                        title = doc.title,
                        body = pageText
                    )
                )
            }
        }
    }

    private suspend fun indexPptx(
        uri: Uri,
        doc: DocumentItem,
        fileHash: String,
        texts: MutableList<DocumentTextEntity>,
        ftsList: MutableList<FtsDocument>
    ) {
        val pptx = pptxEngine.parsePresentation(uri, doc.title)
        for (slide in pptx.slides) {
            val slideText = buildString {
                if (slide.title.isNotBlank()) appendLine(slide.title)
                slide.bulletPoints.forEach { appendLine(it) }
            }.trim()

            if (slideText.isNotBlank()) {
                texts.add(
                    DocumentTextEntity(
                        fileUri = doc.uri,
                        pageNumber = slide.slideNumber,
                        fullText = slideText,
                        indexedAt = System.currentTimeMillis(),
                        contentHash = fileHash
                    )
                )
                ftsList.add(
                    FtsDocument(
                        fileUri = doc.uri,
                        pageNumber = slide.slideNumber,
                        title = "${doc.title} (Slide ${slide.slideNumber})",
                        body = slideText
                    )
                )
            }
        }
    }

    private suspend fun indexTxt(
        uri: Uri,
        doc: DocumentItem,
        fileHash: String,
        texts: MutableList<DocumentTextEntity>,
        ftsList: MutableList<FtsDocument>
    ) {
        val textDoc = textEngine.readText(uri, doc.title)
        val full = textDoc.fullText
        if (full.isBlank()) return

        // Chunk into ~2500 character pages
        val chunkSize = 2500
        var offset = 0
        var page = 1

        while (offset < full.length) {
            val end = (offset + chunkSize).coerceAtMost(full.length)
            val chunkText = full.substring(offset, end)

            texts.add(
                DocumentTextEntity(
                    fileUri = doc.uri,
                    pageNumber = page,
                    fullText = chunkText,
                    indexedAt = System.currentTimeMillis(),
                    contentHash = fileHash
                )
            )
            ftsList.add(
                FtsDocument(
                    fileUri = doc.uri,
                    pageNumber = page,
                    title = doc.title,
                    body = chunkText
                )
            )

            offset = end
            page++
        }
    }
}
