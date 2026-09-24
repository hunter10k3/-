package com.example.data.scanner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SampleDocumentGenerator {

    suspend fun generateSampleLibrary(context: Context): List<File> = withContext(Dispatchers.IO) {
        val sampleDir = File(context.filesDir, "sample_documents").apply { mkdirs() }
        val generatedFiles = mutableListOf<File>()

        try {
            // 1. Generate Sample PDF
            val pdfFile = File(sampleDir, "ꯂꯥꯏꯔꯤꯛ_Introduction_to_Manipuri_Literature.pdf")
            if (!pdfFile.exists() || pdfFile.length() == 0L) {
                try {
                    generateSamplePdf(pdfFile)
                } catch (e: Throwable) {
                    writeFallbackPdf(pdfFile)
                }
            }
            generatedFiles.add(pdfFile)

            // 2. Generate Sample EPUB
            val epubFile = File(sampleDir, "The_Art_of_Reading_Lairik.epub")
            if (!epubFile.exists() || epubFile.length() == 0L) {
                generateSampleEpub(epubFile)
            }
            generatedFiles.add(epubFile)

            // 3. Generate Sample DOCX
            val docxFile = File(sampleDir, "Lairik_Reader_Architecture_Notes.docx")
            if (!docxFile.exists() || docxFile.length() == 0L) {
                generateSampleDocx(docxFile)
            }
            generatedFiles.add(docxFile)

            // 4. Generate Sample PPTX
            val pptxFile = File(sampleDir, "Digital_Document_Reader_Overview.pptx")
            if (!pptxFile.exists() || pptxFile.length() == 0L) {
                generateSamplePptx(pptxFile)
            }
            generatedFiles.add(pptxFile)

            // 5. Generate Sample TXT
            val txtFile = File(sampleDir, "Meetei_Mayek_and_Heritage.txt")
            if (!txtFile.exists() || txtFile.length() == 0L) {
                generateSampleTxt(txtFile)
            }
            generatedFiles.add(txtFile)

        } catch (e: Exception) {
            e.printStackTrace()
        }

        generatedFiles
    }

    private fun generateSamplePdf(destination: File) {
        val document = PdfDocument()
        val paint = Paint().apply { isAntiAlias = true }
        val titlePaint = Paint().apply {
            isAntiAlias = true
            textSize = 20f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            color = Color.rgb(25, 45, 100)
        }
        val headerPaint = Paint().apply {
            isAntiAlias = true
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(180, 80, 20)
        }
        val bodyPaint = Paint().apply {
            isAntiAlias = true
            textSize = 12f
            typeface = Typeface.SERIF
            color = Color.rgb(40, 40, 40)
        }
        val subTextPaint = Paint().apply {
            isAntiAlias = true
            textSize = 10f
            color = Color.GRAY
        }

        val pageWidth = 595
        val pageHeight = 842

        // Page 1: Cover & Intro
        run {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            // Background subtle banner
            paint.color = Color.rgb(240, 244, 255)
            canvas.drawRect(30f, 40f, (pageWidth - 30).toFloat(), 180f, paint)

            paint.color = Color.rgb(30, 58, 138)
            canvas.drawRect(30f, 40f, 38f, 180f, paint)

            canvas.drawText("ꯂꯥꯏꯔꯤꯛ (Lairik) Document Reader", 50f, 85f, titlePaint)
            canvas.drawText("Volume 1: An Overview of Manipuri Manuscripts & Literature", 50f, 115f, headerPaint)
            canvas.drawText("Published for ꯂꯥꯏꯔꯤꯛ Native Android Reader", 50f, 140f, subTextPaint)

            var y = 220f
            val paragraphs = listOf(
                "Welcome to ꯂꯥꯏꯔꯤꯛ, a high-performance native Android document reader built in Kotlin and Jetpack Compose.",
                "In Manipuri (Meetei / Meitei culture), 'ꯂꯥꯏꯔꯤꯛ' (pronounced Lairik) translates to 'Book' or 'Scripture'. The historical manuscripts inscribed on agarwood bark (Aquilaria agallocha) are traditionally known as 'Puya'. These ancient texts cover philosophy, astronomy, law, mythology, and medicine.",
                "Today, reading encompasses digital documents in diverse formats across internal device storage: PDF, EPUB, DOCX, PPTX, and TXT.",
                "This document demonstrates native PDF rendering with page-by-page viewing, zooming, bookmarking, and reading progress tracking."
            )

            for (p in paragraphs) {
                val lines = breakTextIntoLines(p, bodyPaint, (pageWidth - 100).toFloat())
                for (line in lines) {
                    canvas.drawText(line, 50f, y, bodyPaint)
                    y += 18f
                }
                y += 12f
            }

            // Footer
            canvas.drawLine(50f, (pageHeight - 60).toFloat(), (pageWidth - 50).toFloat(), (pageHeight - 60).toFloat(), subTextPaint)
            canvas.drawText("ꯂꯥꯏꯔꯤꯛ Library • Page 1 of 3", 50f, (pageHeight - 40).toFloat(), subTextPaint)

            document.finishPage(page)
        }

        // Page 2: Meetei Mayek & Typography
        run {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawText("Chapter II: The Meetei Mayek Script & Heritage", 50f, 70f, titlePaint)
            canvas.drawLine(50f, 85f, (pageWidth - 50).toFloat(), 85f, headerPaint)

            var y = 120f
            val section2 = listOf(
                "Meetei Mayek is an ancient writing system with a unique anatomical origin.",
                "Unlike most world scripts whose letters represent abstract phonetic symbols, each of the primary 18 letters (Iyek Ipi) in Meetei Mayek represents a part of the human body:",
                "• ꯀ (Kok) — Head",
                "• ꯁ (Sam) — Hair",
                "• ꯂ (Lai) — Forehead",
                "• ꯃ (Mit) — Eye",
                "• ꯄ (Pa) — Eyelash / Lip",
                "• ꯅ (Na) — Ear",
                "• ꯆ (Chil) — Lips / Mouth",
                "• ꯇ (Til) — Saliva",
                "• ꯈ (Khou) — Throat",
                "• ꯉ (Ngou) — Uvula",
                "• ꯊ (Thou) — Chest / Breast",
                "• ꯋ (Wai) — Navel",
                "This anatomical symbolism is a poetic testament to the philosophy that language and writing are organic extensions of the human being."
            )

            for (p in section2) {
                val lines = breakTextIntoLines(p, bodyPaint, (pageWidth - 100).toFloat())
                for (line in lines) {
                    canvas.drawText(line, 50f, y, bodyPaint)
                    y += 18f
                }
                y += 8f
            }

            canvas.drawLine(50f, (pageHeight - 60).toFloat(), (pageWidth - 50).toFloat(), (pageHeight - 60).toFloat(), subTextPaint)
            canvas.drawText("ꯂꯥꯏꯔꯤꯛ Library • Page 2 of 3", 50f, (pageHeight - 40).toFloat(), subTextPaint)

            document.finishPage(page)
        }

        // Page 3: Reader Features & Conclusion
        run {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 3).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawText("Chapter III: Native Document Architecture", 50f, 70f, titlePaint)
            canvas.drawLine(50f, 85f, (pageWidth - 50).toFloat(), 85f, headerPaint)

            var y = 120f
            val section3 = listOf(
                "Key capabilities in ꯂꯥꯏꯔꯤꯛ:",
                "1. Storage Scanning: Seamlessly scans internal storage with MediaStore and Storage Access Framework (SAF).",
                "2. Multi-Format Native Engine: Reads PDF, EPUB, DOCX, PPTX, and TXT files smoothly without heavy external SDKs.",
                "3. Persistence with Room: Remembers recent files, last reading position, and user bookmarks automatically.",
                "4. Reading Themes: Switch between Light, Warm Sepia, Eye-care Sage, Dark, and AMOLED OLED Black themes.",
                "5. Privacy First: All document indexing and rendering occurs 100% on-device.",
                "Thank you for exploring ꯂꯥꯏꯔꯤꯛ. Happy reading!"
            )

            for (p in section3) {
                val lines = breakTextIntoLines(p, bodyPaint, (pageWidth - 100).toFloat())
                for (line in lines) {
                    canvas.drawText(line, 50f, y, bodyPaint)
                    y += 18f
                }
                y += 10f
            }

            canvas.drawLine(50f, (pageHeight - 60).toFloat(), (pageWidth - 50).toFloat(), (pageHeight - 60).toFloat(), subTextPaint)
            canvas.drawText("ꯂꯥꯏꯔꯤꯛ Library • Page 3 of 3 • End of Document", 50f, (pageHeight - 40).toFloat(), subTextPaint)

            document.finishPage(page)
        }

        try {
            FileOutputStream(destination).use { out ->
                document.writeTo(out)
            }
            document.close()
        } catch (e: Throwable) {
            document.close()
            writeFallbackPdf(destination)
        }
    }

    private fun writeFallbackPdf(destination: File) {
        val content = """%PDF-1.4
1 0 obj
<< /Type /Catalog /Pages 2 0 R >>
endobj
2 0 obj
<< /Type /Pages /Kids [3 0 R] /Count 1 >>
endobj
3 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>
endobj
4 0 obj
<< /Length 80 >>
stream
BT
/F1 24 Tf
50 700 Td
(Lairik Document Reader - Sample Book) Tj
ET
endstream
endobj
5 0 obj
<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>
endobj
xref
0 6
0000000000 65535 f 
0000000009 00000 n 
0000000058 00000 n 
0000000115 00000 n 
0000000230 00000 n 
0000000360 00000 n 
trailer
<< /Size 6 /Root 1 0 R >>
startxref
435
%%EOF"""
        destination.writeText(content, StandardCharsets.UTF_8)
    }

    private fun generateSampleEpub(destination: File) {
        ZipOutputStream(FileOutputStream(destination)).use { zos ->
            // mimetype must be first and uncompressed in standard EPUB, but standard zip is accepted by all readers
            zos.putNextEntry(ZipEntry("mimetype"))
            zos.write("application/epub+zip".toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            // container.xml
            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            val containerXml = """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""
            zos.write(containerXml.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            // content.opf
            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookID" version="2.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>The Art of Reading Lairik</dc:title>
    <dc:creator>ꯂꯥꯏꯔꯤꯛ Literary Circle</dc:creator>
    <dc:language>en</dc:language>
  </metadata>
  <manifest>
    <item id="chapter1" href="chapter1.html" media-type="application/xhtml+xml"/>
    <item id="chapter2" href="chapter2.html" media-type="application/xhtml+xml"/>
    <item id="chapter3" href="chapter3.html" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="chapter1"/>
    <itemref idref="chapter2"/>
    <itemref idref="chapter3"/>
  </spine>
</package>"""
            zos.write(opf.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            // Chapter 1
            zos.putNextEntry(ZipEntry("OEBPS/chapter1.html"))
            val ch1 = """<!DOCTYPE html>
<html>
<head><title>Chapter 1: The Sanctuary of Pages</title></head>
<body>
  <h1>Chapter 1: The Sanctuary of Pages</h1>
  <p>Reading is a sanctuary. In an era where information flickers across screens in fractions of a second, the act of deliberate reading remains an irreplaceable mental refuge.</p>
  <p>When you hold a book or open a digital manuscript in ꯂꯥꯏꯔꯤꯛ, you create a private dialogue between yourself and the thinker across time and geography.</p>
  <p>Whether analyzing complex scientific treaties or immersing in lyrical epics like the Khamba Thoibi of Moirang, the mind enters a state of focused immersion known as deep work.</p>
</body>
</html>"""
            zos.write(ch1.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            // Chapter 2
            zos.putNextEntry(ZipEntry("OEBPS/chapter2.html"))
            val ch2 = """<!DOCTYPE html>
<html>
<head><title>Chapter 2: The Epic of Khamba and Thoibi</title></head>
<body>
  <h1>Chapter 2: The Epic of Khamba and Thoibi</h1>
  <p>The tale of Khamba and Princess Thoibi is celebrated as the crowning jewel of Manipuri romantic epics.</p>
  <p>Set in the ancient principality of Moirang on the shores of the majestic Loktak Lake, this legendary saga chronicles love, heroic trials, archery contests, tiger hunts, and enduring loyalty.</p>
  <p>Bards known as Pena performers have sung this epic for centuries, accompanied by the resonant melody of the traditional one-stringed Pena instrument.</p>
</body>
</html>"""
            zos.write(ch2.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            // Chapter 3
            zos.putNextEntry(ZipEntry("OEBPS/chapter3.html"))
            val ch3 = """<!DOCTYPE html>
<html>
<head><title>Chapter 3: The Future of Digital Reading</title></head>
<body>
  <h1>Chapter 3: The Future of Digital Reading</h1>
  <p>Modern mobile devices enable personal libraries containing tens of thousands of works in your pocket.</p>
  <p>ꯂꯥꯏꯔꯤꯛ is designed to preserve the elegance of paper while harnessing instant search, reading progress persistence, customizable typography, and distraction-free layout.</p>
  <p>May your reading journey be rich and enlightening.</p>
</body>
</html>"""
            zos.write(ch3.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }
    }

    private fun generateSampleDocx(destination: File) {
        ZipOutputStream(FileOutputStream(destination)).use { zos ->
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""
            zos.write(contentTypes.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("_rels/.rels"))
            val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
            zos.write(rels.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("word/document.xml"))
            val docXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    <w:p>
      <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
      <w:r><w:t>ꯂꯥꯏꯔꯤꯛ Document Reader Architecture</w:t></w:r>
    </w:p>
    <w:p>
      <w:r><w:t>Document Version: 2.0 | Architecture Specification</w:t></w:r>
    </w:p>
    <w:p>
      <w:pPr><w:pStyle w:val="Heading2"/></w:pPr>
      <w:r><w:t>1. Storage Access &amp; Scanning Layer</w:t></w:r>
    </w:p>
    <w:p>
      <w:r><w:t>The application queries MediaStore.Files for all document MIME types and common document file extensions (PDF, DOC/DOCX, PPT/PPTX, EPUB, TXT). It additionally interfaces with Android's Storage Access Framework (SAF) to let users select custom directories and persist tree permissions.</w:t></w:r>
    </w:p>
    <w:p>
      <w:pPr><w:pStyle w:val="Heading2"/></w:pPr>
      <w:r><w:t>2. Clean MVVM with Repository Pattern</w:t></w:r>
    </w:p>
    <w:p>
      <w:r><w:t>State flows reactively from Room DAOs through DocumentRepository into ViewModel StateFlows, which Compose screens observe using collectAsStateWithLifecycle().</w:t></w:r>
    </w:p>
    <w:p>
      <w:pPr><w:pStyle w:val="Heading2"/></w:pPr>
      <w:r><w:t>3. Native Document Parsers</w:t></w:r>
    </w:p>
    <w:p>
      <w:r><w:t>• PDF: Native PdfRenderer with hardware bitmap rendering and page caching.</w:t></w:r>
    </w:p>
    <w:p>
      <w:r><w:t>• DOCX / PPTX: OpenXML unzipping and XmlPullParser stream processing.</w:t></w:r>
    </w:p>
    <w:p>
      <w:r><w:t>• EPUB: Container and OPF spine parsing with structured chapter flow.</w:t></w:r>
    </w:p>
    <w:p>
      <w:r><w:t>• TXT: Fast memory-mapped UTF-8 reading with instant word counting and search.</w:t></w:r>
    </w:p>
  </w:body>
</w:document>"""
            zos.write(docXml.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }
    }

    private fun generateSamplePptx(destination: File) {
        ZipOutputStream(FileOutputStream(destination)).use { zos ->
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
  <Override PartName="/ppt/slides/slide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
  <Override PartName="/ppt/slides/slide2.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
  <Override PartName="/ppt/slides/slide3.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
</Types>"""
            zos.write(contentTypes.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            // Slide 1
            zos.putNextEntry(ZipEntry("ppt/slides/slide1.xml"))
            val s1 = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:spTree>
      <p:sp>
        <p:txBody>
          <a:p><a:r><a:t>ꯂꯥꯏꯔꯤꯛ: Universal Document Reader</a:t></a:r></a:p>
          <a:p><a:r><a:t>Next-Generation Android Document Management</a:t></a:r></a:p>
          <a:p><a:r><a:t>Presented by the Lairik Engineering Team</a:t></a:r></a:p>
        </p:txBody>
      </p:sp>
    </p:spTree>
  </p:cSld>
</p:sld>"""
            zos.write(s1.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            // Slide 2
            zos.putNextEntry(ZipEntry("ppt/slides/slide2.xml"))
            val s2 = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:spTree>
      <p:sp>
        <p:txBody>
          <a:p><a:r><a:t>Supported Formats &amp; Core Strengths</a:t></a:r></a:p>
          <a:p><a:r><a:t>• PDF: Standard fixed-layout documents with pinch-to-zoom</a:t></a:r></a:p>
          <a:p><a:r><a:t>• PPT / PPTX: Presentation slides and executive summaries</a:t></a:r></a:p>
          <a:p><a:r><a:t>• EPUB: Reflowable ebooks with chapter navigation</a:t></a:r></a:p>
          <a:p><a:r><a:t>• DOC / DOCX: Word documents and technical reports</a:t></a:r></a:p>
          <a:p><a:r><a:t>• TXT: Clean distraction-free plain text reading</a:t></a:r></a:p>
        </p:txBody>
      </p:sp>
    </p:spTree>
  </p:cSld>
</p:sld>"""
            zos.write(s2.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            // Slide 3
            zos.putNextEntry(ZipEntry("ppt/slides/slide3.xml"))
            val s3 = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:spTree>
      <p:sp>
        <p:txBody>
          <a:p><a:r><a:t>User Experience Highlights</a:t></a:r></a:p>
          <a:p><a:r><a:t>1. Automatic Storage Scanning: Instant detection across internal storage</a:t></a:r></a:p>
          <a:p><a:r><a:t>2. Reading Themes: Light, Sepia, Sage, Dark, and AMOLED</a:t></a:r></a:p>
          <a:p><a:r><a:t>3. Bookmarking &amp; Progress: Pick up exactly where you left off</a:t></a:r></a:p>
          <a:p><a:r><a:t>4. 100% Offline &amp; Privacy-Respecting</a:t></a:r></a:p>
        </p:txBody>
      </p:sp>
    </p:spTree>
  </p:cSld>
</p:sld>"""
            zos.write(s3.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }
    }

    private fun generateSampleTxt(destination: File) {
        val content = """ꯂꯥꯏꯔꯤꯛ (Lairik): Meetei Mayek & Cultural Heritage
===================================================

The state of Manipur in northeastern India boasts one of the richest literary, linguistic, and cultural traditions in Asia.

1. The Ancient Puyas
The traditional literature of Manipur was inscribed by Maichous (scholars and philosophers) on agarwood bark paper (Lairik / Puyas) using indigenous ink made from lamp soot and herbal extracts. These Puyas documented:
- Puyas of cosmology: 'Leithak Leikharon' (Chronicle of Heaven and Earth)
- History and chronicles: 'Cheitharol Kumbaba' (The Royal Chronicle dating back to 33 AD)
- Philosophy and law: 'Washak Lairik'

2. The Meetei Mayek Alphabet
Revitalized in modern educational curricula, Meetei Mayek comprises 18 core characters:
Kok (ꯀ), Sam (ꯁ), Lai (ꯂ), Mit (ꯃ), Pa (ꯄ), Na (ꯅ),
Chil (ꯆ), Til (ꯇ), Khou (ꯈ), Ngou (ꯉ), Thou (ꯊ), Wai (ꯋ),
Yang (ꯌ), Huk (ꯍ), Un (ꯎ), I (ꯏ), Pham (ꯐ), Atiya (ꯑ).

3. The Loktak Lake and Keibul Lamjao
Manipur is also home to Loktak Lake, the largest freshwater lake in Northeast India, famous for its floating phumdis (heterogeneous mass of vegetation, soil, and organic matter) and the endangered Sangai (Cervus eldi eldi) brow-antlered deer at Keibul Lamjao National Park — the world's only floating national park.

4. Reading with ꯂꯥꯏꯔꯤꯛ
This TXT document showcases the text reader engine with full font-size customization, sepia/dark themes, instant search with highlighting, and reading position remembrance.
"""
        destination.writeText(content, StandardCharsets.UTF_8)
    }

    private fun breakTextIntoLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        val currentLine = StringBuilder()

        for (word in words) {
            val potentialLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(potentialLine) <= maxWidth) {
                currentLine.setLength(0)
                currentLine.append(potentialLine)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
                currentLine.setLength(0)
                currentLine.append(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines
    }
}
