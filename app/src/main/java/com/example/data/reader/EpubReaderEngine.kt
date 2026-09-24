package com.example.data.reader

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class EpubChapter(
    val id: String,
    val title: String,
    val rawHtml: String,
    val paragraphs: List<String>,
    val wordCount: Int
)

data class EpubBook(
    val title: String,
    val author: String,
    val chapters: List<EpubChapter>
)

class EpubReaderEngine(private val context: Context) {

    suspend fun parseEpub(uri: Uri): EpubBook = withContext(Dispatchers.IO) {
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

        // Find container.xml to locate root OPF
        val containerXmlBytes = zipMap["META-INF/container.xml"]
        var opfPath = "OEBPS/content.opf"
        if (containerXmlBytes != null) {
            val containerXml = String(containerXmlBytes)
            val rootFileMatch = Regex("full-path=\"([^\"]+)\"").find(containerXml)
            if (rootFileMatch != null) {
                opfPath = rootFileMatch.groupValues[1]
            }
        }

        var bookTitle = "Untitled Book"
        var bookAuthor = "Unknown Author"
        val manifest = mutableMapOf<String, String>() // id to href
        val spine = mutableListOf<String>() // idref list

        val opfBytes = zipMap[opfPath] ?: zipMap.entries.firstOrNull { it.key.endsWith(".opf") }?.value
        val opfDir = if (opfPath.contains('/')) opfPath.substringBeforeLast('/') + "/" else ""

        if (opfBytes != null) {
            val opfString = String(opfBytes)
            val titleMatch = Regex("<dc:title[^>]*>([^<]+)</dc:title>").find(opfString)
            if (titleMatch != null) {
                bookTitle = titleMatch.groupValues[1].trim()
            }
            val authorMatch = Regex("<dc:creator[^>]*>([^<]+)</dc:creator>").find(opfString)
            if (authorMatch != null) {
                bookAuthor = authorMatch.groupValues[1].trim()
            }

            // Parse manifest
            val itemRegex = Regex("""<item\s+[^>]*id="([^"]+)"[^>]*href="([^"]+)"|href="([^"]+)"[^>]*id="([^"]+)"""")
            for (match in itemRegex.findAll(opfString)) {
                val id = match.groupValues[1].ifEmpty { match.groupValues[4] }
                val href = match.groupValues[2].ifEmpty { match.groupValues[3] }
                manifest[id] = href
            }

            // Parse spine
            val itemRefRegex = Regex("""<itemref\s+[^>]*idref="([^"]+)"""")
            for (match in itemRefRegex.findAll(opfString)) {
                spine.add(match.groupValues[1])
            }
        }

        val chapters = mutableListOf<EpubChapter>()

        if (spine.isNotEmpty()) {
            for ((index, idref) in spine.withIndex()) {
                val href = manifest[idref] ?: continue
                val fullPath = if (href.startsWith("/")) href.removePrefix("/") else opfDir + href
                val chapterBytes = zipMap[fullPath] ?: zipMap[href]
                if (chapterBytes != null) {
                    val htmlContent = String(chapterBytes)
                    val chapter = parseHtmlChapter(idref, "Chapter ${index + 1}", htmlContent)
                    if (chapter.paragraphs.isNotEmpty() || chapter.rawHtml.isNotBlank()) {
                        chapters.add(chapter)
                    }
                }
            }
        }

        if (chapters.isEmpty()) {
            // Fallback: parse all html/xhtml files in zip
            var index = 1
            for ((path, bytes) in zipMap) {
                if (path.endsWith(".html", ignoreCase = true) || path.endsWith(".xhtml", ignoreCase = true)) {
                    val chapter = parseHtmlChapter(path, "Chapter $index", String(bytes))
                    if (chapter.paragraphs.isNotEmpty() || chapter.rawHtml.isNotBlank()) {
                        chapters.add(chapter)
                        index++
                    }
                }
            }
        }

        EpubBook(
            title = bookTitle,
            author = bookAuthor,
            chapters = chapters
        )
    }

    private fun parseHtmlChapter(id: String, defaultTitle: String, html: String): EpubChapter {
        var title = defaultTitle
        val titleMatch = Regex("<h[1-3][^>]*>([^<]+)</h[1-3]>|<title>([^<]+)</title>").find(html)
        if (titleMatch != null) {
            val candidate = (titleMatch.groupValues[1].ifEmpty { titleMatch.groupValues[2] }).trim()
            if (candidate.isNotEmpty()) {
                title = candidate
            }
        }

        val paragraphs = mutableListOf<String>()

        // Extract paragraphs or block elements for native Compose display
        val pRegex = Regex("<(p|div|li|h1|h2|h3|blockquote)[^>]*>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL)
        val matches = pRegex.findAll(html).toList()

        if (matches.isNotEmpty()) {
            for (match in matches) {
                val rawText = match.groupValues[2]
                val clean = cleanHtmlTags(rawText)
                if (clean.isNotBlank()) {
                    paragraphs.add(clean)
                }
            }
        } else {
            // Fallback: clean all HTML tags and split by double newline
            val cleanAll = cleanHtmlTags(html)
            val lines = cleanAll.split(Regex("\n{2,}"))
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isNotBlank()) {
                    paragraphs.add(trimmed)
                }
            }
        }

        val wordCount = paragraphs.sumOf { p -> p.split(Regex("\\s+")).count { it.isNotBlank() } }

        return EpubChapter(
            id = id,
            title = title,
            rawHtml = html,
            paragraphs = paragraphs,
            wordCount = wordCount
        )
    }

    private fun cleanHtmlTags(raw: String): String {
        return raw.replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()
    }
}
