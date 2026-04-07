package com.salat.camrec.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.salat.camrec.BuildConfig
import com.salat.camrec.presentation.splash.pulseAnimation
import com.salat.navigation.mainGraph
import com.salat.navigation.routs.MainNavGraph
import com.salat.navigation.transitions.routedEnterTransition
import com.salat.navigation.transitions.routedExitTransition
import com.salat.navigation.transitions.routedPopEnterTransition
import com.salat.navigation.transitions.routedPopExitTransition
import com.salat.uikit.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().apply {
            setKeepOnScreenCondition {
                viewModel.splashScreenState.value
            }
            pulseAnimation()
        }
        enableEdgeToEdge(
            SystemBarStyle.dark(Color.Transparent.toArgb()),
            SystemBarStyle.dark(Color.Transparent.toArgb())
        )
        super.onCreate(savedInstanceState)
        setContent {
            val density = LocalDensity.current
            val scaledDensity = remember(density, BuildConfig.UI_SCALE) {
                Density(
                    density.density * BuildConfig.UI_SCALE,
                    density.fontScale * BuildConfig.UI_SCALE
                )
            }

            val navController = rememberNavController()

            AppTheme(darkTheme = true) {
                CompositionLocalProvider(
                    LocalDensity provides scaledDensity,
                    LocalLayoutDirection provides LayoutDirection.Ltr
                ) {
                    InitNavHost(navController)
                }
            }
        }

        // Check if intent was already handled to avoid re-processing
        if (intent?.flags?.and(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
            handleIntent(intent)
        }
    }

    private fun handleIntent(intent: Intent) {
        // Clear the intent to avoid re-processing on resume or relaunch
        intent.flags = intent.flags or Intent.FLAG_ACTIVITY_CLEAR_TASK
        setIntent(null)
    }
}

@Composable
private fun InitNavHost(navController: NavHostController) {
    // Background that is visible during screens transition animations
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.surfaceBackground)
    )

    NavHost(
        modifier = Modifier.fillMaxSize(),
        navController = navController,
        startDestination = MainNavGraph,
        enterTransition = {
            routedEnterTransition(initialState, targetState)
        },
        exitTransition = {
            routedExitTransition(initialState, targetState)
        },
        popEnterTransition = {
            routedPopEnterTransition(initialState, targetState)
        },
        popExitTransition = {
            routedPopExitTransition(initialState, targetState)
        }
    ) {
        mainGraph(navController)
    }
}
