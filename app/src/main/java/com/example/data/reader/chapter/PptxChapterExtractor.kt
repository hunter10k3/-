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

class PptxChapterExtractor(private val context: Context) {

    suspend fun extract(uri: Uri): List<Chapter> = withContext(Dispatchers.IO) {
        val zipMap = mutableMapOf<String, ByteArray>()
        val inputStream: InputStream? = if (uri.scheme == "file") {
            java.io.FileInputStream(java.io.File(uri.path ?: ""))
        } else {
            context.contentResolver.openInputStream(uri)
        }

        inputStream?.use { stream ->
            ZipInputStream(stream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        zipMap[entry.name] = zis.readBytes()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        if (zipMap.isEmpty()) return@withContext emptyList()

        // 1. Determine slide order using ppt/presentation.xml and ppt/_rels/presentation.xml.rels
        val orderedSlidePaths = mutableListOf<String>()
        val presentationXml = zipMap["ppt/presentation.xml"]?.let { String(it) }
        val presentationRels = zipMap["ppt/_rels/presentation.xml.rels"]?.let { String(it) }

        if (presentationXml != null && presentationRels != null) {
            val relsMap = mutableMapOf<String, String>() // rId -> target
            val relRegex = Regex("""<Relationship[^>]*Id="([^"]+)"[^>]*Target="([^"]+)"""", RegexOption.IGNORE_CASE)
            for (match in relRegex.findAll(presentationRels)) {
                relsMap[match.groupValues[1]] = match.groupValues[2]
            }

            val sldIdRegex = Regex("""<p:sldId[^>]*r:id="([^"]+)"""", RegexOption.IGNORE_CASE)
            for (match in sldIdRegex.findAll(presentationXml)) {
                val rId = match.groupValues[1]
                val target = relsMap[rId] ?: continue
                val normalizedTarget = if (target.startsWith("ppt/")) target else "ppt/$target"
                val cleanPath = normalizedTarget.replace("//", "/").removePrefix("/")
                if (zipMap.containsKey(cleanPath) || zipMap.containsKey(cleanPath.removePrefix("ppt/"))) {
                    orderedSlidePaths.add(if (zipMap.containsKey(cleanPath)) cleanPath else cleanPath.removePrefix("ppt/"))
                }
            }
        }

        // Fallback: If rels order not resolved, sort slide entries by number
        if (orderedSlidePaths.isEmpty()) {
            val slideEntries = zipMap.keys.filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
                .sortedBy { path ->
                    val num = path.removePrefix("ppt/slides/slide").removeSuffix(".xml")
                    num.toIntOrNull() ?: 0
                }
            orderedSlidePaths.addAll(slideEntries)
        }

        val chapters = mutableListOf<Chapter>()
        for ((index, slidePath) in orderedSlidePaths.withIndex()) {
            val slideXml = zipMap[slidePath]?.let { String(it) } ?: continue
            val slideTitle = extractSlideTitle(slideXml, index + 1)
            chapters.add(
                Chapter(
                    title = slideTitle,
                    level = 1,
                    startPage = index + 1,
                    startOffset = null,
                    startTimeMs = null
                )
            )
        }

        chapters
    }

    private fun extractSlideTitle(xml: String, slideNumber: Int): String {
        var detectedTitle: String? = null
        val paragraphs = mutableListOf<String>()

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var inParagraph = false
            var currentParagraphText = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name == "p") {
                            inParagraph = true
                            currentParagraphText.setLength(0)
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
                                paragraphs.add(text)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return paragraphs.firstOrNull()?.take(80) ?: "Slide $slideNumber"
    }
}
