package com.example.data.indexer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.LairikApplication
import kotlinx.coroutines.flow.first

class DocumentIndexingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? LairikApplication ?: return Result.failure()
        val repository = app.container.documentRepository
        val searchDao = app.container.database.documentSearchDao()
        val indexer = DocumentContentIndexer(applicationContext, searchDao)

        val force = inputData.getBoolean(KEY_FORCE, false)
        val targetUri = inputData.getString(KEY_TARGET_URI)

        try {
            if (targetUri != null) {
                // Index specific document
                val doc = repository.getDocumentByUri(targetUri)
                if (doc != null) {
                    setProgress(workDataOf(PROGRESS_CURRENT to 1, PROGRESS_TOTAL to 1, PROGRESS_FILE_NAME to doc.title))
                    indexer.indexDocument(doc, force = true)
                }
            } else {
                // Index all documents
                val documents = repository.allDocuments.first()
                val total = documents.size

                setProgress(workDataOf(PROGRESS_CURRENT to 0, PROGRESS_TOTAL to total, PROGRESS_FILE_NAME to ""))

                for ((index, doc) in documents.withIndex()) {
                    if (isStopped) return Result.failure()

                    setProgress(
                        workDataOf(
                            PROGRESS_CURRENT to (index + 1),
                            PROGRESS_TOTAL to total,
                            PROGRESS_FILE_NAME to doc.title
                        )
                    )

                    try {
                        indexer.indexDocument(doc, force = force)
                    } catch (e: Exception) {
                        e.printStackTrace()
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
        const val WORK_NAME = "document_indexing_work"
        const val KEY_FORCE = "key_force"
        const val KEY_TARGET_URI = "key_target_uri"
        const val PROGRESS_CURRENT = "current"
        const val PROGRESS_TOTAL = "total"
        const val PROGRESS_FILE_NAME = "file_name"
    }
}
