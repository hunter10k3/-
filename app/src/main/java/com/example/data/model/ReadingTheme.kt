package com.example.data.model

import androidx.compose.ui.graphics.Color

enum class ReadingTheme(
    val displayName: String,
    val backgroundColor: Color,
    val textColor: Color,
    val secondaryTextColor: Color,
    val toolbarColor: Color
) {
    LIGHT(
        displayName = "Light",
        backgroundColor = Color(0xFFFFFFFF),
        textColor = Color(0xFF191C1E),
        secondaryTextColor = Color(0xFF74777F),
        toolbarColor = Color(0xFFF1F5F9)
    ),
    SEPIA(
        displayName = "Sepia",
        backgroundColor = Color(0xFFF7EEDD),
        textColor = Color(0xFF423226),
        secondaryTextColor = Color(0xFF7A6A59),
        toolbarColor = Color(0xFFEFE4CE)
    ),
    SAGE(
        displayName = "Sage",
        backgroundColor = Color(0xFFE8EFE9),
        textColor = Color(0xFF263328),
        secondaryTextColor = Color(0xFF5D6B60),
        toolbarColor = Color(0xFFDEE7DF)
    ),
    DARK(
        displayName = "Dark",
        backgroundColor = Color(0xFF1E2124),
        textColor = Color(0xFFE2E2E6),
        secondaryTextColor = Color(0xFF909094),
        toolbarColor = Color(0xFF16181A)
    ),
    AMOLED(
        displayName = "OLED Black",
        backgroundColor = Color(0xFF000000),
        textColor = Color(0xFFEDEDED),
        secondaryTextColor = Color(0xFF888888),
        toolbarColor = Color(0xFF0D0D0D)
    )
}
