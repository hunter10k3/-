package com.example.data.reader

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

enum class DocxParagraphStyle {
    TITLE,
    HEADING_1,
    HEADING_2,
    BODY,
    BULLET
}

data class DocxParagraph(
    val text: String,
    val style: DocxParagraphStyle
)

data class DocxDocument(
    val title: String,
    val paragraphs: List<DocxParagraph>,
    val wordCount: Int
)

class DocxReaderEngine(private val context: Context) {

    suspend fun parseDocument(uri: Uri, fileName: String): DocxDocument = withContext(Dispatchers.IO) {
        if (fileName.endsWith(".doc", ignoreCase = true) && !fileName.endsWith(".docx", ignoreCase = true)) {
            // Legacy binary DOC file
            return@withContext parseLegacyDoc(uri, fileName)
        }

        var documentXml: String? = null

        val inputStream: InputStream? = if (uri.scheme == "file") {
            java.io.FileInputStream(java.io.File(uri.path ?: ""))
        } else {
            context.contentResolver.openInputStream(uri)
        }

        inputStream?.use { stream ->
            ZipInputStream(stream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        documentXml = String(zis.readBytes())
                        break
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        if (documentXml == null) {
            return@withContext DocxDocument(
                title = fileName.substringBeforeLast('.'),
                paragraphs = listOf(DocxParagraph("Unable to load document content.", DocxParagraphStyle.BODY)),
                wordCount = 0
            )
        }

        val paragraphs = parseDocumentXml(documentXml!!)
        val wordCount = paragraphs.sumOf { p -> p.text.split(Regex("\\s+")).count { it.isNotBlank() } }

        DocxDocument(
            title = fileName.substringBeforeLast('.'),
            paragraphs = paragraphs,
            wordCount = wordCount
        )
    }

    private fun parseDocumentXml(xml: String): List<DocxParagraph> {
        val result = mutableListOf<DocxParagraph>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var inParagraph = false
            var currentStyle = DocxParagraphStyle.BODY
            val currentParagraphText = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name == "p") {
                            inParagraph = true
                            currentStyle = DocxParagraphStyle.BODY
                            currentParagraphText.setLength(0)
                        } else if (inParagraph && name == "pStyle") {
                            val styleVal = parser.getAttributeValue(null, "val")?.lowercase() ?: ""
                            currentStyle = when {
                                styleVal.contains("title") -> DocxParagraphStyle.TITLE
                                styleVal.contains("heading1") || styleVal.contains("heading 1") -> DocxParagraphStyle.HEADING_1
                                styleVal.contains("heading2") || styleVal.contains("heading 2") -> DocxParagraphStyle.HEADING_2
                                styleVal.contains("list") || styleVal.contains("bullet") -> DocxParagraphStyle.BULLET
                                else -> DocxParagraphStyle.BODY
                            }
                        } else if (inParagraph && name == "numPr") {
                            currentStyle = DocxParagraphStyle.BULLET
                        } else if (inParagraph && name == "t") {
                            val text = parser.nextText()
                            currentParagraphText.append(text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name == "p") {
                            inParagraph = false
                            val text = currentParagraphText.toString().trim()
                            if (text.isNotEmpty()) {
                                result.add(DocxParagraph(text, currentStyle))
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun parseLegacyDoc(uri: Uri, fileName: String): DocxDocument {
        val paragraphs = mutableListOf<DocxParagraph>()
        try {
            val inputStream = if (uri.scheme == "file") {
                java.io.FileInputStream(java.io.File(uri.path ?: ""))
            } else {
                context.contentResolver.openInputStream(uri)
            }

            inputStream?.use { stream ->
                val bytes = stream.readBytes()
                // Extract contiguous ASCII / UTF-8 printable strings
                val sb = StringBuilder()
                for (b in bytes) {
                    val c = b.toInt().toChar()
                    if (c in ' '..'~' || c == '\n' || c == '\t') {
                        sb.append(c)
                    } else if (sb.isNotEmpty() && sb.last() != '\n') {
                        sb.append('\n')
                    }
                }

                val lines = sb.toString().split('\n')
                    .map { it.trim() }
                    .filter { it.length >= 4 && !it.startsWith("ROOT") && !it.startsWith("CompObj") }

                for (line in lines) {
                    paragraphs.add(DocxParagraph(line, DocxParagraphStyle.BODY))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (paragraphs.isEmpty()) {
            paragraphs.add(DocxParagraph("Document text could not be extracted from legacy format.", DocxParagraphStyle.BODY))
        }

        val wordCount = paragraphs.sumOf { p -> p.text.split(Regex("\\s+")).count { it.isNotBlank() } }
        return DocxDocument(
            title = fileName.substringBeforeLast('.'),
            paragraphs = paragraphs,
            wordCount = wordCount
        )
    }
}
