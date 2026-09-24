package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.LairikApplication
import com.example.data.repository.DocumentRepository

class ViewModelFactory(
    private val context: Context,
    private val repository: DocumentRepository,
    private val documentUri: String? = null,
    private val initialPage: Int? = null,
    private val initialOffset: Int? = null,
    private val query: String? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(repository) as T
            }
            modelClass.isAssignableFrom(BookmarksViewModel::class.java) -> {
                BookmarksViewModel(repository) as T
            }
            modelClass.isAssignableFrom(SearchViewModel::class.java) -> {
                val app = context.applicationContext as LairikApplication
                SearchViewModel(repository, app.container.indexingManager) as T
            }
            modelClass.isAssignableFrom(ReaderViewModel::class.java) -> {
                ReaderViewModel(
                    context = context,
                    repository = repository,
                    documentUriString = documentUri ?: "",
                    initialTargetPage = initialPage,
                    initialTargetOffset = initialOffset,
                    highlightSearchQuery = query
                ) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                val app = context.applicationContext as LairikApplication
                SettingsViewModel(app.container.appSettingsDataStore, repository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    companion object {
        fun provideHomeFactory(context: Context): ViewModelProvider.Factory {
            val app = context.applicationContext as LairikApplication
            return ViewModelFactory(app, app.container.documentRepository)
        }

        fun provideBookmarksFactory(context: Context): ViewModelProvider.Factory {
            val app = context.applicationContext as LairikApplication
            return ViewModelFactory(app, app.container.documentRepository)
        }

        fun provideSearchFactory(context: Context): ViewModelProvider.Factory {
            val app = context.applicationContext as LairikApplication
            return ViewModelFactory(app, app.container.documentRepository)
        }

        fun provideReaderFactory(
            context: Context,
            documentUri: String,
            initialPage: Int? = null,
            initialOffset: Int? = null,
            query: String? = null
        ): ViewModelProvider.Factory {
            val app = context.applicationContext as LairikApplication
            return ViewModelFactory(
                context = app,
                repository = app.container.documentRepository,
                documentUri = documentUri,
                initialPage = initialPage,
                initialOffset = initialOffset,
                query = query
            )
        }

        fun provideSettingsFactory(context: Context): ViewModelProvider.Factory {
            val app = context.applicationContext as LairikApplication
            return ViewModelFactory(app, app.container.documentRepository)
        }
    }
}
