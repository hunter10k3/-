package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.navigation.LairikNavHost
import com.example.ui.theme.LairikTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialUri = extractDocumentUri(intent)

        setContent {
            LairikTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LairikNavHost(
                        initialDocumentUri = initialUri
                    )
                }
            }
        }
    }

    private fun extractDocumentUri(intent: Intent?): String? {
        if (intent == null) return null
        val action = intent.action
        if (Intent.ACTION_VIEW == action) {
            intent.data?.let { uri ->
                return uri.toString()
            }
        }
        return null
    }
}
