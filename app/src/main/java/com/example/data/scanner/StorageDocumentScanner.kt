package com.example.data.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.data.model.DocumentFormat
import com.example.data.model.DocumentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

data class ScanProgressState(
    val status: String,
    val count: Int,
    val isComplete: Boolean = false,
    val error: String? = null
)

class StorageDocumentScanner(private val context: Context) {

    private val supportedExtensions = setOf(
        "pdf", "epub", "doc", "docx", "ppt", "pptx", "txt"
    )

    /**
     * Scans storage and emits progress updates and intermediate document lists as a Flow.
     */
    fun scanStorageFlow(): Flow<Pair<ScanProgressState, List<DocumentItem>>> = flow {
        val results = mutableMapOf<String, DocumentItem>()

        emit(ScanProgressState("Initializing storage scanner...", 0) to emptyList())

        // 1. Scan App Internal Documents
        emit(ScanProgressState("Scanning application documents...", results.size) to results.values.toList())
        val internalDocs = scanDirectory(context.filesDir)
        for (doc in internalDocs) {
            results[doc.uri] = doc
        }
        emit(ScanProgressState("Found ${results.size} documents in app storage", results.size) to results.values.toList())

        // 2. Scan MediaStore Files
        emit(ScanProgressState("Querying system MediaStore...", results.size) to results.values.toList())
        val mediaStoreDocs = queryMediaStoreFiles()
        for (doc in mediaStoreDocs) {
            results[doc.uri] = doc
        }
        emit(ScanProgressState("Indexed ${results.size} total documents", results.size) to results.values.toList())

        // 3. Scan Common External Storage Directories with recursive File walk
        try {
            emit(ScanProgressState("Scanning storage directories...", results.size) to results.values.toList())
            val targetDirs = mutableListOf<File>()

            // External files dir
            context.getExternalFilesDir(null)?.let { targetDirs.add(it) }

            // Download directory
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let {
                if (it.exists() && it.canRead()) targetDirs.add(it)
            }

            // Documents directory
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.let {
                if (it.exists() && it.canRead()) targetDirs.add(it)
            }

            // Root external storage if accessible
            val extRoot = Environment.getExternalStorageDirectory()
            if (extRoot != null && extRoot.exists() && extRoot.canRead()) {
                targetDirs.add(extRoot)
            }

            for (dir in targetDirs.distinct()) {
                val found = scanDirectory(dir)
                for (doc in found) {
                    results[doc.uri] = doc
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Scan Persisted SAF Folders (if any)
        try {
            val persistedUris = context.contentResolver.persistedUriPermissions
            if (persistedUris.isNotEmpty()) {
                emit(ScanProgressState("Scanning user-granted folders...", results.size) to results.values.toList())
                for (perm in persistedUris) {
                    if (perm.isReadPermission) {
                        val safDocs = scanSafFolder(perm.uri)
                        for (doc in safDocs) {
                            results[doc.uri] = doc
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val finalList = results.values.sortedByDescending { it.lastModified }
        emit(ScanProgressState("Scan complete. Found ${finalList.size} documents.", finalList.size, isComplete = true) to finalList)
    }.flowOn(Dispatchers.IO)

    suspend fun scanStorageDocuments(): List<DocumentItem> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, DocumentItem>()

        // 1. Scan app internal documents directory
        val internalDocs = scanDirectory(context.filesDir)
        for (doc in internalDocs) {
            results[doc.uri] = doc
        }

        // 2. Scan MediaStore Files
        val mediaStoreDocs = queryMediaStoreFiles()
        for (doc in mediaStoreDocs) {
            results[doc.uri] = doc
        }

        // 3. Scan external directories
        try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let {
                if (it.exists() && it.canRead()) {
                    for (doc in scanDirectory(it)) { results[doc.uri] = doc }
                }
            }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.let {
                if (it.exists() && it.canRead()) {
                    for (doc in scanDirectory(it)) { results[doc.uri] = doc }
                }
            }
            context.getExternalFilesDir(null)?.let { extDir ->
                for (doc in scanDirectory(extDir)) { results[doc.uri] = doc }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Scan Persisted SAF Folders
        try {
            for (perm in context.contentResolver.persistedUriPermissions) {
                if (perm.isReadPermission) {
                    for (doc in scanSafFolder(perm.uri)) {
                        results[doc.uri] = doc
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        results.values.sortedByDescending { it.lastModified }
    }

    suspend fun scanSafFolder(treeUri: Uri): List<DocumentItem> = withContext(Dispatchers.IO) {
        val documents = mutableListOf<DocumentItem>()
        try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            scanSafChildren(childrenUri, treeUri, documents)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        documents
    }

    private fun scanSafChildren(childrenUri: Uri, treeUri: Uri, documents: MutableList<DocumentItem>) {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(idCol)
                    val displayName = cursor.getString(nameCol) ?: "Untitled"
                    val mimeType = cursor.getString(mimeCol) ?: "application/octet-stream"
                    val sizeBytes = cursor.getLong(sizeCol)
                    val lastModified = cursor.getLong(modCol)

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val subFolderChildrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
                        scanSafChildren(subFolderChildrenUri, treeUri, documents)
                    } else {
                        val ext = displayName.substringAfterLast('.', "").lowercase()
                        val format = DocumentFormat.fromExtension(ext)
                        if (format != null) {
                            val fileDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                            documents.add(
                                DocumentItem(
                                    uri = fileDocUri.toString(),
                                    title = displayName.substringBeforeLast('.'),
                                    path = fileDocUri.path ?: displayName,
                                    extension = ext,
                                    format = format,
                                    mimeType = mimeType,
                                    sizeBytes = sizeBytes,
                                    lastModified = if (lastModified > 0) lastModified else System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun resolveSafDocument(uri: Uri): DocumentItem? = withContext(Dispatchers.IO) {
        try {
            var displayName = "Untitled"
            var sizeBytes = 0L
            var lastMod = System.currentTimeMillis()

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) {
                        displayName = cursor.getString(nameIndex) ?: displayName
                    }
                    if (sizeIndex != -1) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }

            val ext = displayName.substringAfterLast('.', "").lowercase()
            val format = DocumentFormat.fromExtension(ext) ?: DocumentFormat.TXT
            val mimeType = context.contentResolver.getType(uri) ?: format.mimeType

            DocumentItem(
                uri = uri.toString(),
                title = displayName.substringBeforeLast('.'),
                path = uri.toString(),
                extension = ext,
                format = format,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                lastModified = lastMod
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Recursive File walk searching for supported document formats.
     */
    private fun scanDirectory(dir: File): List<DocumentItem> {
        val list = mutableListOf<DocumentItem>()
        if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return list

        try {
            dir.walkTopDown()
                .maxDepth(6)
                .onEnter { folder ->
                    // Skip hidden folders, Android system caches, and Android/data protected dirs
                    val name = folder.name
                    !name.startsWith(".") && name != "Android" && !name.equals("cache", ignoreCase = true)
                }
                .forEach { file ->
                    if (file.isFile && !file.name.startsWith(".")) {
                        val ext = file.extension.lowercase()
                        val format = DocumentFormat.fromExtension(ext)
                        if (format != null) {
                            list.add(
                                DocumentItem(
                                    uri = Uri.fromFile(file).toString(),
                                    title = file.nameWithoutExtension,
                                    path = file.absolutePath,
                                    extension = ext,
                                    format = format,
                                    mimeType = format.mimeType,
                                    sizeBytes = file.length(),
                                    lastModified = file.lastModified()
                                )
                            )
                        }
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun queryMediaStoreFiles(): List<DocumentItem> {
        val list = mutableListOf<DocumentItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )

        val selection = supportedExtensions.joinToString(" OR ") {
            "${MediaStore.Files.FileColumns.DATA} LIKE '%.${it}'"
        }

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = if (idCol != -1) cursor.getLong(idCol) else -1L
                    val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val displayName = if (nameCol != -1) cursor.getString(nameCol) ?: File(path).name else File(path).name
                    val ext = displayName.substringAfterLast('.', "").lowercase()
                    val format = DocumentFormat.fromExtension(ext) ?: continue

                    val mimeType = if (mimeCol != -1) cursor.getString(mimeCol) ?: format.mimeType else format.mimeType
                    val sizeBytes = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val dateModifiedSec = if (dateCol != -1) cursor.getLong(dateCol) else 0L
                    val dateModified = if (dateModifiedSec > 0) dateModifiedSec * 1000L else System.currentTimeMillis()

                    val contentUri = ContentUris.withAppendedId(collection, id)

                    list.add(
                        DocumentItem(
                            uri = contentUri.toString(),
                            title = displayName.substringBeforeLast('.'),
                            path = path,
                            extension = ext,
                            format = format,
                            mimeType = mimeType,
                            sizeBytes = sizeBytes,
                            lastModified = dateModified
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return list
    }
}
