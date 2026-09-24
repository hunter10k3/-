package com.example.data.model

/**
 * Data class representing a document chapter or table-of-contents entry.
 *
 * @property title Chapter or section heading title
 * @property level Nesting depth for TOC trees (1 for top-level, 2 for subsections, etc.)
 * @property startPage 1-indexed page number for page-based documents (e.g. PDF, PPTX)
 * @property startOffset Character offset for stream-based documents (e.g. EPUB, TXT, MD, DOCX)
 * @property startTimeMs Reserved for future media / audio sync
 */
data class Chapter(
    val title: String,
    val level: Int = 1,
    val startPage: Int? = null,
    val startOffset: Long? = null,
    val startTimeMs: Long? = null
)
