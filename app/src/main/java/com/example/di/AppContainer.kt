package com.example.di

import android.content.Context
import com.example.data.indexer.DocumentIndexer
import com.example.data.indexer.IndexingManager
import com.example.data.local.LairikDatabase
import com.example.data.local.preferences.AppSettingsDataStore
import com.example.data.ocr.OcrEngine
import com.example.data.repository.DocumentRepository
import com.example.data.repository.DocumentRepositoryImpl
import com.example.data.scanner.StorageDocumentScanner

import com.example.data.ocr.OcrManager
import com.example.data.ocr.OcrProcessor

interface AppContainer {
    val context: Context
    val database: LairikDatabase
    val documentRepository: DocumentRepository
    val appSettingsDataStore: AppSettingsDataStore
    val ocrEngine: OcrEngine
    val ocrProcessor: OcrProcessor
    val ocrManager: OcrManager
    val documentIndexer: DocumentIndexer
    val indexingManager: IndexingManager
}

class DefaultAppContainer(override val context: Context) : AppContainer {
    override val database: LairikDatabase by lazy {
        LairikDatabase.getDatabase(context)
    }

    override val appSettingsDataStore: AppSettingsDataStore by lazy {
        AppSettingsDataStore(context)
    }

    override val ocrEngine: OcrEngine by lazy {
        OcrEngine(context)
    }

    override val ocrProcessor: OcrProcessor by lazy {
        OcrProcessor(
            context = context,
            ocrDao = database.ocrDao(),
            searchDao = database.documentSearchDao()
        )
    }

    override val ocrManager: OcrManager by lazy {
        OcrManager(context)
    }

    private val scanner: StorageDocumentScanner by lazy {
        StorageDocumentScanner(context)
    }

    override val indexingManager: IndexingManager by lazy {
        IndexingManager(context)
    }

    override val documentIndexer: DocumentIndexer by lazy {
        DocumentIndexer(
            context = context,
            contentDao = database.documentContentDao(),
            tocDao = database.documentTocDao(),
            ocrEngine = ocrEngine
        )
    }

    override val documentRepository: DocumentRepository by lazy {
        DocumentRepositoryImpl(
            context = context,
            recentDao = database.recentDocumentDao(),
            bookmarkDao = database.bookmarkDao(),
            progressDao = database.readingProgressDao(),
            contentDao = database.documentContentDao(),
            tocDao = database.documentTocDao(),
            chapterDao = database.chapterDao(),
            searchDao = database.documentSearchDao(),
            ocrDao = database.ocrDao(),
            scanner = scanner,
            indexer = documentIndexer
        )
    }
}

