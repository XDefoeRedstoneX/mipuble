package com.mipuble.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mipuble.ui.library.LibraryScreen
import com.mipuble.ui.reader.ReaderScreen
import com.mipuble.ui.settings.SettingsScreen

private object Routes {
    const val LIBRARY = "library"
    const val READER = "reader/{bookId}"
    const val SETTINGS = "settings"
    fun reader(bookId: Long) = "reader/$bookId"
}

@Composable
fun MipubleNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onOpenBook = { bookId -> navController.navigate(Routes.reader(bookId)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.READER,
            arguments = listOf(navArgument("bookId") { type = NavType.LongType }),
        ) {
            ReaderScreen(onBack = navController::popBackStack)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = navController::popBackStack)
        }
    }
}
