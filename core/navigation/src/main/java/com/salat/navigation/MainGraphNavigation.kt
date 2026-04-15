package com.salat.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.navigation
import com.salat.archive.presentation.archiveScreen
import com.salat.archive.presentation.navigateToArchive
import com.salat.navigation.routs.MainNavGraph
import com.salat.preview.presentation.previewScreen
import com.salat.preview.presentation.route.PreviewNavRoute
import com.salat.settings.presentation.navigateToSettings
import com.salat.settings.presentation.settingsScreen

fun NavGraphBuilder.mainGraph(navController: NavController) = navigation<MainNavGraph>(
    startDestination = PreviewNavRoute
) {
    previewScreen(
        navigateToSettings = navController::navigateToSettings,
        navigateToArchive = navController::navigateToArchive
    )
    settingsScreen(onNavigateBack = navController::navigateUp)
    archiveScreen(onNavigateBack = navController::navigateUp)
}
