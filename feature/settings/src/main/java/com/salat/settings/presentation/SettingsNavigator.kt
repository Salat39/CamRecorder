package com.salat.settings.presentation

import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import com.salat.settings.presentation.route.SettingsNavRoute

fun NavGraphBuilder.settingsScreen(onNavigateBack: () -> Unit) = composable<SettingsNavRoute> {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    SettingsMainScreen(
        state = state,
        sendAction = viewModel::sendAction,
        onNavigateBack = onNavigateBack
    )
}

fun NavController.navigateToSettings(builder: (NavOptionsBuilder.() -> Unit)? = null) =
    navigate(SettingsNavRoute, builder ?: {})
