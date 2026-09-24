package com.example.data.reader.chapter

import android.content.Context
import android.net.Uri
import com.example.data.model.Chapter
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class PdfChapterExtractor(private val context: Context) {

    init {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun extract(uri: Uri): List<Chapter> = withContext(Dispatchers.IO) {
        val chapters = mutableListOf<Chapter>()
        var pdDoc: PDDocument? = null
        var tempFile: File? = null

        try {
            // Read input stream or create temp file
            val inputStream: InputStream? = if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (file.exists()) {
                    pdDoc = PDDocument.load(file)
                    null
                } else {
                    java.io.FileInputStream(file)
                }
            } else {
                try {
                    context.contentResolver.openInputStream(uri)
                } catch (e: Exception) {
                    null
                }
            }

            if (pdDoc == null && inputStream != null) {
                val temp = File(context.cacheDir, "pdf_toc_${System.currentTimeMillis()}.pdf")
                tempFile = temp
                FileOutputStream(temp).use { out -> inputStream.copyTo(out) }
                pdDoc = PDDocument.load(temp)
            }

            if (pdDoc != null) {
                val catalog = pdDoc.documentCatalog
                val outline = catalog.documentOutline

                if (outline != null) {
                    val pageMap = mutableMapOf<PDPage, Int>()
                    for (i in 0 until pdDoc.numberOfPages) {
                        pageMap[pdDoc.getPage(i)] = i + 1
                    }

                    traverseOutlineNode(outline, 1, pdDoc, pageMap, chapters)
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

        // Fallback: If no outline found in PDF catalog, try byte scanning or generate page structure
        if (chapters.isEmpty()) {
            val fallback = extractFallbackOutline(uri)
            if (fallback.isNotEmpty()) {
                chapters.addAll(fallback)
            }
        }

        chapters
    }

    private fun traverseOutlineNode(
        node: PDOutlineNode,
        level: Int,
        doc: PDDocument,
        pageMap: Map<PDPage, Int>,
        results: MutableList<Chapter>
    ) {
        var currentItem: PDOutlineItem? = node.firstChild
        while (currentItem != null) {
            val title = currentItem.title?.trim() ?: "Section"
            var targetPage: Int? = null

            try {
                val dest = currentItem.destination
                if (dest is PDPageDestination) {
                    val pageNum = dest.pageNumber
                    if (pageNum >= 0) {
                        targetPage = pageNum + 1
                    } else {
                        val pageObj = dest.page
                        if (pageObj != null && pageMap.containsKey(pageObj)) {
                            targetPage = pageMap[pageObj]
                        }
                    }
                } else {
                    val destPage = currentItem.findDestinationPage(doc)
                    if (destPage != null && pageMap.containsKey(destPage)) {
                        targetPage = pageMap[destPage]
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            results.add(
                Chapter(
                    title = title,
                    level = level,
                    startPage = targetPage ?: results.size + 1,
                    startOffset = null,
                    startTimeMs = null
                )
            )

            // Traverse child items at next nesting depth
            if (currentItem.hasChildren()) {
                traverseOutlineNode(currentItem, level + 1, doc, pageMap, results)
            }

            currentItem = currentItem.nextSibling
        }
    }

    private fun extractFallbackOutline(uri: Uri): List<Chapter> {
        val list = mutableListOf<Chapter>()
        try {
            val stream = if (uri.scheme == "file") {
                java.io.FileInputStream(File(uri.path ?: ""))
            } else {
                context.contentResolver.openInputStream(uri)
            }
            val bytes = stream?.use { it.readBytes() } ?: return emptyList()
            val content = String(bytes, Charsets.ISO_8859_1)

            val titleRegex = Regex("""/Title\s*\(([^)]+)\)""")
            val destRegex = Regex("""/Dest\s*\[\s*(\d+)\s+""")

            val titles = titleRegex.findAll(content).map { cleanPdfString(it.groupValues[1]).trim() }.toList()
            val dests = destRegex.findAll(content).map { it.groupValues[1].toIntOrNull() ?: 1 }.toList()

            for ((i, title) in titles.withIndex()) {
                if (title.isNotBlank() && !title.startsWith("D:") && title.length < 100) {
                    val pageNum = dests.getOrNull(i) ?: (i + 1)
                    list.add(
                        Chapter(
                            title = title,
                            level = 1,
                            startPage = pageNum,
                            startOffset = null,
                            startTimeMs = null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.distinctBy { it.title }
    }

    private fun cleanPdfString(raw: String): String {
        return raw.replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\\\", "\\")
            .replace("\\n", " ")
            .replace("\\r", " ")
            .replace("\\t", " ")
            .trim()
    }
}
