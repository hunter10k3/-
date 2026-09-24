package com.example.data.reader.chapter

import android.content.Context
import android.net.Uri
import com.example.data.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class DocxChapterExtractor(private val context: Context) {

    suspend fun extract(uri: Uri): List<Chapter> = withContext(Dispatchers.IO) {
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

        if (documentXml == null) return@withContext emptyList()

        parseHeadingsFromXml(documentXml!!)
    }

    private fun parseHeadingsFromXml(xml: String): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var inParagraph = false
            var currentHeadingLevel: Int? = null
            val currentParagraphText = StringBuilder()
            var cumulativeOffset = 0L

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name == "p") {
                            inParagraph = true
                            currentHeadingLevel = null
                            currentParagraphText.setLength(0)
                        } else if (inParagraph && name == "pStyle") {
                            val styleVal = parser.getAttributeValue(null, "val") ?: ""
                            currentHeadingLevel = extractHeadingLevel(styleVal)
                        } else if (inParagraph && name == "t") {
                            val text = parser.nextText()
                            currentParagraphText.append(text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name == "p") {
                            inParagraph = false
                            val paragraphText = currentParagraphText.toString().trim()
                            val textLen = paragraphText.length.toLong()

                            if (currentHeadingLevel != null && paragraphText.isNotBlank()) {
                                val pageEstimate = ((cumulativeOffset / 1800L) + 1).toInt()
                                chapters.add(
                                    Chapter(
                                        title = paragraphText,
                                        level = currentHeadingLevel!!,
                                        startPage = pageEstimate,
                                        startOffset = cumulativeOffset,
                                        startTimeMs = null
                                    )
                                )
                            }
                            cumulativeOffset += textLen + 1L
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return chapters
    }

    private fun extractHeadingLevel(styleVal: String): Int? {
        val normalized = styleVal.trim()
        val headingMatch = Regex("""Heading\s*([1-6])""", RegexOption.IGNORE_CASE).find(normalized)
        if (headingMatch != null) {
            return headingMatch.groupValues[1].toIntOrNull() ?: 1
        }
        if (normalized.equals("Title", ignoreCase = true)) {
            return 1
        }
        if (normalized.equals("Subtitle", ignoreCase = true)) {
            return 2
        }
        return null
    }
}
