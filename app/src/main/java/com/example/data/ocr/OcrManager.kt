package com.example.data.ocr

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class OcrProgress(
    val isRunning: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val fileName: String = "",
    val targetUri: String? = null,
    val pageDetail: String = ""
) {
    val progressPercent: Float
        get() = if (total > 0) (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
}

class OcrManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val workManager = WorkManager.getInstance(context)

    private val _progress = MutableStateFlow(OcrProgress())
    val progress: StateFlow<OcrProgress> = _progress.asStateFlow()

    init {
        observeWorkerStatus()
    }

    private fun observeWorkerStatus() {
        scope.launch {
            try {
                workManager.getWorkInfosByTagFlow(TAG_OCR_WORK)
                    .collectLatest { workInfos ->
                        val activeWork = workInfos.firstOrNull {
                            it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                        }

                        if (activeWork != null) {
                            val current = activeWork.progress.getInt(DocumentOcrWorker.PROGRESS_CURRENT, 0)
                            val total = activeWork.progress.getInt(DocumentOcrWorker.PROGRESS_TOTAL, 0)
                            val fileName = activeWork.progress.getString(DocumentOcrWorker.PROGRESS_FILE_NAME) ?: ""
                            val pageDetail = activeWork.progress.getString(DocumentOcrWorker.PROGRESS_PAGE_TEXT) ?: ""

                            _progress.value = OcrProgress(
                                isRunning = true,
                                current = current,
                                total = total,
                                fileName = fileName,
                                pageDetail = pageDetail
                            )
                        } else {
                            _progress.value = OcrProgress(isRunning = false)
                        }
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startOcrForDocument(uri: String, fileName: String, forceAll: Boolean = false) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val inputData = Data.Builder()
            .putString(DocumentOcrWorker.KEY_TARGET_URI, uri)
            .putString(DocumentOcrWorker.KEY_FILE_NAME, fileName)
            .putBoolean(DocumentOcrWorker.KEY_FORCE_ALL, forceAll)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DocumentOcrWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10_000, TimeUnit.MILLISECONDS)
            .addTag(TAG_OCR_WORK)
            .addTag("ocr_${uri.hashCode()}")
            .build()

        workManager.enqueueUniqueWork(
            "${DocumentOcrWorker.WORK_NAME_PREFIX}${uri.hashCode()}",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun startOcrForAllDocuments(forceAll: Boolean = false) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val inputData = Data.Builder()
            .putBoolean(DocumentOcrWorker.KEY_FORCE_ALL, forceAll)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DocumentOcrWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10_000, TimeUnit.MILLISECONDS)
            .addTag(TAG_OCR_WORK)
            .build()

        workManager.enqueueUniqueWork(
            DocumentOcrWorker.WORK_NAME_ALL,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun cancelOcrForDocument(uri: String) {
        workManager.cancelUniqueWork("${DocumentOcrWorker.WORK_NAME_PREFIX}${uri.hashCode()}")
    }

    fun cancelAllOcr() {
        workManager.cancelAllWorkByTag(TAG_OCR_WORK)
        workManager.cancelUniqueWork(DocumentOcrWorker.WORK_NAME_ALL)
    }

    companion object {
        const val TAG_OCR_WORK = "tag_ocr_document_work"
    }
}
