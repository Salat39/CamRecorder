package com.salat.preview.presentation

import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.salat.preview.presentation.route.PreviewNavRoute

fun NavGraphBuilder.previewScreen(navigateToSettings: () -> Unit, navigateToArchive: () -> Unit) =
    composable<PreviewNavRoute> {
        val viewModel: PreviewViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()

        PreviewMainScreen(
            state = state,
            sendAction = viewModel::sendAction,
            onOpenArchive = navigateToArchive,
            onOpenSettings = navigateToSettings
        )
    }
