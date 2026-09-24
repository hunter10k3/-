package com.example.data.model

data class DocumentItem(
    val uri: String,
    val title: String,
    val path: String? = null,
    val extension: String,
    val format: DocumentFormat,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val lastModified: Long = System.currentTimeMillis(),
    val pageCount: Int = 0,
    val isSample: Boolean = false
) {
    val name: String
        get() = title

    val size: Long
        get() = sizeBytes

    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            var size = sizeBytes.toDouble()
            var unitIndex = 0
            while (size >= 1024 && unitIndex < units.lastIndex) {
                size /= 1024
                unitIndex++
            }
            return String.format("%.1f %s", size, units[unitIndex])
        }
}

// Alias Document for DocumentItem for clean terminology
typealias Document = DocumentItem
