package com.example.ui.screens.reader

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.EpubRenderMode
import com.example.data.model.ReaderFontFamily
import com.example.data.reader.EpubChapter
import com.example.ui.viewmodel.ReaderUiState

@Composable
fun EpubReaderView(
    uiState: ReaderUiState,
    onNextChapter: () -> Unit,
    onPrevChapter: () -> Unit,
    onToggleControls: () -> Unit,
    onOpenToc: () -> Unit,
    onToggleRenderMode: (EpubRenderMode) -> Unit,
    onAddBookmarkSnippet: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val book = uiState.epubBook ?: return
    val chapterIdx = uiState.currentEpubChapterIndex
    val chapter = book.chapters.getOrNull(chapterIdx) ?: return
    val theme = uiState.readingTheme

    Box(modifier = modifier.fillMaxSize().background(theme.backgroundColor)) {
        if (uiState.epubRenderMode == EpubRenderMode.COMPOSE_TEXT) {
            EpubComposeTextView(
                chapter = chapter,
                chapterIndex = chapterIdx,
                totalChapters = book.chapters.size,
                uiState = uiState,
                onNextChapter = onNextChapter,
                onPrevChapter = onPrevChapter,
                onToggleControls = onToggleControls,
                onOpenToc = onOpenToc,
                onToggleRenderMode = onToggleRenderMode,
                onAddBookmarkSnippet = onAddBookmarkSnippet
            )
        } else {
            EpubWebView(
                chapter = chapter,
                chapterIndex = chapterIdx,
                totalChapters = book.chapters.size,
                uiState = uiState,
                onNextChapter = onNextChapter,
                onPrevChapter = onPrevChapter,
                onToggleControls = onToggleControls,
                onOpenToc = onOpenToc,
                onToggleRenderMode = onToggleRenderMode
            )
        }
    }
}

