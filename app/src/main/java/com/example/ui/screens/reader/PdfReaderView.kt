package com.example.ui.screens.reader

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PdfScrollMode
import com.example.ui.viewmodel.ReaderUiState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// Color inversion matrix for PDF Night Mode (converts bright paper to dark mode)
private val InvertColorMatrix = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )
)

@Composable
fun PdfReaderView(
    uiState: ReaderUiState,
    onPageSelected: (Int) -> Unit,
    onToggleScrollMode: () -> Unit,
    onToggleNightMode: () -> Unit,
    onRequestJumpToPage: () -> Unit,
    onToggleControls: () -> Unit,
    renderPageBitmap: suspend (pageIndex: Int) -> Bitmap?,
    modifier: Modifier = Modifier
) {
    val totalPages = uiState.totalPages.coerceAtLeast(1)
    val coroutineScope = rememberCoroutineScope()

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = newScale
        if (newScale > 1f) {
            val maxOffset = 500f * (newScale - 1f)
            offset = Offset(
                x = (offset.x + panChange.x).coerceIn(-maxOffset, maxOffset),
                y = (offset.y + panChange.y).coerceIn(-maxOffset, maxOffset)
            )
        } else {
            offset = Offset.Zero
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            // Center zoom on tap position
                            offset = Offset(
                                x = (size.width / 2f - tapOffset.x) * 1.5f,
                                y = (size.height / 2f - tapOffset.y) * 1.5f
                            )
                        }
                    }
                )
            }
            .transformable(state = transformableState)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
    ) {
        if (uiState.pdfScrollMode == PdfScrollMode.HORIZONTAL) {
            // Horizontal Pager Mode (Page Flip)
            val pagerState = rememberPagerState(
                initialPage = (uiState.currentPage - 1).coerceIn(0, totalPages - 1),
                pageCount = { totalPages }
            )

            // Sync from ViewModel to Pager
            LaunchedEffect(uiState.currentPage) {
                val target = uiState.currentPage - 1
                if (target != pagerState.currentPage && target in 0 until totalPages) {
                    pagerState.animateScrollToPage(target)
                }
            }

            // Sync from Pager to ViewModel
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }
                    .distinctUntilChanged()
                    .collect { pageIdx ->
                        onPageSelected(pageIdx + 1)
                    }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 16.dp,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
            ) { pageIdx ->
                PdfSinglePage(
                    pageIndex = pageIdx,
                    isNightMode = uiState.isPdfNightMode,
                    renderBitmap = renderPageBitmap,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // Vertical Continuous Scroll Mode (LazyColumn)
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = (uiState.currentPage - 1).coerceIn(0, totalPages - 1)
            )

            // Sync from ViewModel to LazyColumn
            LaunchedEffect(uiState.currentPage) {
                val target = uiState.currentPage - 1
                if (target != listState.firstVisibleItemIndex && target in 0 until totalPages) {
                    listState.animateScrollToItem(target)
                }
            }

            // Sync from LazyColumn to ViewModel
            val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
            LaunchedEffect(firstVisibleIndex) {
                if (firstVisibleIndex in 0 until totalPages) {
                    onPageSelected(firstVisibleIndex + 1)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    count = totalPages,
                    key = { it }
                ) { pageIdx ->
                    PdfSinglePage(
                        pageIndex = pageIdx,
                        isNightMode = uiState.isPdfNightMode,
                        renderBitmap = renderPageBitmap,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Quick floating action pill overlay (Page flip mode toggle, Night mode toggle, and Zoom reset)
        AnimatedVisibility(
            visible = uiState.showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Zoom reset indicator if zoomed in
                    if (scale > 1.05f) {
                        IconButton(
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset Zoom",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Night Mode Toggle (Invert Colors)
                    IconButton(
                        onClick = onToggleNightMode,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isPdfNightMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (uiState.isPdfNightMode) "Normal Paper Mode" else "Invert Colors / Night Mode",
                            tint = if (uiState.isPdfNightMode) Color(0xFFFFB74D) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Scroll Mode Toggle (Horizontal vs Vertical)
                    IconButton(
                        onClick = onToggleScrollMode,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.pdfScrollMode == PdfScrollMode.HORIZONTAL) Icons.Default.SwapVert else Icons.Default.SwapHoriz,
                            contentDescription = if (uiState.pdfScrollMode == PdfScrollMode.HORIZONTAL) "Switch to Vertical Scroll" else "Switch to Horizontal Flip",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Bottom Page Indicator Pill (Tappable to jump to page)
        AnimatedVisibility(
            visible = uiState.showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        ) {
            Surface(
                onClick = onRequestJumpToPage,
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 4.dp,
                modifier = Modifier.testTag("pdf_jump_page_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Page ${uiState.currentPage} / ${uiState.totalPages}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• Tap to jump",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfSinglePage(
    pageIndex: Int,
    isNightMode: Boolean,
    renderBitmap: suspend (pageIndex: Int) -> Bitmap?,
    modifier: Modifier = Modifier
) {
    val bitmapState by produceState<Bitmap?>(initialValue = null, key1 = pageIndex) {
        value = renderBitmap(pageIndex)
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val bitmap = bitmapState
        if (bitmap != null && !bitmap.isRecycled) {
            val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat().coerceAtLeast(1f)
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                colorFilter = if (isNightMode) ColorFilter.colorMatrix(InvertColorMatrix) else null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f / aspectRatio)
                    .clip(RoundedCornerShape(6.dp))
                    .border(
                        width = 1.dp,
                        color = if (isNightMode) Color.DarkGray else Color.LightGray.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp)
                    )
            )
        } else {
            // Skeleton loader with standard A4 ratio (1.414)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f / 1.414f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isNightMode) Color(0xFF1E1E1E) else Color(0xFFEBEBEB)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Rendering Page ${pageIndex + 1}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isNightMode) Color.LightGray else Color.Gray
                    )
                }
            }
        }
    }
}
