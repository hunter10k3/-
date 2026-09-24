package com.example.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ReadingTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "lairik_settings_prefs")

data class AppSettings(
    val defaultReaderTheme: ReadingTheme = ReadingTheme.LIGHT,
    val defaultFontSize: Float = 18f,
    val autoScanOnLaunch: Boolean = true,
    val ocrScannedDocumentsEnabled: Boolean = true,
    val scanFoldersIncluded: Set<String> = setOf("Download", "Documents", "Books"),
    val scanFoldersExcluded: Set<String> = setOf("Android/data", ".thumbnails", "WhatsApp")
)

class AppSettingsDataStore(private val context: Context) {

    private object PreferencesKeys {
        val DEFAULT_READER_THEME = stringPreferencesKey("default_reader_theme")
        val DEFAULT_FONT_SIZE = floatPreferencesKey("default_font_size")
        val AUTO_SCAN_ON_LAUNCH = booleanPreferencesKey("auto_scan_on_launch")
        val OCR_SCANNED_DOCUMENTS_ENABLED = booleanPreferencesKey("ocr_scanned_documents_enabled")
        val SCAN_FOLDERS_INCLUDED = stringSetPreferencesKey("scan_folders_included")
        val SCAN_FOLDERS_EXCLUDED = stringSetPreferencesKey("scan_folders_excluded")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val themeStr = preferences[PreferencesKeys.DEFAULT_READER_THEME] ?: ReadingTheme.LIGHT.name
            val theme = try {
                ReadingTheme.valueOf(themeStr)
            } catch (e: Exception) {
                ReadingTheme.LIGHT
            }

            val fontSize = preferences[PreferencesKeys.DEFAULT_FONT_SIZE] ?: 18f
            val autoScan = preferences[PreferencesKeys.AUTO_SCAN_ON_LAUNCH] ?: true
            val ocrEnabled = preferences[PreferencesKeys.OCR_SCANNED_DOCUMENTS_ENABLED] ?: true
            val included = preferences[PreferencesKeys.SCAN_FOLDERS_INCLUDED] ?: setOf("Download", "Documents", "Books")
            val excluded = preferences[PreferencesKeys.SCAN_FOLDERS_EXCLUDED] ?: setOf("Android/data", ".thumbnails", "WhatsApp")

            AppSettings(
                defaultReaderTheme = theme,
                defaultFontSize = fontSize,
                autoScanOnLaunch = autoScan,
                ocrScannedDocumentsEnabled = ocrEnabled,
                scanFoldersIncluded = included,
                scanFoldersExcluded = excluded
            )
        }

    suspend fun setOcrScannedDocumentsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.OCR_SCANNED_DOCUMENTS_ENABLED] = enabled
        }
    }

    suspend fun setDefaultReaderTheme(theme: ReadingTheme) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_READER_THEME] = theme.name
        }
    }

    suspend fun setDefaultFontSize(fontSize: Float) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_FONT_SIZE] = fontSize.coerceIn(12f, 36f)
        }
    }

    suspend fun setAutoScanOnLaunch(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_SCAN_ON_LAUNCH] = enabled
        }
    }

    suspend fun addIncludedFolder(folderName: String) {
        if (folderName.isBlank()) return
        context.settingsDataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.SCAN_FOLDERS_INCLUDED] ?: setOf("Download", "Documents", "Books")
            preferences[PreferencesKeys.SCAN_FOLDERS_INCLUDED] = current + folderName.trim()
        }
    }

    suspend fun removeIncludedFolder(folderName: String) {
        context.settingsDataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.SCAN_FOLDERS_INCLUDED] ?: setOf("Download", "Documents", "Books")
            preferences[PreferencesKeys.SCAN_FOLDERS_INCLUDED] = current - folderName
        }
    }

    suspend fun addExcludedFolder(folderName: String) {
        if (folderName.isBlank()) return
        context.settingsDataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.SCAN_FOLDERS_EXCLUDED] ?: setOf("Android/data", ".thumbnails", "WhatsApp")
            preferences[PreferencesKeys.SCAN_FOLDERS_EXCLUDED] = current + folderName.trim()
        }
    }

    suspend fun removeExcludedFolder(folderName: String) {
        context.settingsDataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.SCAN_FOLDERS_EXCLUDED] ?: setOf("Android/data", ".thumbnails", "WhatsApp")
            preferences[PreferencesKeys.SCAN_FOLDERS_EXCLUDED] = current - folderName
        }
    }

    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        var totalDeletedBytes = 0L
        try {
            val cacheDir = context.cacheDir
            totalDeletedBytes += getDirectorySize(cacheDir)
            cacheDir.listFiles()?.forEach { file ->
                file.deleteRecursively()
            }
            context.externalCacheDir?.let { extCache ->
                totalDeletedBytes += getDirectorySize(extCache)
                extCache.listFiles()?.forEach { file ->
                    file.deleteRecursively()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        totalDeletedBytes
    }

    fun getCacheSizeBytes(): Long {
        var size = 0L
        try {
            size += getDirectorySize(context.cacheDir)
            context.externalCacheDir?.let { size += getDirectorySize(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return size
    }

    private fun getDirectorySize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        dir.listFiles()?.forEach { file ->
            total += if (file.isDirectory) getDirectorySize(file) else file.length()
        }
        return total
    }
}
