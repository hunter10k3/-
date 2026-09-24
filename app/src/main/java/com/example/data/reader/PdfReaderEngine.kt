package com.example.data.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfReaderEngine(
    private val context: Context,
    private val documentUri: Uri
) {
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var tempFile: File? = null

    private val renderMutex = Mutex()

    // Cache up to 24 bitmaps in memory to keep scrolling super smooth and avoid re-rendering
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceIn(16 * 1024, 64 * 1024) // in KB

    private val bitmapCache = object : LruCache<Int, Bitmap>(cacheSize) {
        override fun sizeOf(key: Int, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    var pageCount: Int = 0
        private set

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            try {
                closeInternal()
                pfd = if (documentUri.scheme == "file") {
                    val file = File(documentUri.path ?: "")
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                } else {
                    try {
                        context.contentResolver.openFileDescriptor(documentUri, "r")
                    } catch (e: Exception) {
                        // Fallback: copy content stream to temporary cache file
                        val temp = File(context.cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
                        context.contentResolver.openInputStream(documentUri)?.use { input ->
                            FileOutputStream(temp).use { output ->
                                input.copyTo(output)
                            }
                        }
                        tempFile = temp
                        ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
                    }
                }

                pfd?.let { descriptor ->
                    renderer = PdfRenderer(descriptor)
                    pageCount = renderer?.pageCount ?: 0
                    return@withLock pageCount > 0
                }
                false
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    fun isPageCached(pageIndex: Int): Boolean {
        val cached = bitmapCache.get(pageIndex)
        return cached != null && !cached.isRecycled
    }

    fun getCachedPage(pageIndex: Int): Bitmap? {
        val cached = bitmapCache.get(pageIndex)
        return if (cached != null && !cached.isRecycled) cached else null
    }

    suspend fun getPageAspectRatio(pageIndex: Int): Float = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            val currentRenderer = renderer ?: return@withLock 1.414f
            if (pageIndex < 0 || pageIndex >= pageCount) return@withLock 1.414f
            try {
                val page = currentRenderer.openPage(pageIndex)
                val ratio = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                page.close()
                ratio
            } catch (e: Exception) {
                1.414f
            }
        }
    }

    suspend fun renderPage(pageIndex: Int, targetWidth: Int = 1200): Bitmap? = withContext(Dispatchers.IO) {
        // Fast path: check in-memory cache
        val cached = bitmapCache.get(pageIndex)
        if (cached != null && !cached.isRecycled) {
            return@withContext cached
        }

        renderMutex.withLock {
            // Check cache again inside lock
            val doubleCheck = bitmapCache.get(pageIndex)
            if (doubleCheck != null && !doubleCheck.isRecycled) {
                return@withLock doubleCheck
            }

            val currentRenderer = renderer ?: return@withLock null
            if (pageIndex < 0 || pageIndex >= pageCount) return@withLock null

            try {
                val page = currentRenderer.openPage(pageIndex)
                val aspectRatio = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                val targetHeight = (targetWidth * aspectRatio).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                bitmapCache.put(pageIndex, bitmap)
                bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun clearCache() {
        bitmapCache.evictAll()
    }

    private fun closeInternal() {
        bitmapCache.evictAll()
        try {
            renderer?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        renderer = null

        try {
            pfd?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        pfd = null

        tempFile?.delete()
        tempFile = null
    }

    fun close() {
        closeInternal()
    }
}
