package com.example.di

import android.content.Context
import com.example.data.indexer.DocumentIndexer
import com.example.data.local.LairikDatabase
import com.example.data.local.dao.BookmarkDao
import com.example.data.local.dao.ChapterDao
import com.example.data.local.dao.DocumentContentDao
import com.example.data.local.dao.DocumentTocDao
import com.example.data.local.dao.ReadingProgressDao
import com.example.data.local.dao.RecentFileDao
import com.example.data.ocr.OcrEngine
import com.example.data.repository.DocumentRepository
import com.example.data.repository.DocumentRepositoryImpl
import com.example.data.scanner.StorageDocumentScanner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt Dependency Injection Module for Database and DAO bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LairikDatabase {
        return LairikDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideRecentFileDao(database: LairikDatabase): RecentFileDao {
        return database.recentFileDao()
    }

    @Provides
    @Singleton
    fun provideBookmarkDao(database: LairikDatabase): BookmarkDao {
        return database.bookmarkDao()
    }

    @Provides
    @Singleton
    fun provideReadingProgressDao(database: LairikDatabase): ReadingProgressDao {
        return database.readingProgressDao()
    }

    @Provides
    @Singleton
    fun provideDocumentContentDao(database: LairikDatabase): DocumentContentDao {
        return database.documentContentDao()
    }

    @Provides
    @Singleton
    fun provideDocumentTocDao(database: LairikDatabase): DocumentTocDao {
        return database.documentTocDao()
    }

    @Provides
    @Singleton
    fun provideChapterDao(database: LairikDatabase): ChapterDao {
        return database.chapterDao()
    }

    @Provides
    @Singleton
    fun provideOcrDao(database: LairikDatabase): com.example.data.local.dao.OcrDao {
        return database.ocrDao()
    }

    @Provides
    @Singleton
    fun provideDocumentSearchDao(database: LairikDatabase): com.example.data.local.dao.DocumentSearchDao {
        return database.documentSearchDao()
    }

    @Provides
    @Singleton
    fun provideOcrProcessor(
        @ApplicationContext context: Context,
        ocrDao: com.example.data.local.dao.OcrDao,
        searchDao: com.example.data.local.dao.DocumentSearchDao
    ): com.example.data.ocr.OcrProcessor {
        return com.example.data.ocr.OcrProcessor(context, ocrDao, searchDao)
    }

    @Provides
    @Singleton
    fun provideOcrManager(@ApplicationContext context: Context): com.example.data.ocr.OcrManager {
        return com.example.data.ocr.OcrManager(context)
    }

    @Provides
    @Singleton
    fun provideOcrEngine(@ApplicationContext context: Context): OcrEngine {
        return OcrEngine(context)
    }

    @Provides
    @Singleton
    fun provideDocumentIndexer(
        @ApplicationContext context: Context,
        contentDao: DocumentContentDao,
        tocDao: DocumentTocDao,
        ocrEngine: OcrEngine
    ): DocumentIndexer {
        return DocumentIndexer(context, contentDao, tocDao, ocrEngine)
    }

    @Provides
    @Singleton
    fun provideStorageScanner(@ApplicationContext context: Context): StorageDocumentScanner {
        return StorageDocumentScanner(context)
    }

    @Provides
    @Singleton
    fun provideDocumentRepository(
        @ApplicationContext context: Context,
        database: LairikDatabase,
        chapterDao: ChapterDao,
        scanner: StorageDocumentScanner,
        indexer: DocumentIndexer
    ): DocumentRepository {
        return DocumentRepositoryImpl(
            context = context,
            recentDao = database.recentFileDao(),
            bookmarkDao = database.bookmarkDao(),
            progressDao = database.readingProgressDao(),
            contentDao = database.documentContentDao(),
            tocDao = database.documentTocDao(),
            chapterDao = chapterDao,
            searchDao = database.documentSearchDao(),
            ocrDao = database.ocrDao(),
            scanner = scanner,
            indexer = indexer
        )
    }
}
