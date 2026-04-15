package com.salat.archive.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import com.salat.archive.presentation.route.ArchiveNavRoute

fun NavGraphBuilder.archiveScreen(onNavigateBack: () -> Unit) = composable<ArchiveNavRoute> {
    val viewModel: ArchiveViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(onBack = onNavigateBack)

    ArchiveMainScreen(
        state = state,
        sendAction = viewModel::sendAction,
        onNavigateBack = onNavigateBack
    )
}

fun NavController.navigateToArchive(builder: (NavOptionsBuilder.() -> Unit)? = null) =
    navigate(ArchiveNavRoute, builder ?: {})
