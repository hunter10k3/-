package com.example.data.model

import androidx.compose.ui.text.font.FontFamily

enum class PdfScrollMode(val displayName: String) {
    HORIZONTAL("Page Flip"),
    VERTICAL("Continuous Scroll")
}

enum class EpubRenderMode(val displayName: String) {
    COMPOSE_TEXT("Compose Text"),
    WEB_VIEW("Rich Web")
}

enum class ReaderFontFamily(val displayName: String, val fontFamily: FontFamily) {
    SYSTEM("System", FontFamily.Default),
    SERIF("Serif (Book)", FontFamily.Serif),
    MONOSPACE("Monospace", FontFamily.Monospace),
    CURSIVE("Cursive", FontFamily.Cursive)
}

enum class ReaderLineSpacing(val displayName: String, val multiplier: Float) {
    COMPACT("Compact", 1.25f),
    NORMAL("Normal", 1.55f),
    RELAXED("Relaxed", 1.85f),
    EXPANDED("Spacious", 2.15f)
}
