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

data class PptxSlide(
    val slideNumber: Int,
    val title: String,
    val bulletPoints: List<String>
)

data class PptxPresentation(
    val title: String,
    val slides: List<PptxSlide>
)

class PptxReaderEngine(private val context: Context) {

    suspend fun parsePresentation(uri: Uri, fileName: String): PptxPresentation = withContext(Dispatchers.IO) {
        val slideEntries = mutableMapOf<Int, String>() // slideIndex to xml

        val inputStream: InputStream? = if (uri.scheme == "file") {
            java.io.FileInputStream(java.io.File(uri.path ?: ""))
        } else {
            context.contentResolver.openInputStream(uri)
        }

        inputStream?.use { stream ->
            ZipInputStream(stream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name.startsWith("ppt/slides/slide") && name.endsWith(".xml")) {
                        // Extract slide number e.g. "slide1.xml" -> 1
                        val numberStr = name.removePrefix("ppt/slides/slide").removeSuffix(".xml")
                        val slideNumber = numberStr.toIntOrNull() ?: (slideEntries.size + 1)
                        slideEntries[slideNumber] = String(zis.readBytes())
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        val slides = mutableListOf<PptxSlide>()
        val sortedKeys = slideEntries.keys.sorted()

        for (slideNum in sortedKeys) {
            val xml = slideEntries[slideNum] ?: continue
            val slide = parseSlideXml(slideNum, xml)
            slides.add(slide)
        }

        if (slides.isEmpty()) {
            slides.add(
                PptxSlide(
                    slideNumber = 1,
                    title = fileName.substringBeforeLast('.'),
                    bulletPoints = listOf("Slide content could not be previewed or presentation is empty.")
                )
            )
        }

        PptxPresentation(
            title = fileName.substringBeforeLast('.'),
            slides = slides
        )
    }

    private fun parseSlideXml(slideNumber: Int, xml: String): PptxSlide {
        val paragraphs = mutableListOf<String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var inParagraph = false
            val currentParagraphText = StringBuilder()

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

        val slideTitle = if (paragraphs.isNotEmpty()) paragraphs.first() else "Slide $slideNumber"
        val bullets = if (paragraphs.size > 1) paragraphs.drop(1) else emptyList()

        return PptxSlide(
            slideNumber = slideNumber,
            title = slideTitle,
            bulletPoints = bullets
        )
    }
}
