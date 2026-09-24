package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DocumentFormat
import com.example.data.model.DocumentItem
import com.example.data.scanner.SampleDocumentGenerator
import com.example.data.scanner.StorageDocumentScanner
import com.example.util.PermissionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ꯂꯥꯏꯔꯤꯛ", appName)
    }

    @Test
    fun `document format identification`() {
        assertEquals(DocumentFormat.PDF, DocumentFormat.fromExtension("pdf"))
        assertEquals(DocumentFormat.EPUB, DocumentFormat.fromExtension("epub"))
        assertEquals(DocumentFormat.DOCX, DocumentFormat.fromExtension("docx"))
        assertEquals(DocumentFormat.DOC, DocumentFormat.fromExtension("doc"))
        assertEquals(DocumentFormat.PPTX, DocumentFormat.fromExtension("pptx"))
        assertEquals(DocumentFormat.PPT, DocumentFormat.fromExtension("ppt"))
        assertEquals(DocumentFormat.TXT, DocumentFormat.fromExtension("txt"))
    }

    @Test
    fun `document item properties alias`() {
        val doc = DocumentItem(
            uri = "content://docs/sample.pdf",
            title = "Sample Document",
            extension = "pdf",
            format = DocumentFormat.PDF,
            mimeType = "application/pdf",
            sizeBytes = 2048L
        )
        assertEquals("Sample Document", doc.name)
        assertEquals(2048L, doc.size)
        assertEquals("2.0 KB", doc.formattedSize)
    }

    @Test
    fun `sample documents generation and scanning`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SampleDocumentGenerator.generateSampleLibrary(context)
        val scanner = StorageDocumentScanner(context)
        val scanned = scanner.scanStorageDocuments()

        assertTrue("Should discover generated sample documents", scanned.isNotEmpty())
        assertTrue("Should include PDF or TXT", scanned.any { it.format == DocumentFormat.PDF || it.format == DocumentFormat.TXT })
    }

    @Test
    fun `permission manager intent creation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = PermissionManager.createManageAllFilesIntent(context)
        assertNotNull(intent)
    }
}
