package com.example.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.BookmarksScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.viewmodel.BookmarksViewModel
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.ReaderViewModel
import com.example.ui.viewmodel.ViewModelFactory
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Destinations {
    const val HOME = "home"
    const val BOOKMARKS = "bookmarks"
    const val READER = "reader/{docUri}?page={page}&offset={offset}&query={query}"

    fun readerRoute(
        docUri: String,
        page: Int = 1,
        offset: Int = 0,
        query: String = ""
    ): String {
        val encodedUri = URLEncoder.encode(docUri, StandardCharsets.UTF_8.toString())
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        return "reader/$encodedUri?page=$page&offset=$offset&query=$encodedQuery"
    }
}

@Composable
fun LairikNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    initialDocumentUri: String? = null
) {
    val context = LocalContext.current

    val startDestination = if (initialDocumentUri != null) {
        Destinations.readerRoute(initialDocumentUri)
    } else {
        Destinations.HOME
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Destinations.HOME) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = ViewModelFactory.provideHomeFactory(context)
            )
            val bookmarksViewModel: BookmarksViewModel = viewModel(
                factory = ViewModelFactory.provideBookmarksFactory(context)
            )

            HomeScreen(
                viewModel = homeViewModel,
                bookmarksViewModel = bookmarksViewModel,
                onOpenDocument = { doc ->
                    navController.navigate(Destinations.readerRoute(doc.uri))
                },
                onOpenDocumentByUri = { uri, page ->
                    navController.navigate(Destinations.readerRoute(uri, page = page))
                },
                onOpenSearchResult = { result ->
                    navController.navigate(
                        Destinations.readerRoute(
                            docUri = result.documentUri,
                            page = result.pageNumber,
                            offset = result.charOffset,
                            query = result.highlightTerm
                        )
                    )
                }
            )
        }

        composable(Destinations.BOOKMARKS) {
            val bookmarksViewModel: BookmarksViewModel = viewModel(
                factory = ViewModelFactory.provideBookmarksFactory(context)
            )
            BookmarksScreen(
                viewModel = bookmarksViewModel,
                onOpenDocumentAtPage = { uri, page ->
                    navController.navigate(Destinations.readerRoute(uri, page = page))
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Destinations.READER,
            arguments = listOf(
                navArgument("docUri") { type = NavType.StringType },
                navArgument("page") {
                    type = NavType.IntType
                    defaultValue = 1
                },
                navArgument("offset") {
                    type = NavType.IntType
                    defaultValue = 0
                },
                navArgument("query") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val encodedUri = backStackEntry.arguments?.getString("docUri") ?: ""
            val targetPage = backStackEntry.arguments?.getInt("page") ?: 1
            val targetOffset = backStackEntry.arguments?.getInt("offset") ?: 0
            val rawQuery = backStackEntry.arguments?.getString("query") ?: ""
            val decodedUri = try {
                URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.toString())
            } catch (e: Exception) {
                encodedUri
            }
            val decodedQuery = try {
                URLDecoder.decode(rawQuery, StandardCharsets.UTF_8.toString())
            } catch (e: Exception) {
                rawQuery
            }

            val readerViewModel: ReaderViewModel = viewModel(
                key = "$decodedUri-$targetPage-$targetOffset-$decodedQuery",
                factory = ViewModelFactory.provideReaderFactory(
                    context = context,
                    documentUri = decodedUri,
                    initialPage = targetPage,
                    initialOffset = targetOffset,
                    query = decodedQuery
                )
            )

            ReaderScreen(
                viewModel = readerViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
