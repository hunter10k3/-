package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.preferences.AppSettings
import com.example.data.local.preferences.AppSettingsDataStore
import com.example.data.model.ReadingTheme
import com.example.data.repository.DocumentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class SettingsUiMessage(
    val id: Long = System.currentTimeMillis(),
    val message: String
)

class SettingsViewModel(
    private val settingsDataStore: AppSettingsDataStore,
    private val repository: DocumentRepository
) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = settingsDataStore.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    private val _cacheSize = MutableStateFlow(formatBytes(settingsDataStore.getCacheSizeBytes()))
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    private val _uiMessage = MutableStateFlow<SettingsUiMessage?>(null)
    val uiMessage: StateFlow<SettingsUiMessage?> = _uiMessage.asStateFlow()

    init {
        refreshCacheSize()
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            val bytes = settingsDataStore.getCacheSizeBytes()
            _cacheSize.value = formatBytes(bytes)
        }
    }

    fun setDefaultTheme(theme: ReadingTheme) {
        viewModelScope.launch {
            settingsDataStore.setDefaultReaderTheme(theme)
            _uiMessage.value = SettingsUiMessage(message = "Default reader theme set to ${theme.displayName}")
        }
    }

    fun setDefaultFontSize(size: Float) {
        viewModelScope.launch {
            settingsDataStore.setDefaultFontSize(size)
        }
    }

    fun setAutoScanOnLaunch(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setAutoScanOnLaunch(enabled)
            _uiMessage.value = SettingsUiMessage(
                message = if (enabled) "Auto-scan on launch enabled" else "Auto-scan on launch disabled"
            )
        }
    }

    fun setOcrScannedDocumentsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setOcrScannedDocumentsEnabled(enabled)
            _uiMessage.value = SettingsUiMessage(
                message = if (enabled) "OCR for scanned documents enabled" else "OCR disabled"
            )
        }
    }

    fun addIncludedFolder(folderName: String) {
        viewModelScope.launch {
            settingsDataStore.addIncludedFolder(folderName)
            _uiMessage.value = SettingsUiMessage(message = "Added scan folder: $folderName")
        }
    }

    fun removeIncludedFolder(folderName: String) {
        viewModelScope.launch {
            settingsDataStore.removeIncludedFolder(folderName)
            _uiMessage.value = SettingsUiMessage(message = "Removed scan folder: $folderName")
        }
    }

    fun addExcludedFolder(folderName: String) {
        viewModelScope.launch {
            settingsDataStore.addExcludedFolder(folderName)
            _uiMessage.value = SettingsUiMessage(message = "Added excluded folder rule: $folderName")
        }
    }

    fun removeExcludedFolder(folderName: String) {
        viewModelScope.launch {
            settingsDataStore.removeExcludedFolder(folderName)
            _uiMessage.value = SettingsUiMessage(message = "Removed exclude rule: $folderName")
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            val clearedBytes = settingsDataStore.clearCache()
            _cacheSize.value = formatBytes(settingsDataStore.getCacheSizeBytes())
            _uiMessage.value = SettingsUiMessage(
                message = "Cache cleared (${formatBytes(clearedBytes)} freed)"
            )
        }
    }

    fun clearRecentFiles() {
        viewModelScope.launch {
            repository.clearAllRecent()
            _uiMessage.value = SettingsUiMessage(message = "Recent files history cleared")
        }
    }

    fun dismissMessage() {
        _uiMessage.value = null
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes <= 0 -> "0 B"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
            bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
            else -> String.format(Locale.US, "%.2f GB", bytes / (1024f * 1024f * 1024f))
        }
    }
}
