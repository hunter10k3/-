package com.example

import android.app.Application
import com.example.di.AppContainer
import com.example.di.DefaultAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LairikApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)

        // Pre-scan storage and generate sample documents in background on startup
        applicationScope.launch {
            try {
                container.documentRepository.scanStorage()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
