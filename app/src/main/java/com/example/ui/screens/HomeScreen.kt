package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.data.model.SearchFilterType
import com.example.data.model.SearchMatchType
import com.example.data.model.SearchResultItem
import com.example.ui.components.DocumentGridItem
import com.example.ui.components.DocumentListItem
import com.example.ui.viewmodel.BookmarksViewModel
import com.example.ui.viewmodel.HomeBottomTab
import com.example.ui.viewmodel.HomeUiState
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.SortOption
import com.example.ui.viewmodel.ViewMode
import com.example.ui.viewmodel.ViewModelFactory
import com.example.util.PermissionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    bookmarksViewModel: BookmarksViewModel,
    onOpenDocument: (DocumentItem) -> Unit,
    onOpenDocumentByUri: (uri: String, page: Int) -> Unit,
    onOpenSearchResult: (SearchResultItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showActionSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    // SAF Document Picker
    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore if not supported
            }
            viewModel.onDocumentPicked(uri) { doc ->
                onOpenDocument(doc)
            }
        }
    }

    // SAF Folder Tree Picker
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            viewModel.onSafFolderSelected(treeUri)
        }
    }

    // Legacy Storage Permission Launcher
    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onPermissionGranted()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    // Manage all files launcher for Android 11+
    val allFilesPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.checkPermission(context)
        if (PermissionManager.hasAnyStorageAccess(context)) {
            viewModel.onPermissionGranted()
        } else {
            folderPickerLauncher.launch(null)
        }
    }

    val requestStorageAccess = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = PermissionManager.createManageAllFilesIntent(context)
                allFilesPermissionLauncher.launch(intent)
            } catch (e: Exception) {
                folderPickerLauncher.launch(null)
            }
        } else {
            legacyPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermission(context)
        if (!uiState.hasStoragePermission && Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            legacyPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    Scaffold(
        topBar = {
            if (uiState.currentTab == HomeBottomTab.HOME) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "ꯂꯥꯏꯔꯤꯛ",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Deep Document Search & Reader",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.toggleViewMode() },
                            modifier = Modifier.testTag("toggle_view_mode")
                        ) {
                            Icon(
                                imageVector = if (uiState.viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                                contentDescription = "Toggle View Mode"
                            )
                        }
                        IconButton(
                            onClick = { viewModel.scanStorage() },
                            modifier = Modifier.testTag("rescan_storage_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Rescan Storage"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                NavigationBarItem(
                    selected = uiState.currentTab == HomeBottomTab.HOME,
                    onClick = { viewModel.setBottomTab(HomeBottomTab.HOME) },
                    icon = {
                        Icon(
                            if (uiState.currentTab == HomeBottomTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                            contentDescription = "Home"
                        )
                    },
                    label = { Text("Home") },
                    modifier = Modifier.testTag("tab_home")
                )
                NavigationBarItem(
                    selected = uiState.currentTab == HomeBottomTab.SEARCH,
                    onClick = { viewModel.setBottomTab(HomeBottomTab.SEARCH) },
                    icon = {
                        Icon(
                            if (uiState.currentTab == HomeBottomTab.SEARCH) Icons.Filled.Search else Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    label = { Text("Search") },
                    modifier = Modifier.testTag("tab_search")
                )
                NavigationBarItem(
                    selected = uiState.currentTab == HomeBottomTab.RECENT,
                    onClick = { viewModel.setBottomTab(HomeBottomTab.RECENT) },
                    icon = {
                        Icon(
                            if (uiState.currentTab == HomeBottomTab.RECENT) Icons.Filled.History else Icons.Outlined.History,
                            contentDescription = "Recent"
                        )
                    },
                    label = { Text("Recent") },
                    modifier = Modifier.testTag("tab_recent")
                )
                NavigationBarItem(
                    selected = uiState.currentTab == HomeBottomTab.BOOKMARKS,
                    onClick = { viewModel.setBottomTab(HomeBottomTab.BOOKMARKS) },
                    icon = {
                        Icon(
                            if (uiState.currentTab == HomeBottomTab.BOOKMARKS) Icons.Filled.Bookmarks else Icons.Outlined.Bookmarks,
                            contentDescription = "Bookmarks"
                        )
                    },
                    label = { Text("Bookmarks") },
                    modifier = Modifier.testTag("tab_bookmarks")
                )
                NavigationBarItem(
                    selected = uiState.currentTab == HomeBottomTab.SETTINGS,
                    onClick = { viewModel.setBottomTab(HomeBottomTab.SETTINGS) },
                    icon = {
                        Icon(
                            if (uiState.currentTab == HomeBottomTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = "Settings"
                        )
                    },
                    label = { Text("Settings") },
                    modifier = Modifier.testTag("tab_settings")
                )
            }
        },
        floatingActionButton = {
            if (uiState.currentTab == HomeBottomTab.HOME) {
                ExtendedFloatingActionButton(
                    onClick = { showActionSheet = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add / Scan") },
                    modifier = Modifier.testTag("add_document_fab")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.currentTab) {
                HomeBottomTab.HOME -> {
                    HomeContent(
                        uiState = uiState,
                        onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                        onSearchFilterChanged = { viewModel.onSearchFilterChanged(it) },
                        onSelectFormat = { viewModel.onFormatFilterSelected(it) },
                        onSortChanged = { viewModel.onSortOptionChanged(it) },
                        onOpenDocument = onOpenDocument,
                        onOpenSearchResult = onOpenSearchResult,
                        onRequestStoragePermission = requestStorageAccess,
                        onPickSaf = { docPickerLauncher.launch(arrayOf("*/*")) },
                        onScanFolder = { folderPickerLauncher.launch(null) },
                        onDismissPermissionError = { viewModel.clearPermissionError() },
                        onTriggerReindex = { viewModel.triggerReindex() },
                        showSortMenu = showSortMenu,
                        onSetSortMenu = { showSortMenu = it }
                    )
                }

                HomeBottomTab.SEARCH -> {
                    val searchViewModel: com.example.ui.viewmodel.SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                        factory = ViewModelFactory.provideSearchFactory(context)
                    )
                    SearchScreen(
                        viewModel = searchViewModel,
                        onOpenSearchHit = { hit ->
                            val ext = hit.fileName.substringAfterLast('.', "")
                            val format = DocumentFormat.fromExtension(ext) ?: DocumentFormat.TXT
                            onOpenSearchResult(
                                SearchResultItem(
                                    id = "${hit.fileUri}_${hit.location.page}_${hit.location.charOffset}_${hit.hashCode()}",
                                    documentUri = hit.fileUri.toString(),
                                    documentTitle = hit.fileName,
                                    format = format,
                                    matchType = when (hit.matchType) {
                                        com.example.data.model.MatchType.FILENAME -> com.example.data.model.SearchMatchType.FILENAME
                                        com.example.data.model.MatchType.CONTENT -> com.example.data.model.SearchMatchType.CONTENT
                                        com.example.data.model.MatchType.CHAPTER -> com.example.data.model.SearchMatchType.CHAPTER
                                        com.example.data.model.MatchType.OCR -> com.example.data.model.SearchMatchType.OCR
                                    },
                                    pageNumber = hit.location.page ?: 1,
                                    chapterTitle = if (hit.matchType == com.example.data.model.MatchType.CHAPTER) hit.snippet.replace("<mark>", "").replace("</mark>", "") else null,
                                    charOffset = hit.location.charOffset?.toInt() ?: 0,
                                    snippet = hit.snippet.replace("<mark>", "").replace("</mark>", ""),
                                    highlightTerm = ""
                                )
                            )
                        }
                    )
                }

                HomeBottomTab.RECENT -> {
                    RecentScreen(
                        recentDocuments = uiState.recentDocuments,
                        onOpenDocument = onOpenDocument,
                        onRemoveRecent = { uri ->
                            viewModel.removeRecentDocument(uri)
                        },
                        onClearAllRecent = {
                            viewModel.clearAllRecent()
                        }
                    )
                }

                HomeBottomTab.BOOKMARKS -> {
                    BookmarksScreen(
                        viewModel = bookmarksViewModel,
                        onOpenDocumentAtPage = onOpenDocumentByUri,
                        onNavigateBack = { viewModel.setBottomTab(HomeBottomTab.HOME) }
                    )
                }

                HomeBottomTab.SETTINGS -> {
                    val settingsViewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                        factory = ViewModelFactory.provideSettingsFactory(context)
                    )
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onRescanStorage = { viewModel.scanStorage() },
                        onSelectFolderSaf = { folderPickerLauncher.launch(null) },
                        onGenerateSamples = { viewModel.generateSampleLibrary() },
                        onRequestStoragePermission = requestStorageAccess,
                        onReindexAll = { viewModel.triggerReindex() }
                    )
                }
            }
        }

        // Action Sheet
        if (showActionSheet) {
            ModalBottomSheet(onDismissRequest = { showActionSheet = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "Document Access & Scanning",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column {
                            ActionSheetItem(
                                icon = Icons.Default.FileOpen,
                                title = "Pick Document (SAF)",
                                subtitle = "Select any PDF, PPT, EPUB, DOC, or TXT file",
                                onClick = {
                                    showActionSheet = false
                                    docPickerLauncher.launch(
                                        arrayOf(
                                            "application/pdf",
                                            "application/epub+zip",
                                            "text/plain",
                                            "application/msword",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "application/vnd.ms-powerpoint",
                                            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                            "*/*"
                                        )
                                    )
                                }
                            )

                            ActionSheetItem(
                                icon = Icons.Default.CreateNewFolder,
                                title = "Scan Folder with SAF",
                                subtitle = "Grant persistent access to folders like /Download or /Documents",
                                onClick = {
                                    showActionSheet = false
                                    folderPickerLauncher.launch(null)
                                }
                            )

                            ActionSheetItem(
                                icon = Icons.Default.Refresh,
                                title = "Rescan MediaStore & Storage",
                                subtitle = "Index all storage documents across internal and external paths",
                                onClick = {
                                    showActionSheet = false
                                    viewModel.scanStorage()
                                }
                            )

                            ActionSheetItem(
                                icon = Icons.Default.FindInPage,
                                title = "Re-Index Full Text & OCR",
                                subtitle = "Rebuild deep searchable text index for all documents",
                                onClick = {
                                    showActionSheet = false
                                    viewModel.triggerReindex()
                                }
                            )

                            ActionSheetItem(
                                icon = Icons.Default.AutoAwesome,
                                title = "Generate Sample Library",
                                subtitle = "Add authentic sample PDF, PPT, EPUB, DOC, TXT documents",
                                onClick = {
                                    showActionSheet = false
                                    viewModel.generateSampleLibrary()
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onSearchQueryChanged: (String) -> Unit,
    onSearchFilterChanged: (SearchFilterType) -> Unit,
    onSelectFormat: (DocumentFormat?) -> Unit,
    onSortChanged: (SortOption) -> Unit,
    onOpenDocument: (DocumentItem) -> Unit,
    onOpenSearchResult: (SearchResultItem) -> Unit,
    onRequestStoragePermission: () -> Unit,
    onPickSaf: () -> Unit,
    onScanFolder: () -> Unit,
    onDismissPermissionError: () -> Unit,
    onTriggerReindex: () -> Unit,
    showSortMenu: Boolean,
    onSetSortMenu: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Scanning progress indicator
        if (uiState.isScanning) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uiState.scanProgress.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp
                )
            }
        }

        // Indexing indicator banner (when indexing full text/OCR in background)
        if (uiState.isIndexing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = uiState.indexingProgress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Permission denied warning banner
        if (uiState.permissionDeniedMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Storage Permission Required",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = uiState.permissionDeniedMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = onRequestStoragePermission,
                            modifier = Modifier.testTag("banner_grant_permission_btn")
                        ) {
                            Text("Grant Access", fontSize = 12.sp)
                        }
                    }
                    IconButton(onClick = onDismissPermissionError) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss")
                    }
                }
            }
        }

        // Deep Search Bar: Filenames, Content, Chapters, OCR
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("search_document_input"),
            placeholder = { Text("Search text, chapters, OCR, filename...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChanged("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                focusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        // When searching, show search category tabs (All, Content, TOC, OCR, Filename)
        if (uiState.searchQuery.isNotBlank()) {
            SearchFilterChipsRow(
                selectedFilter = uiState.searchFilterType,
                onSelectFilter = onSearchFilterChanged
            )
        } else {
            // Standard format filter chips
            FilterChipsRow(
                selectedFormat = uiState.selectedFormat,
                formatCounts = uiState.formatCounts,
                totalCount = uiState.totalDocumentCount,
                onSelectFormat = onSelectFormat
            )
        }

        // Search Results Mode vs Library Browsing Mode
        if (uiState.searchQuery.isNotBlank()) {
            SearchResultsContent(
                query = uiState.searchQuery,
                isSearching = uiState.isSearching,
                searchResults = uiState.searchResults,
                indexedDocsCount = uiState.indexedDocsCount,
                totalDocsCount = uiState.totalDocumentCount,
                onOpenSearchResult = onOpenSearchResult,
                onTriggerReindex = onTriggerReindex
            )
        } else if (uiState.filteredDocuments.isEmpty() && !uiState.isScanning) {
            EmptyStorageState(
                searchQuery = uiState.searchQuery,
                onGrantStorageAccess = onRequestStoragePermission,
                onPickSaf = onPickSaf,
                onScanFolder = onScanFolder
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                // Header with Document count and Sort by: Name, Date, Size
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val headerTitle = if (uiState.selectedFormat != null) {
                            "${uiState.selectedFormat.displayName} (${uiState.filteredDocuments.size})"
                        } else {
                            "Documents (${uiState.filteredDocuments.size})"
                        }

                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Box {
                            OutlinedButton(
                                onClick = { onSetSortMenu(true) },
                                modifier = Modifier.testTag("sort_by_dropdown")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Sort: ${uiState.sortOption.label}",
                                    fontSize = 12.sp
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { onSetSortMenu(false) }
                            ) {
                                SortOption.values().forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            onSortChanged(option)
                                            onSetSortMenu(false)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // List or Grid presentation
                if (uiState.viewMode == ViewMode.LIST) {
                    items(uiState.filteredDocuments, key = { it.uri }) { doc ->
                        val recent = uiState.recentDocuments.firstOrNull { it.uri == doc.uri }
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            DocumentListItem(
                                document = doc,
                                onClick = { onOpenDocument(doc) },
                                readingProgressPercent = recent?.progressPercent,
                                lastOpenedTimestamp = recent?.lastReadTimestamp
                            )
                        }
                    }
                } else {
                    item {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(((uiState.filteredDocuments.size / 2 + 1) * 150).dp)
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.filteredDocuments, key = { it.uri }) { doc ->
                                val recent = uiState.recentDocuments.firstOrNull { it.uri == doc.uri }
                                DocumentGridItem(
                                    document = doc,
                                    onClick = { onOpenDocument(doc) },
                                    lastOpenedTimestamp = recent?.lastReadTimestamp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchFilterChipsRow(
    selectedFilter: SearchFilterType,
    onSelectFilter: (SearchFilterType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SearchFilterType.values().forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onSelectFilter(filter) },
                label = { Text(filter.label) },
                leadingIcon = {
                    val icon = when (filter) {
                        SearchFilterType.ALL -> Icons.Default.FindInPage
                        SearchFilterType.CONTENT -> Icons.Default.TextFields
                        SearchFilterType.CHAPTERS -> Icons.AutoMirrored.Filled.MenuBook
                        SearchFilterType.OCR -> Icons.Default.DocumentScanner
                        SearchFilterType.FILENAMES -> Icons.Default.Description
                    }
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
        }
    }
}

@Composable
private fun SearchResultsContent(
    query: String,
    isSearching: Boolean,
    searchResults: List<SearchResultItem>,
    indexedDocsCount: Int,
    totalDocsCount: Int,
    onOpenSearchResult: (SearchResultItem) -> Unit,
    onTriggerReindex: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Status header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isSearching) "Searching..." else "${searchResults.size} results for \"$query\"",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (indexedDocsCount < totalDocsCount) {
                Text(
                    text = "Indexed $indexedDocsCount/$totalDocsCount docs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (searchResults.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No matches found for \"$query\"",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Try different keywords or re-index the library for deep full-text and OCR extraction.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onTriggerReindex) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Re-Index Document Library")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(searchResults, key = { it.id }) { item ->
                    SearchResultCard(
                        result = item,
                        onClick = { onOpenSearchResult(item) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun SearchResultCard(
    result: SearchResultItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Document title and Format + Match Type Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Badge for Document format
                    Surface(
                        color = Color(result.format.badgeColor),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = result.format.displayName,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = result.documentTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Match category tag (Content, Chapter, OCR, Filename)
                val (matchLabel, matchColor) = when (result.matchType) {
                    SearchMatchType.CONTENT -> "Full-Text" to MaterialTheme.colorScheme.primary
                    SearchMatchType.CHAPTER -> "Chapter" to Color(0xFFE65100)
                    SearchMatchType.OCR -> "OCR Text" to Color(0xFF6A1B9A)
                    SearchMatchType.FILENAME -> "Filename" to MaterialTheme.colorScheme.secondary
                }

                Surface(
                    color = matchColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = matchLabel,
                        color = matchColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Location banner (e.g. Page 4 • Chapter 2: Introduction)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FindInPage,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                val locText = buildString {
                    if (result.pageNumber > 0) {
                        append("Page ${result.pageNumber}")
                    }
                    if (!result.chapterTitle.isNullOrBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append(result.chapterTitle)
                    }
                }
                Text(
                    text = if (locText.isNotBlank()) locText else "General",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Highlighted snippet text
            val annotatedSnippet = remember(result.snippet, result.highlightTerm) {
                buildAnnotatedString {
                    val text = result.snippet
                    val term = result.highlightTerm.lowercase()
                    if (term.isBlank() || !text.lowercase().contains(term)) {
                        append(text)
                    } else {
                        var start = 0
                        while (start < text.length) {
                            val idx = text.lowercase().indexOf(term, start)
                            if (idx == -1) {
                                append(text.substring(start))
                                break
                            }
                            if (idx > start) {
                                append(text.substring(start, idx))
                            }
                            withStyle(
                                SpanStyle(
                                    background = Color(0xFFFFEB3B),
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append(text.substring(idx, (idx + term.length).coerceAtMost(text.length)))
                            }
                            start = idx + term.length
                        }
                    }
                }
            }

            Text(
                text = annotatedSnippet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Jump to location",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    selectedFormat: DocumentFormat?,
    formatCounts: Map<DocumentFormat, Int>,
    totalCount: Int,
    onSelectFormat: (DocumentFormat?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // All
        FilterChip(
            selected = selectedFormat == null,
            onClick = { onSelectFormat(null) },
            label = { Text("All ($totalCount)") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
            )
        )

        // PDF
        val pdfCount = formatCounts[DocumentFormat.PDF] ?: 0
        FilterChip(
            selected = selectedFormat == DocumentFormat.PDF,
            onClick = { onSelectFormat(DocumentFormat.PDF) },
            label = { Text("PDF ($pdfCount)") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(DocumentFormat.PDF.badgeColor),
                selectedLabelColor = Color.White
            )
        )

        // PPT (PPT / PPTX)
        val pptCount = (formatCounts[DocumentFormat.PPT] ?: 0) + (formatCounts[DocumentFormat.PPTX] ?: 0)
        FilterChip(
            selected = selectedFormat == DocumentFormat.PPT || selectedFormat == DocumentFormat.PPTX,
            onClick = { onSelectFormat(DocumentFormat.PPTX) },
            label = { Text("PPT ($pptCount)") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(DocumentFormat.PPTX.badgeColor),
                selectedLabelColor = Color.White
            )
        )

        // EPUB
        val epubCount = formatCounts[DocumentFormat.EPUB] ?: 0
        FilterChip(
            selected = selectedFormat == DocumentFormat.EPUB,
            onClick = { onSelectFormat(DocumentFormat.EPUB) },
            label = { Text("EPUB ($epubCount)") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(DocumentFormat.EPUB.badgeColor),
                selectedLabelColor = Color.White
            )
        )

        // DOC (DOC / DOCX)
        val docCount = (formatCounts[DocumentFormat.DOC] ?: 0) + (formatCounts[DocumentFormat.DOCX] ?: 0)
        FilterChip(
            selected = selectedFormat == DocumentFormat.DOC || selectedFormat == DocumentFormat.DOCX,
            onClick = { onSelectFormat(DocumentFormat.DOCX) },
            label = { Text("DOC ($docCount)") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(DocumentFormat.DOCX.badgeColor),
                selectedLabelColor = Color.White
            )
        )

        // TXT
        val txtCount = formatCounts[DocumentFormat.TXT] ?: 0
        FilterChip(
            selected = selectedFormat == DocumentFormat.TXT,
            onClick = { onSelectFormat(DocumentFormat.TXT) },
            label = { Text("TXT ($txtCount)") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(DocumentFormat.TXT.badgeColor),
                selectedLabelColor = Color.White
            )
        )
    }
}

@Composable
private fun EmptyStorageState(
    searchQuery: String,
    onGrantStorageAccess: () -> Unit,
    onPickSaf: () -> Unit,
    onScanFolder: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(38.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (searchQuery.isNotBlank()) {
            Text(
                text = "No documents found matching '$searchQuery'",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Check spelling or clear the filter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "No Documents Discovered",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "ꯂꯥꯏꯔꯤꯛ needs storage access to find your documents (PDF, PPT, EPUB, DOC, TXT) across internal and external storage.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Primary CTA: "Grant storage access"
            Button(
                onClick = onGrantStorageAccess,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .testTag("grant_storage_access_cta")
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Grant storage access")
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onScanFolder,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Select Folder with SAF")
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onPickSaf,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Pick Document File")
            }
        }
    }
}

@Composable
private fun ActionSheetItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
