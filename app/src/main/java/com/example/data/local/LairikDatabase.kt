package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import com.example.data.local.dao.BookmarkDao
import com.example.data.local.dao.ChapterDao
import com.example.data.local.dao.DocumentContentDao
import com.example.data.local.dao.DocumentSearchDao
import com.example.data.local.dao.DocumentTocDao
import com.example.data.local.dao.OcrDao
import com.example.data.local.dao.ReadingProgressDao
import com.example.data.local.dao.RecentFileDao
import com.example.data.local.entity.Bookmark
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.DocumentContentEntity
import com.example.data.local.entity.DocumentTextEntity
import com.example.data.local.entity.DocumentTocEntity
import com.example.data.local.entity.FtsDocument
import com.example.data.local.entity.OcrResultEntity
import com.example.data.local.entity.ReadingProgress
import com.example.data.local.entity.RecentFile

/**
 * Room Database for Lairik Reader with auto-migration placeholder.
 */
@Database(
    entities = [
        RecentFile::class,
        Bookmark::class,
        ReadingProgress::class,
        DocumentContentEntity::class,
        DocumentTocEntity::class,
        ChapterEntity::class,
        DocumentTextEntity::class,
        FtsDocument::class,
        OcrResultEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class LairikDatabase : RoomDatabase() {
    abstract fun recentFileDao(): RecentFileDao
    fun recentDocumentDao(): RecentFileDao = recentFileDao()
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun documentContentDao(): DocumentContentDao
    abstract fun documentTocDao(): DocumentTocDao
    abstract fun chapterDao(): ChapterDao
    abstract fun documentSearchDao(): DocumentSearchDao
    abstract fun ocrDao(): OcrDao

    // Auto-migration specification placeholder for future schema evolution:
    // class Migration1To2Spec : AutoMigrationSpec

    companion object {
        @Volatile
        private var INSTANCE: LairikDatabase? = null

        fun getDatabase(context: Context): LairikDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LairikDatabase::class.java,
                    "lairik_database"
                ).fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
