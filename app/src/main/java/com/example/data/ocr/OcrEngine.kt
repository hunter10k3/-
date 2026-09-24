package com.example.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.coroutines.resume

class OcrEngine(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        suspendCancellableCoroutine { continuation ->
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        continuation.resume(visionText.text)
                    }
                    .addOnFailureListener { error ->
                        error.printStackTrace()
                        continuation.resume("")
                    }
            } catch (e: Throwable) {
                e.printStackTrace()
                continuation.resume("")
            }
        }
    }

    suspend fun recognizeTextFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        var bitmap: Bitmap? = null
        try {
            val stream: InputStream? = if (uri.scheme == "file") {
                java.io.FileInputStream(java.io.File(uri.path ?: ""))
            } else {
                context.contentResolver.openInputStream(uri)
            }

            stream?.use { input ->
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                bitmap = BitmapFactory.decodeStream(input, null, options)
            }

            bitmap?.let { bmp ->
                return@withContext recognizeTextFromBitmap(bmp)
            }
            ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        } finally {
            try {
                bitmap?.recycle()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
