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

class EpubChapterExtractor(private val context: Context) {

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

        // Locate OPF package
        val containerXml = zipMap["META-INF/container.xml"]?.let { String(it) } ?: ""
        val opfPath = Regex("full-path=\"([^\"]+)\"").find(containerXml)?.groupValues?.get(1)
            ?: zipMap.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
            ?: "OEBPS/content.opf"

        val opfDir = if (opfPath.contains('/')) opfPath.substringBeforeLast('/') + "/" else ""
        val opfContent = zipMap[opfPath]?.let { String(it) } ?: ""

        // Map manifest items (id -> href, media-type, properties)
        val manifestHrefToId = mutableMapOf<String, String>()
        val manifestIdToHref = mutableMapOf<String, String>()
        val itemRegex = Regex("""<item\s+[^>]*id="([^"]+)"[^>]*href="([^"]+)"[^>]*properties="([^"]*)"""", RegexOption.IGNORE_CASE)
        val fallbackItemRegex = Regex("""<item\s+[^>]*href="([^"]+)"[^>]*id="([^"]+)"""", RegexOption.IGNORE_CASE)

        var navPath: String? = null
        var ncxPath: String? = null

        for (match in itemRegex.findAll(opfContent)) {
            val id = match.groupValues[1]
            val href = match.groupValues[2]
            val props = match.groupValues[3]
            manifestHrefToId[href] = id
            manifestIdToHref[id] = href
            if (props.contains("nav", ignoreCase = true)) {
                navPath = opfDir + href
            }
        }

        for (match in fallbackItemRegex.findAll(opfContent)) {
            val href = match.groupValues[1]
            val id = match.groupValues[2]
            manifestHrefToId[href] = id
            manifestIdToHref[id] = href
        }

        // Spine order
        val spineIdRefs = mutableListOf<String>()
        val itemRefRegex = Regex("""<itemref\s+[^>]*idref="([^"]+)"""", RegexOption.IGNORE_CASE)
        for (match in itemRefRegex.findAll(opfContent)) {
            spineIdRefs.add(match.groupValues[1])
        }

        // Calculate chapter character offsets based on spine items
        val chapterCharOffsets = mutableMapOf<String, Long>()
        var cumulativeOffset = 0L
        for (idref in spineIdRefs) {
            val href = manifestIdToHref[idref] ?: continue
            val fullPath = if (href.startsWith("/")) href.removePrefix("/") else opfDir + href
            val bytes = zipMap[fullPath] ?: zipMap[href] ?: continue
            val textLength = cleanHtml(String(bytes)).length.toLong()
            chapterCharOffsets[href] = cumulativeOffset
            chapterCharOffsets[href.substringAfterLast('/')] = cumulativeOffset
            cumulativeOffset += textLength
        }

        // 1. Try EPUB 3 Navigation Document (nav.xhtml / nav.html)
        if (navPath == null) {
            navPath = zipMap.keys.firstOrNull {
                it.endsWith("nav.xhtml", ignoreCase = true) ||
                it.endsWith("nav.html", ignoreCase = true) ||
                it.endsWith("toc.xhtml", ignoreCase = true)
            }
        }

        if (navPath != null && zipMap.containsKey(navPath)) {
            val navHtml = String(zipMap[navPath]!!)
            val navChapters = parseNavXhtml(navHtml, chapterCharOffsets)
            if (navChapters.isNotEmpty()) {
                return@withContext navChapters
            }
        }

        // 2. Try EPUB 2 NCX (toc.ncx)
        if (ncxPath == null) {
            ncxPath = zipMap.keys.firstOrNull { it.endsWith("toc.ncx", ignoreCase = true) }
        }

        if (ncxPath != null && zipMap.containsKey(ncxPath)) {
            val ncxXml = String(zipMap[ncxPath]!!)
            val ncxChapters = parseTocNcx(ncxXml, chapterCharOffsets)
            if (ncxChapters.isNotEmpty()) {
                return@withContext ncxChapters
            }
        }

        // 3. Fallback: Parse spine items HTML headings
        val fallbackChapters = mutableListOf<Chapter>()
        var fallbackOffset = 0L
        for ((index, idref) in spineIdRefs.withIndex()) {
            val href = manifestIdToHref[idref] ?: continue
            val fullPath = if (href.startsWith("/")) href.removePrefix("/") else opfDir + href
            val bytes = zipMap[fullPath] ?: zipMap[href] ?: continue
            val html = String(bytes)

            val heading = Regex("<h[1-3][^>]*>([^<]+)</h[1-3]>|<title>([^<]+)</title>", RegexOption.IGNORE_CASE).find(html)
            val title = heading?.groupValues?.get(1)?.ifBlank { heading.groupValues.getOrNull(2) }?.trim()
                ?: "Chapter ${index + 1}"

            val textLength = cleanHtml(html).length.toLong()
            fallbackChapters.add(
                Chapter(
                    title = title,
                    level = 1,
                    startPage = index + 1,
                    startOffset = fallbackOffset,
                    startTimeMs = null
                )
            )
            fallbackOffset += textLength
        }

        fallbackChapters
    }

    private fun parseNavXhtml(html: String, chapterOffsets: Map<String, Long>): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        try {
            // Find <nav epub:type="toc"> or <nav role="doc-toc"> or fallback <nav>
            val navSectionMatch = Regex("<nav[^>]*(epub:type=[\"']toc[\"']|role=[\"']doc-toc[\"'])[^>]*>([\\s\\S]*?)</nav>", RegexOption.IGNORE_CASE).find(html)
            val navContent = navSectionMatch?.groupValues?.get(2) ?: html

            val liRegex = Regex("<li[^>]*>([\\s\\S]*?)</li>", RegexOption.IGNORE_CASE)
            val aRegex = Regex("<a\\s+[^>]*href=[\"']([^\"']*)[\"'][^>]*>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE)

            var pageCounter = 1
            for (match in liRegex.findAll(navContent)) {
                val liBody = match.groupValues[1]
                val aMatch = aRegex.find(liBody)
                val title = if (aMatch != null) cleanHtml(aMatch.groupValues[2]) else cleanHtml(liBody).take(60)
                val href = aMatch?.groupValues?.get(1)?.substringBefore('#') ?: ""
                val cleanHref = href.substringAfterLast('/')

                val offset = chapterOffsets[cleanHref] ?: chapterOffsets[href]

                if (title.isNotBlank()) {
                    chapters.add(
                        Chapter(
                            title = title,
                            level = 1,
                            startPage = pageCounter,
                            startOffset = offset,
                            startTimeMs = null
                        )
                    )
                    pageCounter++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return chapters
    }

    private fun parseTocNcx(ncxXml: String, chapterOffsets: Map<String, Long>): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(ncxXml))

            var eventType = parser.eventType
            var currentLevel = 0
            var currentTitle = ""
            var currentSrc = ""
            var inNavLabel = false
            var inText = false
            var pageCounter = 1

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (name.lowercase()) {
                            "navpoint" -> {
                                currentLevel++
                                currentTitle = ""
                                currentSrc = ""
                            }
                            "navlabel" -> inNavLabel = true
                            "text" -> {
                                if (inNavLabel) inText = true
                            }
                            "content" -> {
                                val src = parser.getAttributeValue(null, "src") ?: ""
                                currentSrc = src.substringBefore('#').substringAfterLast('/')
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inText) {
                            currentTitle += parser.text.orEmpty()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (name.lowercase()) {
                            "text" -> inText = false
                            "navlabel" -> inNavLabel = false
                            "navpoint" -> {
                                if (currentTitle.isNotBlank()) {
                                    val offset = chapterOffsets[currentSrc]
                                    chapters.add(
                                        Chapter(
                                            title = currentTitle.trim(),
                                            level = currentLevel.coerceAtLeast(1),
                                            startPage = pageCounter++,
                                            startOffset = offset,
                                            startTimeMs = null
                                        )
                                    )
                                    currentTitle = ""
                                }
                                currentLevel = (currentLevel - 1).coerceAtLeast(0)
                            }
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

    private fun cleanHtml(raw: String): String {
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
