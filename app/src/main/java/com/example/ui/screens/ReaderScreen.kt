package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DocumentFormat
import com.example.data.model.DocumentItem
import com.example.data.model.ReadingTheme
import com.example.data.reader.DocxParagraphStyle
import com.example.ui.components.BookmarksBottomSheet
import com.example.ui.components.FormatBadge
import com.example.ui.components.JumpToPageDialog
import com.example.ui.components.ReaderSettingsBottomSheet
import com.example.ui.components.TableOfContentsBottomSheet
import com.example.ui.components.TableOfContentsDrawerContent
import com.example.ui.screens.reader.EpubReaderView
import com.example.ui.screens.reader.PdfReaderView
import com.example.ui.viewmodel.ReaderUiState
import com.example.ui.viewmodel.ReaderViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showMoreMenu by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val currentTheme = uiState.readingTheme

    LaunchedEffect(uiState.showTocSheet) {
        if (uiState.showTocSheet) {
            drawerState.open()
            viewModel.setShowTocSheet(false)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            TableOfContentsDrawerContent(
                uiState = uiState,
                onSelectChapter = { chapter ->
                    viewModel.jumpToChapter(chapter)
                    scope.launch { drawerState.close() }
                },
                onSelectPage = { page ->
                    viewModel.goToPage(page)
                    scope.launch { drawerState.close() }
                },
                onCloseDrawer = {
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                AnimatedVisibility(
                    visible = uiState.showControls,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = uiState.document?.title ?: "Document",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (uiState.document != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        FormatBadge(format = uiState.document!!.format, isSmall = true)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (uiState.totalPages > 1) "Page ${uiState.currentPage} of ${uiState.totalPages}" else "${uiState.document!!.formattedSize}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.testTag("reader_back_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            // Search button (for text documents)
                            if (uiState.document?.format == DocumentFormat.TXT) {
                                IconButton(onClick = { viewModel.toggleSearch() }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search in text")
                                }
                            }

                            // Bookmark toggle
                            IconButton(
                                onClick = { viewModel.toggleBookmark() },
                                modifier = Modifier.testTag("reader_bookmark_toggle")
                            ) {
                                Icon(
                                    imageVector = if (uiState.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Bookmark Page",
                                    tint = if (uiState.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Appearance / Theme & Reader Settings
                            IconButton(
                                onClick = { viewModel.setShowThemeSheet(true) },
                                modifier = Modifier.testTag("reader_theme_button")
                            ) {
                                Icon(Icons.Default.Palette, contentDescription = "Reading Customization")
                            }

                            // OCR Text Extraction & Selection
                            if (uiState.document?.format == DocumentFormat.PDF) {
                                IconButton(
                                    onClick = { viewModel.toggleOcrOverlay() },
                                    modifier = Modifier.testTag("reader_ocr_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DocumentScanner,
                                        contentDescription = "Select Text & OCR",
                                        tint = if (uiState.isOcrOverlayVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Table of Contents - Opens TOC Drawer
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        if (drawerState.isOpen) drawerState.close() else drawerState.open()
                                    }
                                },
                                modifier = Modifier.testTag("reader_toc_button")
                            ) {
                                Icon(Icons.Default.FormatListNumbered, contentDescription = "Table of Contents")
                            }

                            // More menu: Bookmarks, Share, Open External
                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("View Bookmarks") },
                                        leadingIcon = { Icon(Icons.Default.Bookmarks, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.setShowBookmarksSheet(true)
                                        }
                                    )
                                    if (uiState.document?.format == DocumentFormat.PDF) {
                                        DropdownMenuItem(
                                            text = { Text("Select Text (OCR Overlay)") },
                                            leadingIcon = { Icon(Icons.Default.DocumentScanner, contentDescription = null) },
                                            onClick = {
                                                showMoreMenu = false
                                                viewModel.toggleOcrOverlay()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Run OCR on Page ${uiState.currentPage}") },
                                            leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                                            onClick = {
                                                showMoreMenu = false
                                                viewModel.runOcrOnCurrentPage()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Run OCR on Full Document") },
                                            leadingIcon = { Icon(Icons.Default.FindInPage, contentDescription = null) },
                                            onClick = {
                                                showMoreMenu = false
                                                viewModel.runOcrOnEntireDocument()
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Share Document") },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            uiState.document?.let { doc ->
                                                try {
                                                    val uri = Uri.parse(doc.uri)
                                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                                        type = doc.mimeType
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        putExtra(Intent.EXTRA_TITLE, doc.title)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(intent, "Share Document"))
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Open in External App") },
                                        leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            uiState.document?.let { doc ->
                                                try {
                                                    val uri = Uri.parse(doc.uri)
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(uri, doc.mimeType)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(Intent.createChooser(intent, "Open with..."))
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = currentTheme.toolbarColor
                        )
                    )
                }
            },
            bottomBar = {
                // Bottom controls for non-PDF or general paging
                AnimatedVisibility(
                    visible = uiState.showControls && uiState.totalPages > 1 && uiState.document?.format != DocumentFormat.PDF,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    Surface(
                        color = currentTheme.toolbarColor,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FilledTonalIconButton(
                                    onClick = { viewModel.prevPage() },
                                    enabled = uiState.currentPage > 1
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Page")
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${uiState.currentPage} / ${uiState.totalPages}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = currentTheme.textColor
                                    )
                                    Text(
                                        text = "${uiState.progressPercent.toInt()}% completed",
                                        fontSize = 11.sp,
                                        color = currentTheme.secondaryTextColor
                                    )
                                }

                                FilledTonalIconButton(
                                    onClick = { viewModel.nextPage() },
                                    enabled = uiState.currentPage < uiState.totalPages
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Page")
                                }
                            }

                            Slider(
                                value = uiState.currentPage.toFloat(),
                                onValueChange = { viewModel.goToPage(it.toInt()) },
                                valueRange = 1f..uiState.totalPages.toFloat().coerceAtLeast(1f),
                                steps = (uiState.totalPages - 2).coerceAtLeast(0),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(currentTheme.backgroundColor)
            ) {
                when {
                    uiState.isLoading -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading document...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = currentTheme.textColor
                            )
                        }
                    }

                    uiState.errorMessage != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Unable to Display Document",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.errorMessage ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = currentTheme.textColor,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            FilledTonalButton(onClick = {
                                uiState.document?.let { doc ->
                                    try {
                                        val uri = Uri.parse(doc.uri)
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, doc.mimeType)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Open in external app"))
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                Text("Open in External App")
                            }
                        }
                    }

                    else -> {
                        // Document Format Specific Reader
                        when (uiState.document?.format) {
                            DocumentFormat.PDF -> {
                                PdfReaderView(
                                    uiState = uiState,
                                    onPageSelected = { page -> viewModel.onScrolledToPage(page - 1) },
                                    onToggleScrollMode = { viewModel.togglePdfScrollMode() },
                                    onToggleNightMode = { viewModel.togglePdfNightMode() },
                                    onRequestJumpToPage = { viewModel.setShowJumpToPageDialog(true) },
                                    onToggleControls = { viewModel.toggleControls() },
                                    renderPageBitmap = { pageIndex -> viewModel.getPageBitmap(pageIndex) }
                                )
                            }

                            DocumentFormat.EPUB -> {
                                EpubReaderView(
                                    uiState = uiState,
                                    onNextChapter = { viewModel.nextPage() },
                                    onPrevChapter = { viewModel.prevPage() },
                                    onToggleControls = { viewModel.toggleControls() },
                                    onOpenToc = {
                                        scope.launch { drawerState.open() }
                                    },
                                    onToggleRenderMode = { viewModel.setEpubRenderMode(it) },
                                    onAddBookmarkSnippet = { viewModel.toggleBookmark() }
                                )
                            }

                            DocumentFormat.DOC, DocumentFormat.DOCX -> {
                                DocxDocumentViewer(uiState = uiState)
                            }

                            DocumentFormat.PPT, DocumentFormat.PPTX -> {
                                PptxDocumentViewer(
                                    uiState = uiState,
                                    onNextSlide = { viewModel.nextPage() },
                                    onPrevSlide = { viewModel.prevPage() }
                                )
                            }

                            DocumentFormat.TXT, null -> {
                                TextDocumentViewer(
                                    uiState = uiState,
                                    onSearchQuery = { viewModel.setSearchQuery(it) }
                                )
                            }
                        }
                    }
                }

                // Bottom Sheets & Dialogs
                if (uiState.showThemeSheet) {
                    ReaderSettingsBottomSheet(
                        documentFormat = uiState.document?.format,
                        currentTheme = uiState.readingTheme,
                        currentFontSizeSp = uiState.fontSizeSp,
                        currentFontFamily = uiState.readerFontFamily,
                        currentLineSpacing = uiState.lineSpacing,
                        pdfScrollMode = uiState.pdfScrollMode,
                        isPdfNightMode = uiState.isPdfNightMode,
                        epubRenderMode = uiState.epubRenderMode,
                        onThemeSelected = { viewModel.setReadingTheme(it) },
                        onFontSizeChanged = { viewModel.setFontSize(it) },
                        onFontFamilySelected = { viewModel.setReaderFontFamily(it) },
                        onLineSpacingSelected = { viewModel.setLineSpacing(it) },
                        onPdfScrollModeChanged = { viewModel.setPdfScrollMode(it) },
                        onTogglePdfNightMode = { viewModel.togglePdfNightMode() },
                        onEpubRenderModeChanged = { viewModel.setEpubRenderMode(it) },
                        onDismiss = { viewModel.setShowThemeSheet(false) }
                    )
                }

                if (uiState.showBookmarksSheet) {
                    BookmarksBottomSheet(
                        bookmarks = uiState.bookmarks,
                        onSelectBookmark = { page -> viewModel.goToPage(page) },
                        onDeleteBookmark = { id -> viewModel.toggleBookmark() },
                        onDismiss = { viewModel.setShowBookmarksSheet(false) }
                    )
                }

                if (uiState.showJumpToPageDialog) {
                    JumpToPageDialog(
                        currentPage = uiState.currentPage,
                        totalPages = uiState.totalPages,
                        onJumpToPage = { page -> viewModel.goToPage(page) },
                        onDismiss = { viewModel.setShowJumpToPageDialog(false) }
                    )
                }

                if (uiState.isOcrOverlayVisible) {
                    OcrTextOverlaySheet(
                        uiState = uiState,
                        onRunOcrCurrentPage = { viewModel.runOcrOnCurrentPage() },
                        onRunOcrEntireDocument = { viewModel.runOcrOnEntireDocument() },
                        onDismiss = { viewModel.toggleOcrOverlay() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrTextOverlaySheet(
    uiState: ReaderUiState,
    onRunOcrCurrentPage: () -> Unit,
    onRunOcrEntireDocument: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val text = uiState.ocrText
    val isProcessing = uiState.isOcrProcessing
    val confidence = uiState.ocrConfidence ?: 0.90f
    val percent = (confidence * 100).toInt().coerceIn(60, 99)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DocumentScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Page ${uiState.currentPage} Text (OCR)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (text != null && text.isNotBlank()) {
                    Surface(
                        color = Color(0xFFE8DEF8),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "📷 ML Kit ($percent%)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D192B),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Select, copy, or share text recognized from this scanned page using on-device ML Kit.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            when {
                isProcessing -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Running ML Kit Text Recognition on Page ${uiState.currentPage}...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                text.isNullOrBlank() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.DocumentScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No OCR text indexed for this page yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Extract selectable text from this scanned image using on-device ML Kit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onRunOcrCurrentPage,
                                modifier = Modifier.testTag("ocr_run_page_btn")
                            ) {
                                Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Recognize Page ${uiState.currentPage}")
                            }
                            OutlinedButton(onClick = onRunOcrEntireDocument) {
                                Text("OCR Full Doc")
                            }
                        }
                    }
                }
                else -> {
                    // Text available for selection
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 320.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            SelectionContainer {
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("OCR Text", text))
                                Toast.makeText(context, "Copied OCR text to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ocr_copy_btn")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Text")
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        this.type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Extracted Text"))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share Text")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// -----------------------------------------------------------------------------------------
// DOCX VIEWER
// -----------------------------------------------------------------------------------------
@Composable
private fun DocxDocumentViewer(uiState: ReaderUiState) {
    val docx = uiState.docxDocument
    val doc = uiState.document
    val theme = uiState.readingTheme

    if (docx == null || docx.paragraphs.isEmpty()) {
        OfficeDocumentFallbackView(
            document = doc,
            formatName = "Word Document (.docx / .doc)",
            theme = theme,
            errorMessage = uiState.errorMessage ?: "This document could not be previewed in native mode."
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Quick external app banner (Option C integration)
        OfficeDocumentQuickBanner(
            document = doc,
            formatLabel = "Word Document Preview",
            theme = theme
        )

        Spacer(modifier = Modifier.height(12.dp))

        docx.paragraphs.forEach { p ->
            when (p.style) {
                DocxParagraphStyle.TITLE -> {
                    Text(
                        text = p.text,
                        fontSize = (uiState.fontSizeSp + 8f).sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textColor,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                DocxParagraphStyle.HEADING_1 -> {
                    Text(
                        text = p.text,
                        fontSize = (uiState.fontSizeSp + 5f).sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textColor,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
                DocxParagraphStyle.HEADING_2 -> {
                    Text(
                        text = p.text,
                        fontSize = (uiState.fontSizeSp + 2f).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.textColor,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                    )
                }
                DocxParagraphStyle.BODY -> {
                    Text(
                        text = p.text,
                        fontSize = uiState.fontSizeSp.sp,
                        lineHeight = (uiState.fontSizeSp * 1.55f).sp,
                        color = theme.textColor,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
                DocxParagraphStyle.BULLET -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            fontSize = (uiState.fontSizeSp + 2f).sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = p.text,
                            fontSize = uiState.fontSizeSp.sp,
                            lineHeight = (uiState.fontSizeSp * 1.55f).sp,
                            color = theme.textColor
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

// -----------------------------------------------------------------------------------------
// PPTX VIEWER
// -----------------------------------------------------------------------------------------
@Composable
private fun PptxDocumentViewer(
    uiState: ReaderUiState,
    onNextSlide: () -> Unit,
    onPrevSlide: () -> Unit
) {
    val presentation = uiState.pptxPresentation
    val doc = uiState.document
    val theme = uiState.readingTheme

    if (presentation == null || presentation.slides.isEmpty()) {
        OfficeDocumentFallbackView(
            document = doc,
            formatName = "PowerPoint Presentation (.pptx / .ppt)",
            theme = theme,
            errorMessage = uiState.errorMessage ?: "This presentation could not be previewed in native mode."
        )
        return
    }

    val currentSlideIndex = (uiState.currentPage - 1).coerceIn(0, (presentation.slides.size - 1).coerceAtLeast(0))
    val currentSlide = presentation.slides.getOrNull(currentSlideIndex) ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OfficeDocumentQuickBanner(
            document = doc,
            formatLabel = "Slide ${currentSlide.slideNumber} of ${presentation.slides.size}",
            theme = theme
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = theme.backgroundColor
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text(
                    text = "Slide ${currentSlide.slideNumber}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = currentSlide.title,
                    fontSize = (uiState.fontSizeSp + 6f).sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.textColor,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                currentSlide.bulletPoints.forEach { point ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            fontSize = (uiState.fontSizeSp + 4f).sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                            text = point,
                            fontSize = uiState.fontSizeSp.sp,
                            lineHeight = (uiState.fontSizeSp * 1.5f).sp,
                            color = theme.textColor
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// OFFICE FALLBACK & QUICK ACTION COMPONENTS (Options B & C)
// -----------------------------------------------------------------------------------------
@Composable
private fun OfficeDocumentQuickBanner(
    document: DocumentItem?,
    formatLabel: String,
    theme: ReadingTheme
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = theme.toolbarColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$formatLabel (Fast Preview)",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textColor,
                    fontWeight = FontWeight.Medium
                )
            }

            TextButton(
                onClick = {
                    document?.let { launchExternalOfficeApp(context, it) }
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Open in Office App", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun OfficeDocumentFallbackView(
    document: DocumentItem?,
    formatName: String,
    theme: ReadingTheme,
    errorMessage: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = document?.title ?: formatName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = theme.textColor,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "$formatName • ${document?.formattedSize ?: ""}",
            style = MaterialTheme.typography.labelMedium,
            color = theme.secondaryTextColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = theme.toolbarColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Complex Document Rendering (Option C Recommended)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.textColor
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = errorMessage ?: "This document format (DOC/DOCX/PPT/PPTX) contains complex layouts, charts, shapes, or tables. For 100% full fidelity, open with your device's office suite (Google Docs, Google Slides, Microsoft 365, or WPS Office).",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.secondaryTextColor,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                document?.let { launchExternalOfficeApp(context, it) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("open_external_office_button")
        ) {
            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open with External Office App")
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                document?.let { doc ->
                    try {
                        val uri = Uri.parse(doc.uri)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = doc.mimeType
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_TITLE, doc.title)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Document"))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Share Document")
        }
    }
}

private fun launchExternalOfficeApp(context: Context, doc: DocumentItem) {
    try {
        val uri = Uri.parse(doc.uri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, doc.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open ${doc.title} with"))
    } catch (e: Exception) {
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=office+viewer&c=apps")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
        } catch (e2: Exception) {
            android.widget.Toast.makeText(context, "No app found to open this document", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

// -----------------------------------------------------------------------------------------
// TEXT VIEWER
// -----------------------------------------------------------------------------------------
@Composable
private fun TextDocumentViewer(
    uiState: ReaderUiState,
    onSearchQuery: (String) -> Unit
) {
    val textDoc = uiState.textDocument ?: return
    val theme = uiState.readingTheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        if (uiState.isSearchActive) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQuery,
                placeholder = { Text("Search document...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                singleLine = true
            )

            if (uiState.searchResults.isNotEmpty()) {
                Text(
                    text = "Found ${uiState.searchResults.size} matches",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            textDoc.lines.forEach { line ->
                val annotatedString = buildAnnotatedString {
                    if (uiState.searchQuery.isNotEmpty() && line.contains(uiState.searchQuery, ignoreCase = true)) {
                        var startIndex = 0
                        val query = uiState.searchQuery
                        while (startIndex < line.length) {
                            val foundIndex = line.indexOf(query, startIndex, ignoreCase = true)
                            if (foundIndex == -1) {
                                append(line.substring(startIndex))
                                break
                            }
                            append(line.substring(startIndex, foundIndex))
                            withStyle(SpanStyle(background = Color.Yellow, color = Color.Black, fontWeight = FontWeight.Bold)) {
                                append(line.substring(foundIndex, foundIndex + query.length))
                            }
                            startIndex = foundIndex + query.length
                        }
                    } else {
                        append(line)
                    }
                }

                Text(
                    text = annotatedString,
                    fontSize = uiState.fontSizeSp.sp,
                    lineHeight = (uiState.fontSizeSp * 1.55f).sp,
                    color = theme.textColor,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