@Composable
private fun EpubComposeTextView(
    chapter: EpubChapter,
    chapterIndex: Int,
    totalChapters: Int,
    uiState: ReaderUiState,
    onNextChapter: () -> Unit,
    onPrevChapter: () -> Unit,
    onToggleControls: () -> Unit,
    onOpenToc: () -> Unit,
    onToggleRenderMode: (EpubRenderMode) -> Unit,
    onAddBookmarkSnippet: (String) -> Unit
) {
    val theme = uiState.readingTheme
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .clickable { onToggleControls() }
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        // Mode & TOC quick bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onOpenToc,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FormatListNumbered, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Chapter ${chapterIndex + 1}/$totalChapters", fontSize = 12.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = uiState.epubRenderMode == EpubRenderMode.COMPOSE_TEXT,
                    onClick = { onToggleRenderMode(EpubRenderMode.COMPOSE_TEXT) },
                    label = { Text("Compose", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )
                FilterChip(
                    selected = uiState.epubRenderMode == EpubRenderMode.WEB_VIEW,
                    onClick = { onToggleRenderMode(EpubRenderMode.WEB_VIEW) },
                    label = { Text("Web", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Chapter Title
        Text(
            text = chapter.title,
            fontSize = (uiState.fontSizeSp + 6f).sp,
            fontFamily = uiState.readerFontFamily.fontFamily,
            fontWeight = FontWeight.Bold,
            color = theme.textColor,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // Chapter Paragraphs with native text selection
        SelectionContainer {
            Column {
                chapter.paragraphs.forEachIndexed { pIdx, paragraph ->
                    Text(
                        text = paragraph,
                        fontSize = uiState.fontSizeSp.sp,
                        fontFamily = uiState.readerFontFamily.fontFamily,
                        lineHeight = (uiState.fontSizeSp * uiState.lineSpacing.multiplier).sp,
                        color = theme.textColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = (uiState.fontSizeSp * 0.75f).dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Chapter navigation controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onPrevChapter,
                enabled = chapterIndex > 0,
                modifier = Modifier.testTag("epub_prev_chapter")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Previous")
            }

            Text(
                text = "${chapter.wordCount} words",
                style = MaterialTheme.typography.labelSmall,
                color = theme.secondaryTextColor
            )

            FilledTonalButton(
                onClick = onNextChapter,
                enabled = chapterIndex < totalChapters - 1,
                modifier = Modifier.testTag("epub_next_chapter")
            ) {
                Text("Next")
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EpubWebView(
    chapter: EpubChapter,
    chapterIndex: Int,
    totalChapters: Int,
    uiState: ReaderUiState,
    onNextChapter: () -> Unit,
    onPrevChapter: () -> Unit,
    onToggleControls: () -> Unit,
    onOpenToc: () -> Unit,
    onToggleRenderMode: (EpubRenderMode) -> Unit
) {
    val theme = uiState.readingTheme
    val bgColorHex = String.format("#%06X", (0xFFFFFF and theme.backgroundColor.toArgb()))
    val textColorHex = String.format("#%06X", (0xFFFFFF and theme.textColor.toArgb()))
    val cssFontFamily = when (uiState.readerFontFamily) {
        ReaderFontFamily.SERIF -> "Georgia, serif"
        ReaderFontFamily.MONOSPACE -> "monospace, Courier New"
        ReaderFontFamily.CURSIVE -> "cursive, Georgia"
        ReaderFontFamily.SYSTEM -> "-apple-system, Roboto, sans-serif"
    }

    val styledHtml = remember(chapter.rawHtml, theme, uiState.fontSizeSp, uiState.lineSpacing, uiState.readerFontFamily) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=2.0, user-scalable=yes">
            <style>
                body {
                    background-color: $bgColorHex !important;
                    color: $textColorHex !important;
                    font-size: ${uiState.fontSizeSp}px !important;
                    line-height: ${uiState.lineSpacing.multiplier} !important;
                    font-family: $cssFontFamily !important;
                    padding: 16px 20px 80px 20px !important;
                    margin: 0 !important;
                    word-wrap: break-word !important;
                }
                h1, h2, h3, h4, h5, h6 {
                    color: $textColorHex !important;
                    font-family: $cssFontFamily !important;
                    margin-top: 1.2em;
                    margin-bottom: 0.6em;
                }
                p {
                    margin-bottom: 1em !important;
                    text-align: justify;
                }
                a {
                    color: #2196F3 !important;
                }
                img {
                    max-width: 100% !important;
                    height: auto !important;
                    border-radius: 8px;
                }
                blockquote {
                    margin-left: 16px;
                    padding-left: 12px;
                    border-left: 3px solid $textColorHex;
                    opacity: 0.85;
                }
            </style>
        </head>
        <body>
            ${if (chapter.rawHtml.isNotBlank()) chapter.rawHtml else chapter.paragraphs.joinToString("") { "<p>$it</p>" }}
        </body>
        </html>
        """.trimIndent()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Mode & Chapter switcher header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onOpenToc,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FormatListNumbered, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Chapter ${chapterIndex + 1}/$totalChapters", fontSize = 12.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = uiState.epubRenderMode == EpubRenderMode.COMPOSE_TEXT,
                    onClick = { onToggleRenderMode(EpubRenderMode.COMPOSE_TEXT) },
                    label = { Text("Compose", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )
                FilterChip(
                    selected = uiState.epubRenderMode == EpubRenderMode.WEB_VIEW,
                    onClick = { onToggleRenderMode(EpubRenderMode.WEB_VIEW) },
                    label = { Text("Web", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )
            }
        }

        // Web view content
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = false
                        setBackgroundColor(theme.backgroundColor.toArgb())
                        loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
                    }
                },
                update = { webView ->
                    webView.setBackgroundColor(theme.backgroundColor.toArgb())
                    webView.loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Chapter navigation bottom bar
        Surface(
            color = theme.toolbarColor,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onPrevChapter,
                    enabled = chapterIndex > 0
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Chapter")
                }

                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.textColor,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    textAlign = TextAlign.Center
                )

                FilledTonalIconButton(
                    onClick = onNextChapter,
                    enabled = chapterIndex < totalChapters - 1
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Chapter")
                }
            }
        }
    }
}
