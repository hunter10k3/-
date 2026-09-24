package com.example.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.data.local.dao.DocumentSearchDao
import com.example.data.local.dao.OcrDao
import com.example.data.local.entity.DocumentTextEntity
import com.example.data.local.entity.FtsDocument
import com.example.data.local.entity.OcrResultEntity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.coroutines.resume

class OcrProcessor(
    private val context: Context,
    private val ocrDao: OcrDao,
    private val searchDao: DocumentSearchDao
) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Processes a PDF document page by page.
     * Detects "image-only" / scanned pages (< 20 characters extracted by PdfBox)
     * and performs ML Kit Text Recognition at 250 DPI.
     */
    suspend fun processPdf(
        uri: Uri,
        fileName: String = "Document",
        forceAllPages: Boolean = false,
        onProgress: (current: Int, total: Int, pageText: String) -> Unit = { _, _, _ -> }
    ): List<OcrResultEntity> = withContext(Dispatchers.IO) {
        val ocrResults = mutableListOf<OcrResultEntity>()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var tempFile: File? = null
        var pdDoc: PDDocument? = null

        try {
            // Open stream and copy to temp file if necessary for PdfRenderer & PdfBox
            tempFile = File(context.cacheDir, "ocr_temp_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext emptyList()

            pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            if (pageCount == 0) return@withContext emptyList()

            // Try loading with PdfBox to measure embedded text per page
            val stripper = PDFTextStripper()
            try {
                pdDoc = PDDocument.load(tempFile)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            for (i in 0 until pageCount) {
                val pageNumber = i + 1
                var pageText = ""
                var isScannedPage = forceAllPages

                if (!forceAllPages && pdDoc != null) {
                    try {
                        stripper.startPage = pageNumber
                        stripper.endPage = pageNumber
                        pageText = stripper.getText(pdDoc).trim()
                        // If extracted text is < 20 chars, mark as image-only / scanned page for OCR
                        if (pageText.length < 20) {
                            isScannedPage = true
                        }
                    } catch (e: Exception) {
                        isScannedPage = true
                    }
                } else if (!forceAllPages) {
                    isScannedPage = true
                }

                if (isScannedPage) {
                    val page = renderer.openPage(i)
                    // Render page to high-res Bitmap (200-300 DPI ~ 2.5x density)
                    val densityMultiplier = 2.5f
                    val width = (page.width * densityMultiplier).toInt().coerceIn(800, 2400)
                    val height = (page.height * densityMultiplier).toInt().coerceIn(1000, 3200)

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val (ocrText, confidence) = recognizeTextWithConfidence(bitmap)
                    bitmap.recycle()

                    if (ocrText.isNotBlank()) {
                        val result = OcrResultEntity(
                            fileUri = uri.toString(),
                            pageNumber = pageNumber,
                            text = ocrText,
                            confidence = confidence,
                            engine = "MLKit Text v2"
                        )
                        ocrResults.add(result)

                        // Save directly to OCR DAO
                        ocrDao.insertOcrResult(result)

                        // Index in DocumentSearch FTS table
                        searchDao.insertDocumentText(
                            DocumentTextEntity(
                                fileUri = uri.toString(),
                                pageNumber = pageNumber,
                                fullText = ocrText,
                                contentHash = "ocr_${pageNumber}_${ocrText.hashCode()}"
                            )
                        )
                        searchDao.insertFtsDocument(
                            FtsDocument(
                                fileUri = uri.toString(),
                                pageNumber = pageNumber,
                                title = "[OCR] $fileName (Page $pageNumber)",
                                body = ocrText
                            )
                        )
                    }
                    onProgress(pageNumber, pageCount, ocrText)
                } else {
                    onProgress(pageNumber, pageCount, pageText)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { pdDoc?.close() } catch (e: Exception) { }
            try { renderer?.close() } catch (e: Exception) { }
            try { pfd?.close() } catch (e: Exception) { }
            try { tempFile?.delete() } catch (e: Exception) { }
        }

        ocrResults
    }

    /**
     * Processes standalone image files (.png, .jpg, .jpeg, .webp) for OCR.
     */
    suspend fun processImage(
        uri: Uri,
        fileName: String = "Image Document"
    ): OcrResultEntity? = withContext(Dispatchers.IO) {
        var bitmap: Bitmap? = null
        try {
            val stream: InputStream? = if (uri.scheme == "file") {
                java.io.FileInputStream(java.io.File(uri.path ?: ""))
            } else {
                context.contentResolver.openInputStream(uri)
            }

            stream?.use { input ->
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                bitmap = BitmapFactory.decodeStream(input, null, options)
            }

            bitmap?.let { bmp ->
                val (ocrText, confidence) = recognizeTextWithConfidence(bmp)
                if (ocrText.isNotBlank()) {
                    val result = OcrResultEntity(
                        fileUri = uri.toString(),
                        pageNumber = 1,
                        text = ocrText,
                        confidence = confidence,
                        engine = "MLKit Text v2"
                    )
                    ocrDao.insertOcrResult(result)
                    searchDao.insertDocumentText(
                        DocumentTextEntity(
                            fileUri = uri.toString(),
                            pageNumber = 1,
                            fullText = ocrText,
                            contentHash = "ocr_img_${ocrText.hashCode()}"
                        )
                    )
                    searchDao.insertFtsDocument(
                        FtsDocument(
                            fileUri = uri.toString(),
                            pageNumber = 1,
                            title = "[OCR] $fileName",
                            body = ocrText
                        )
                    )
                    return@withContext result
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { bitmap?.recycle() } catch (e: Exception) { }
        }
    }

    /**
     * Runs MLKit TextRecognizer on a Bitmap and calculates average confidence from blocks.
     */
    private suspend fun recognizeTextWithConfidence(bitmap: Bitmap): Pair<String, Float> =
        suspendCancellableCoroutine { continuation ->
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val text = visionText.text.trim()
                        if (text.isBlank()) {
                            continuation.resume(Pair("", 0f))
                            return@addOnSuccessListener
                        }

                        // Calculate average confidence from text lines / blocks if available
                        var totalConf = 0f
                        var count = 0
                        for (block in visionText.textBlocks) {
                            for (line in block.lines) {
                                line.confidence?.let { conf ->
                                    totalConf += conf
                                    count++
                                }
                            }
                        }
                        val avgConfidence = if (count > 0) (totalConf / count) else 0.88f
                        continuation.resume(Pair(text, avgConfidence))
                    }
                    .addOnFailureListener { error ->
                        error.printStackTrace()
                        continuation.resume(Pair("", 0f))
                    }
            } catch (e: Throwable) {
                e.printStackTrace()
                continuation.resume(Pair("", 0f))
            }
        }
}
