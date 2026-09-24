package com.example.data.search

import android.net.Uri
import com.example.data.local.dao.ChapterDao
import com.example.data.local.dao.DocumentSearchDao
import com.example.data.local.dao.OcrDao
import com.example.data.model.DocumentFormat
import com.example.data.model.DocumentItem
import com.example.data.model.MatchType
import com.example.data.model.SearchFilters
import com.example.data.model.SearchHit
import com.example.data.model.SearchLocation
import com.example.data.repository.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class DocumentSearchEngine(
    private val repository: DocumentRepository,
    private val searchDao: DocumentSearchDao,
    private val chapterDao: ChapterDao,
    private val ocrDao: OcrDao
) {

    /**
     * Executes parallel search across Filenames, Room FTS4 Content, Chapter Headings, and OCR Text.
     * De-duplicates and ranks results by relevance score.
     */
    fun search(query: String, filters: SearchFilters = SearchFilters()): Flow<List<SearchHit>> = flow {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val allDocs = repository.allDocuments.first()
        val docsByUri = allDocs.associateBy { it.uri }

        val hits = coroutineScope {
            val filenameDeferred = async(Dispatchers.IO) {
                if (filters.matchTypes.contains(MatchType.FILENAME)) {
                    searchFilenames(cleanQuery, allDocs, filters.formats)
                } else emptyList()
            }

            val contentDeferred = async(Dispatchers.IO) {
                if (filters.matchTypes.contains(MatchType.CONTENT)) {
                    searchContentFts(cleanQuery, docsByUri, filters.formats)
                } else emptyList()
            }

            val chapterDeferred = async(Dispatchers.IO) {
                if (filters.matchTypes.contains(MatchType.CHAPTER)) {
                    searchChapters(cleanQuery, docsByUri, filters.formats)
                } else emptyList()
            }

            val ocrDeferred = async(Dispatchers.IO) {
                if (filters.matchTypes.contains(MatchType.OCR)) {
                    searchOcr(cleanQuery, docsByUri, filters.formats)
                } else emptyList()
            }

            val fHits = filenameDeferred.await()
            val cHits = contentDeferred.await()
            val chHits = chapterDeferred.await()
            val oHits = ocrDeferred.await()

            val combined = mutableListOf<SearchHit>()
            combined.addAll(fHits)
            combined.addAll(cHits)
            combined.addAll(chHits)
            combined.addAll(oHits)

            // De-duplicate by fileUri + location + matchType
            combined.distinctBy {
                "${it.fileUri}_${it.matchType}_${it.location.page}_${it.location.chapterIndex}_${it.location.charOffset}"
            }.sortedByDescending { it.score }
        }

        emit(hits)
    }.flowOn(Dispatchers.IO)

    private fun searchFilenames(
        query: String,
        allDocs: List<DocumentItem>,
        allowedFormats: Set<DocumentFormat>
    ): List<SearchHit> {
        val results = mutableListOf<SearchHit>()
        val lowerQuery = query.lowercase()

        for (doc in allDocs) {
            if (allowedFormats.isNotEmpty() && !allowedFormats.contains(doc.format)) {
                continue
            }

            val titleLower = doc.title.lowercase()
            if (titleLower.contains(lowerQuery)) {
                val isExact = titleLower == lowerQuery || titleLower.substringBeforeLast(".") == lowerQuery
                val score = if (isExact) 1.0f else 0.90f
                val snippet = "Matching document: ${SearchSnippetHelper.highlightQueryTerms(doc.title, query)}"

                results.add(
                    SearchHit(
                        fileUri = Uri.parse(doc.uri),
                        fileName = doc.title,
                        matchType = MatchType.FILENAME,
                        snippet = snippet,
                        location = SearchLocation(page = 1, label = "Document"),
                        score = score
                    )
                )
            }
        }
        return results
    }

    private suspend fun searchContentFts(
        query: String,
        docsByUri: Map<String, DocumentItem>,
        allowedFormats: Set<DocumentFormat>
    ): List<SearchHit> {
        val results = mutableListOf<SearchHit>()

        // Prepare FTS query token (e.g. *query* or individual terms)
        val ftsQuery = query.replace("\"", "").trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }

        val ftsMatches = try {
            if (ftsQuery.isNotBlank()) {
                searchDao.searchFts(ftsQuery)
            } else {
                searchDao.searchFallback(query)
            }
        } catch (e: Exception) {
            // Fallback to LIKE if FTS parser encounters syntax edge case
            searchDao.searchFallback(query)
        }

        for (item in ftsMatches) {
            val doc = if (item is com.example.data.local.entity.FtsDocument) {
                docsByUri[item.fileUri]
            } else if (item is com.example.data.local.entity.DocumentTextEntity) {
                docsByUri[item.fileUri]
            } else null

            if (doc != null && allowedFormats.isNotEmpty() && !allowedFormats.contains(doc.format)) {
                continue
            }

            val fileUriStr = if (item is com.example.data.local.entity.FtsDocument) item.fileUri else (item as com.example.data.local.entity.DocumentTextEntity).fileUri
            val pageNumber = if (item is com.example.data.local.entity.FtsDocument) item.pageNumber else (item as com.example.data.local.entity.DocumentTextEntity).pageNumber
            val fullBody = if (item is com.example.data.local.entity.FtsDocument) item.body else (item as com.example.data.local.entity.DocumentTextEntity).fullText
            val title = doc?.title ?: (if (item is com.example.data.local.entity.FtsDocument) item.title else "Document")

            val snippet = SearchSnippetHelper.createSnippet(fullBody, query)

            results.add(
                SearchHit(
                    fileUri = Uri.parse(fileUriStr),
                    fileName = title,
                    matchType = MatchType.CONTENT,
                    snippet = snippet,
                    location = SearchLocation(page = pageNumber, label = "Page $pageNumber"),
                    score = 0.75f
                )
            )
        }

        return results
    }

    private suspend fun searchChapters(
        query: String,
        docsByUri: Map<String, DocumentItem>,
        allowedFormats: Set<DocumentFormat>
    ): List<SearchHit> {
        val results = mutableListOf<SearchHit>()
        val matches = chapterDao.searchChapters(query)

        for (ch in matches) {
            val doc = docsByUri[ch.fileUri]
            if (doc != null && allowedFormats.isNotEmpty() && !allowedFormats.contains(doc.format)) {
                continue
            }

            val fileName = doc?.title ?: "Document"
            val snippet = "Chapter section: ${SearchSnippetHelper.highlightQueryTerms(ch.title, query)}"

            results.add(
                SearchHit(
                    fileUri = Uri.parse(ch.fileUri),
                    fileName = fileName,
                    matchType = MatchType.CHAPTER,
                    snippet = snippet,
                    location = SearchLocation(
                        page = ch.startPage,
                        charOffset = ch.startOffset,
                        label = "Chapter (Lvl ${ch.level}): ${ch.title}"
                    ),
                    score = 0.82f
                )
            )
        }

        return results
    }

    private suspend fun searchOcr(
        query: String,
        docsByUri: Map<String, DocumentItem>,
        allowedFormats: Set<DocumentFormat>
    ): List<SearchHit> {
        val results = mutableListOf<SearchHit>()
        val matches = ocrDao.searchOcr(query)

        for (ocr in matches) {
            val doc = docsByUri[ocr.fileUri]
            if (doc != null && allowedFormats.isNotEmpty() && !allowedFormats.contains(doc.format)) {
                continue
            }

            val fileName = doc?.title ?: "Document"
            val snippet = SearchSnippetHelper.createSnippet(ocr.text, query)

            results.add(
                SearchHit(
                    fileUri = Uri.parse(ocr.fileUri),
                    fileName = fileName,
                    matchType = MatchType.OCR,
                    snippet = snippet,
                    location = SearchLocation(
                        page = ocr.pageNumber,
                        label = "OCR Page ${ocr.pageNumber} (${(ocr.confidence * 100).toInt()}%)"
                    ),
                    score = 0.70f
                )
            )
        }

        return results
    }
}
