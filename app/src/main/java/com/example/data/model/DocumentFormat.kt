package com.example.data.model

import androidx.compose.ui.graphics.Color

enum class DocumentFormat(
    val extension: String,
    val displayName: String,
    val mimeType: String,
    val badgeColor: Long
) {
    PDF("pdf", "PDF", "application/pdf", 0xFFE53935),
    PPT("ppt", "PPT", "application/vnd.ms-powerpoint", 0xFFE65100),
    PPTX("pptx", "PPTX", "application/vnd.openxmlformats-officedocument.presentationml.presentation", 0xFFE65100),
    EPUB("epub", "EPUB", "application/epub+zip", 0xFF2E7D32),
    DOC("doc", "DOC", "application/msword", 0xFF1565C0),
    DOCX("docx", "DOCX", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 0xFF1565C0),
    TXT("txt", "TXT", "text/plain", 0xFF546E7A);

    val isPresentation: Boolean
        get() = this == PPT || this == PPTX

    val isWordDoc: Boolean
        get() = this == DOC || this == DOCX

    companion object {
        fun fromExtension(ext: String): DocumentFormat? {
            val cleanExt = ext.trim().lowercase().removePrefix(".")
            return values().firstOrNull { it.extension.equals(cleanExt, ignoreCase = true) }
        }

        fun fromFileName(fileName: String): DocumentFormat? {
            val ext = fileName.substringAfterLast('.', "")
            return fromExtension(ext)
        }

        fun fromMimeType(mime: String?): DocumentFormat? {
            if (mime == null) return null
            return values().firstOrNull { it.mimeType.equals(mime, ignoreCase = true) }
        }
    }
}
