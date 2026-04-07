package com.salat.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.navigation
import com.salat.navigation.routs.MainNavGraph
import com.salat.preview.presentation.previewScreen
import com.salat.preview.presentation.route.PreviewNavRoute

fun NavGraphBuilder.mainGraph(navController: NavController) = navigation<MainNavGraph>(
    startDestination = PreviewNavRoute
) {
    previewScreen()
}
