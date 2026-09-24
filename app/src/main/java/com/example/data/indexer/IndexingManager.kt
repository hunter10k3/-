package com.example.data.indexer

import android.content.Context
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

data class IndexingProgress(
    val isIndexing: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val currentFileName: String = ""
) {
    val progressPercent: Float
        get() = if (total > 0) (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
}

class IndexingManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val workManager = WorkManager.getInstance(context)

    private val _progress = MutableStateFlow(IndexingProgress())
    val progress: StateFlow<IndexingProgress> = _progress.asStateFlow()

    init {
        observeWorkerStatus()
    }

    private fun observeWorkerStatus() {
        scope.launch {
            try {
                workManager.getWorkInfosForUniqueWorkFlow(DocumentIndexingWorker.WORK_NAME)
                    .collectLatest { workInfos ->
                        val workInfo = workInfos.firstOrNull()
                        if (workInfo != null) {
                            val isRunning = workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED
                            val current = workInfo.progress.getInt(DocumentIndexingWorker.PROGRESS_CURRENT, 0)
                            val total = workInfo.progress.getInt(DocumentIndexingWorker.PROGRESS_TOTAL, 0)
                            val fileName = workInfo.progress.getString(DocumentIndexingWorker.PROGRESS_FILE_NAME) ?: ""

                            _progress.value = IndexingProgress(
                                isIndexing = isRunning,
                                current = current,
                                total = total,
                                currentFileName = fileName
                            )
                        } else {
                            _progress.value = IndexingProgress(isIndexing = false)
                        }
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun triggerIndexing(force: Boolean = false) {
        val inputData = Data.Builder()
            .putBoolean(DocumentIndexingWorker.KEY_FORCE, force)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DocumentIndexingWorker>()
            .setInputData(inputData)
            .build()

        workManager.enqueueUniqueWork(
            DocumentIndexingWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun indexSingleDocument(uri: String) {
        val inputData = Data.Builder()
            .putString(DocumentIndexingWorker.KEY_TARGET_URI, uri)
            .putBoolean(DocumentIndexingWorker.KEY_FORCE, true)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DocumentIndexingWorker>()
            .setInputData(inputData)
            .build()

        workManager.enqueue(workRequest)
    }

    fun cancel() {
        workManager.cancelUniqueWork(DocumentIndexingWorker.WORK_NAME)
    }
}
