package com.example.data.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.data.local.entity.DocumentContentEntity
import com.example.data.local.entity.DocumentTocEntity
import com.example.data.ocr.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

data class ExtractedPdfPage(
    val pageNumber: Int,
    val text: String,
    val isOcr: Boolean
)

data class ExtractedPdfResult(
    val pages: List<ExtractedPdfPage>,
    val tocItems: List<DocumentTocEntity>
)

class PdfContentExtractor(
    private val context: Context,
    private val ocrEngine: OcrEngine
) {

    suspend fun extract(uri: Uri, documentTitle: String): ExtractedPdfResult = withContext(Dispatchers.IO) {
        val pages = mutableListOf<ExtractedPdfPage>()
        val tocItems = mutableListOf<DocumentTocEntity>()

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var tempFile: File? = null

        try {
            // Open parcel file descriptor
            pfd = if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                try {
                    context.contentResolver.openFileDescriptor(uri, "r")
                } catch (e: Exception) {
                    val temp = File(context.cacheDir, "pdf_index_${System.currentTimeMillis()}.pdf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(temp).use { output -> input.copyTo(output) }
                    }
                    tempFile = temp
                    ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
                }
            }

            // Extract stream-based raw text and outline from bytes
            val rawPdfBytes = readPdfBytes(uri, tempFile)
            val extractedStreams = if (rawPdfBytes != null) extractTextFromPdfBytes(rawPdfBytes) else emptyList()
            val extractedOutlines = if (rawPdfBytes != null) extractOutlineTitles(rawPdfBytes, uri.toString()) else emptyList()
            tocItems.addAll(extractedOutlines)

            // Setup renderer for OCR fallback if pages have little or no text
            pfd?.let { descriptor ->
                try {
                    renderer = PdfRenderer(descriptor)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val pageCount = renderer?.pageCount ?: extractedStreams.size.coerceAtLeast(1)

            for (pageIndex in 0 until pageCount) {
                val pageNum = pageIndex + 1
                val streamText = extractedStreams.getOrNull(pageIndex)?.trim() ?: ""

                // If stream text is rich enough, use it as digital text
                if (streamText.length >= 40) {
                    pages.add(ExtractedPdfPage(pageNum, streamText, isOcr = false))
                } else {
                    // Page is scanned/image-based or text stream is empty: run OCR on rendered bitmap!
                    var ocrText = ""
                    renderer?.let { rend ->
                        if (pageIndex < rend.pageCount) {
                            try {
                                val page = rend.openPage(pageIndex)
                                val width = (page.width * 1.5f).toInt().coerceIn(600, 1600)
                                val height = (page.height * 1.5f).toInt().coerceIn(800, 2400)
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(bitmap)
                                canvas.drawColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                page.close()

                                ocrText = ocrEngine.recognizeTextFromBitmap(bitmap)
                                bitmap.recycle()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    if (ocrText.isNotBlank()) {
                        pages.add(ExtractedPdfPage(pageNum, ocrText.trim(), isOcr = true))
                    } else if (streamText.isNotBlank()) {
                        pages.add(ExtractedPdfPage(pageNum, streamText, isOcr = false))
                    } else {
                        pages.add(ExtractedPdfPage(pageNum, "Page $pageNum", isOcr = false))
                    }
                }
            }

            // If no explicit PDF outlines were found, create fallback TOC from detected chapter headings or pages
            if (tocItems.isEmpty()) {
                for (page in pages) {
                    val firstLine = page.text.lines().firstOrNull { it.isNotBlank() }?.trim() ?: ""
                    if (firstLine.startsWith("Chapter", ignoreCase = true) ||
                        firstLine.startsWith("Section", ignoreCase = true) ||
                        firstLine.startsWith("Part", ignoreCase = true) ||
                        firstLine.contains("Table of Contents", ignoreCase = true) ||
                        firstLine.contains("Introduction", ignoreCase = true)
                    ) {
                        val headingTitle = firstLine.take(60)
                        tocItems.add(
                            DocumentTocEntity(
                                documentUri = uri.toString(),
                                title = headingTitle,
                                page = page.pageNumber,
                                level = 1
                            )
                        )
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { renderer?.close() } catch (e: Exception) {}
            try { pfd?.close() } catch (e: Exception) {}
            tempFile?.delete()
        }

        ExtractedPdfResult(pages, tocItems)
    }

    private fun readPdfBytes(uri: Uri, tempFile: File?): ByteArray? {
        return try {
            if (tempFile != null && tempFile.exists()) {
                tempFile.readBytes()
            } else {
                val stream: InputStream? = if (uri.scheme == "file") {
                    java.io.FileInputStream(java.io.File(uri.path ?: ""))
                } else {
                    context.contentResolver.openInputStream(uri)
                }
                stream?.use { it.readBytes() }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts text streams from PDF objects by uncompressing FlateDecode streams.
     */
    private fun extractTextFromPdfBytes(pdfBytes: ByteArray): List<String> {
        val pagesText = mutableListOf<String>()
        try {
            val content = String(pdfBytes, Charsets.ISO_8859_1)
            val streamRegex = Regex("""stream[\r\n]+([\s\S]*?)[\r\n]+endstream""")
            val matches = streamRegex.findAll(content)

            val currentBuffer = StringBuilder()

            for (match in matches) {
                val start = match.groups[1]?.range?.first ?: continue
                val end = match.groups[1]?.range?.last ?: continue
                val streamSlice = pdfBytes.copyOfRange(start, end + 1)

                val decompressed = tryDecompressFlate(streamSlice) ?: String(streamSlice, Charsets.ISO_8859_1)
                val extracted = parsePdfTextOperators(decompressed)
                if (extracted.isNotBlank()) {
                    currentBuffer.append(extracted).append("\n")
                }
            }

            if (currentBuffer.isNotBlank()) {
                // Split roughly across pages or group into chunks
                pagesText.add(currentBuffer.toString())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return pagesText
    }

    private fun tryDecompressFlate(bytes: ByteArray): String? {
        return try {
            val inflater = Inflater(false)
            val inStream = InflaterInputStream(bytes.inputStream(), inflater)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            var len: Int
            while (inStream.read(buf).also { len = it } > 0) {
                out.write(buf, 0, len)
            }
            String(out.toByteArray(), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun parsePdfTextOperators(content: String): String {
        val sb = StringBuilder()
        // Match BT ... ET blocks
        val btRegex = Regex("""BT\s+([\s\S]*?)\s+ET""")
        for (btMatch in btRegex.findAll(content)) {
            val block = btMatch.groupValues[1]

            // Match Tj strings: (Hello world) Tj
            val tjRegex = Regex("""\((.*?)\)\s*Tj""")
            for (tj in tjRegex.findAll(block)) {
                sb.append(cleanPdfString(tj.groupValues[1])).append(" ")
            }

            // Match TJ array: [(Hello) 10 (world)] TJ
            val tjArrayRegex = Regex("""\[([\s\S]*?)\]\s*TJ""")
            for (tjArr in tjArrayRegex.findAll(block)) {
                val inner = tjArr.groupValues[1]
                val subStr = Regex("""\((.*?)\)""").findAll(inner)
                for (s in subStr) {
                    sb.append(cleanPdfString(s.groupValues[1])).append(" ")
                }
            }
            sb.append("\n")
        }
        return sb.toString().trim()
    }

    private fun cleanPdfString(raw: String): String {
        return raw.replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\\\", "\\")
            .replace("\\n", " ")
            .replace("\\r", " ")
            .replace("\\t", " ")
    }

    /**
     * Extracts outline bookmarks/headings from PDF `/Outlines` dictionary.
     */
    private fun extractOutlineTitles(pdfBytes: ByteArray, docUri: String): List<DocumentTocEntity> {
        val list = mutableListOf<DocumentTocEntity>()
        try {
            val content = String(pdfBytes, Charsets.ISO_8859_1)
            // Search for /Title (Chapter Name) in outlines
            val titleRegex = Regex("""/Title\s*\(([^)]+)\)""")
            val destRegex = Regex("""/Dest\s*\[\s*(\d+)\s+""")

            val titles = titleRegex.findAll(content).map { cleanPdfString(it.groupValues[1]).trim() }.toList()
            val dests = destRegex.findAll(content).map { it.groupValues[1].toIntOrNull() ?: 1 }.toList()

            for ((i, title) in titles.withIndex()) {
                if (title.isNotBlank()) {
                    val pageNum = dests.getOrNull(i) ?: (i + 1)
                    list.add(
                        DocumentTocEntity(
                            documentUri = docUri,
                            title = title,
                            page = pageNum,
                            level = 1
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.distinctBy { it.title }
    }
}
