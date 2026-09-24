package com.example.data.ocr

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.LairikApplication
import com.example.data.model.DocumentFormat
import kotlinx.coroutines.flow.first

class DocumentOcrWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? LairikApplication ?: return Result.failure()
        val repository = app.container.documentRepository
        val ocrDao = app.container.database.ocrDao()
        val searchDao = app.container.database.documentSearchDao()
        val ocrProcessor = OcrProcessor(applicationContext, ocrDao, searchDao)

        val targetUri = inputData.getString(KEY_TARGET_URI)
        val forceAll = inputData.getBoolean(KEY_FORCE_ALL, false)

        try {
            if (targetUri != null) {
                val doc = repository.getDocumentByUri(targetUri)
                val fileName = doc?.title ?: inputData.getString(KEY_FILE_NAME) ?: "Document"
                val uri = Uri.parse(targetUri)

                val format = doc?.format ?: DocumentFormat.fromMimeType(applicationContext.contentResolver.getType(uri) ?: "")
                if (format == DocumentFormat.PDF) {
                    ocrProcessor.processPdf(
                        uri = uri,
                        fileName = fileName,
                        forceAllPages = forceAll
                    ) { current, total, pageText ->
                        setProgressAsync(
                            workDataOf(
                                PROGRESS_CURRENT to current,
                                PROGRESS_TOTAL to total,
                                PROGRESS_FILE_NAME to fileName,
                                PROGRESS_PAGE_TEXT to pageText.take(120)
                            )
                        )
                    }
                } else {
                    // Standalone image or other format
                    ocrProcessor.processImage(uri, fileName)
                    setProgressAsync(
                        workDataOf(
                            PROGRESS_CURRENT to 1,
                            PROGRESS_TOTAL to 1,
                            PROGRESS_FILE_NAME to fileName
                        )
                    )
                }
            } else {
                // OCR all PDF/scanned documents
                val documents = repository.allDocuments.first()
                val pdfDocs = documents.filter { it.format == DocumentFormat.PDF }
                val totalDocs = pdfDocs.size

                for ((docIdx, doc) in pdfDocs.withIndex()) {
                    if (isStopped) return Result.failure()

                    val uri = Uri.parse(doc.uri)
                    ocrProcessor.processPdf(
                        uri = uri,
                        fileName = doc.title,
                        forceAllPages = forceAll
                    ) { current, total, pageText ->
                        setProgressAsync(
                            workDataOf(
                                PROGRESS_CURRENT to (docIdx + 1),
                                PROGRESS_TOTAL to totalDocs,
                                PROGRESS_FILE_NAME to doc.title,
                                PROGRESS_PAGE_TEXT to "Page $current/$total"
                            )
                        )
                    }
                }
            }
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }

    companion object {
        const val WORK_NAME_PREFIX = "document_ocr_work_"
        const val WORK_NAME_ALL = "document_ocr_all_work"
        const val KEY_TARGET_URI = "key_target_uri"
        const val KEY_FILE_NAME = "key_file_name"
        const val KEY_FORCE_ALL = "key_force_all"
        const val PROGRESS_CURRENT = "current"
        const val PROGRESS_TOTAL = "total"
        const val PROGRESS_FILE_NAME = "file_name"
        const val PROGRESS_PAGE_TEXT = "page_text"
    }
}
