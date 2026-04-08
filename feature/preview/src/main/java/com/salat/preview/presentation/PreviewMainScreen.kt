package com.salat.preview.presentation

import android.Manifest
import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salat.preview.presentation.components.CameraPreviewView
import com.salat.preview.presentation.components.RecordingIndicator
import com.salat.preview.presentation.components.RenderStatusWarning
import com.salat.preview.presentation.components.hasCameraPermission
import com.salat.preview.presentation.components.openManageAllFilesAccessSettings
import com.salat.preview.presentation.entity.ActionStatus
import com.salat.preview.presentation.entity.RecordingIndicatorState
import com.salat.preview.presentation.entity.WarningStatus
import com.salat.preview.presentation.mappers.buildCameraConfigInfo
import com.salat.resources.R
import com.salat.ui.clickableNoRipple
import com.salat.ui.rememberHasManageAllFilesAccess
import com.salat.uikit.component.RenderSwitcher
import com.salat.uikit.component.TopShadow
import com.salat.uikit.preview.PreviewScreen
import com.salat.uikit.theme.AppTheme
import presentation.getActivity
import presentation.openAccessibilityServiceSettings

private const val BLOCKS_PADDING = 20

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
internal fun PreviewMainScreen(state: PreviewViewModel.ViewState, sendAction: (PreviewViewModel.Action) -> Unit = {}) =
    Scaffold { innerPadding ->
        val context = LocalContext.current
        val gridState = rememberLazyGridState()
        val border = remember { RoundedCornerShape(16.dp) }
        val aspect = remember { 1280f / 800f }
        BackHandler { context.getActivity()?.moveTaskToBack(true) }

        val fileAccess = rememberHasManageAllFilesAccess()
        var cameraAccess by remember { mutableStateOf(context.hasCameraPermission()) }

        val requestPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            cameraAccess = isGranted
            if (isGranted) sendAction(PreviewViewModel.Action.GetCamerasInfo)
        }

        LaunchedEffect(fileAccess) {
            if (fileAccess) {
                sendAction(PreviewViewModel.Action.RefreshDriveConnected)
            }
        }

        val enableRecord = state.enableRecord ?: false
        val forceRecord = state.forceRecord ?: false
        val hasAccess = fileAccess && state.driveConnected && cameraAccess
        val recStatus = if (!hasAccess) {
            ActionStatus.WAITING_ACTION
        } else {
            if (enableRecord) {
                if (state.ignition || forceRecord) {
                    ActionStatus.CAN_STOP
                } else {
                    ActionStatus.WAITING_IGNITION
                }
            } else {
                ActionStatus.CAN_START
            }
        }

        val previewsBlocked = recStatus == ActionStatus.CAN_STOP

        val onEnable = rememberUpdatedState {
            if (!fileAccess) {
                context.openManageAllFilesAccessSettings()
                return@rememberUpdatedState
            }
            if (!cameraAccess) {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                return@rememberUpdatedState
            }

            if (!state.accessibilityServiceEnabled) {
                openAccessibilityServiceSettings(context)
                return@rememberUpdatedState
            }

            when (recStatus) {
                ActionStatus.WAITING_ACTION -> Unit

                ActionStatus.WAITING_IGNITION, ActionStatus.CAN_STOP -> sendAction(
                    PreviewViewModel.Action.SetEnableRec(false)
                )

                ActionStatus.CAN_START -> {
                    sendAction(PreviewViewModel.Action.HideAllCameraPreviews)
                    sendAction(PreviewViewModel.Action.SetEnableRec(true))
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.surfaceBackground)
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .systemBarsPadding()
                .imePadding()
        ) {
            // Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(24.dp))
                Text(
                    text = stringResource(R.string.app_label),
                    modifier = Modifier.weight(1f, false),
                    color = AppTheme.colors.contentPrimary,
                    style = AppTheme.typography.toolbar,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )

                Spacer(Modifier.width(10.dp))
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .background(AppTheme.colors.surfaceSettings)
            ) {
                val minCardWidth = 320.dp
                val isWideLayout = maxWidth >= minCardWidth * 2 + BLOCKS_PADDING.dp

                TopShadow()

                LazyVerticalGrid(
                    modifier = Modifier.fillMaxSize(),
                    state = gridState,
                    columns = GridCells.Fixed(if (isWideLayout) 2 else 1),
                    contentPadding = PaddingValues(BLOCKS_PADDING.dp),
                    verticalArrangement = Arrangement.spacedBy(BLOCKS_PADDING.dp),
                    horizontalArrangement = Arrangement.spacedBy(BLOCKS_PADDING.dp)
                ) {
                    item(
                        key = -1,
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            PreviewCardsLayout(
                                isWideLayout = isWideLayout,
                                firstCard = { cardModifier ->
                                    Row(
                                        modifier = cardModifier
                                            .shadow(4.dp, shape = border)
                                            .background(AppTheme.colors.surfaceSettingsLayer1)
                                            .clickableNoRipple(onClick = onEnable.value),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Spacer(Modifier.width(8.dp))

                                        RecordingIndicator(
                                            size = 76.dp,
                                            showLabel = false,
                                            state = when (recStatus) {
                                                ActionStatus.WAITING_ACTION -> RecordingIndicatorState.WARNING
                                                ActionStatus.WAITING_IGNITION -> RecordingIndicatorState.READY
                                                ActionStatus.CAN_START -> RecordingIndicatorState.DISABLED
                                                ActionStatus.CAN_STOP -> RecordingIndicatorState.REC
                                            },
                                            glowIntensity = .6f,
                                            pulseScaleIntensity = .3f,
                                            centerHighlightIntensity = .9f
                                        )

                                        Spacer(Modifier.width(2.dp))

                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = when (recStatus) {
                                                    ActionStatus.WAITING_ACTION -> stringResource(
                                                        R.string.preview_status_waiting_action
                                                    )

                                                    ActionStatus.WAITING_IGNITION -> stringResource(
                                                        R.string.preview_status_waiting_ignition
                                                    )

                                                    ActionStatus.CAN_START -> stringResource(
                                                        R.string.preview_status_off
                                                    )

                                                    ActionStatus.CAN_STOP -> stringResource(
                                                        R.string.preview_status_recording
                                                    )
                                                },
                                                style = AppTheme.typography.previewTitle,
                                                color = AppTheme.colors.contentPrimary
                                            )

                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                if (!fileAccess) {
                                                    RenderStatusWarning(
                                                        text = stringResource(R.string.preview_warning_no_file_access),
                                                        type = WarningStatus.ERROR
                                                    )
                                                } else if (!state.driveConnected) {
                                                    RenderStatusWarning(
                                                        text = stringResource(R.string.preview_warning_insert_usb),
                                                        type = WarningStatus.WARNING
                                                    )
                                                }

                                                if (!cameraAccess) {
                                                    RenderStatusWarning(
                                                        text = stringResource(
                                                            R.string.preview_warning_no_camera_access
                                                        ),
                                                        type = WarningStatus.ERROR
                                                    )
                                                }

                                                if (!state.accessibilityServiceEnabled) {
                                                    RenderStatusWarning(
                                                        text = stringResource(
                                                            R.string.preview_warning_no_accessibility_service
                                                        ),
                                                        type = WarningStatus.WARNING
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(Modifier.width(16.dp))
                                    }
                                },
                                secondCard = { cardModifier ->
                                    Column(
                                        modifier = cardModifier
                                            .shadow(4.dp, shape = border)
                                            .background(AppTheme.colors.surfaceSettingsLayer1)
                                            .padding(vertical = 8.dp)
                                    ) {
                                        RenderSwitcher(
                                            title = stringResource(R.string.preview_switch_enable_title),
                                            subtitle = if (enableRecord) {
                                                stringResource(R.string.preview_switch_enable_subtitle_on)
                                            } else {
                                                stringResource(R.string.preview_switch_enable_subtitle_off)
                                            },
                                            groupDivider = false,
                                            value = state.enableRecord,
                                            onChange = { _ -> onEnable.value.invoke() }
                                        )

                                        RenderSwitcher(
                                            title = stringResource(R.string.preview_switch_ignition_title),
                                            subtitle = if (!forceRecord) {
                                                stringResource(R.string.preview_switch_ignition_subtitle_ignition_only)
                                            } else {
                                                stringResource(R.string.preview_switch_ignition_subtitle_always)
                                            },
                                            groupDivider = false,
                                            value = state.forceRecord?.let { !it },
                                            onChange = { isEnabled ->
                                                sendAction(PreviewViewModel.Action.SetForceRecord(!isEnabled))
                                            }
                                        )
                                    }
                                }
                            )

                            if (state.cameras.isNotEmpty()) {
                                Spacer(Modifier.height(32.dp))

                                Text(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = stringResource(R.string.available_cameras),
                                    textAlign = TextAlign.Center,
                                    style = AppTheme.typography.previewTitle,
                                    color = AppTheme.colors.contentPrimary.copy(.9f)
                                )

                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                    items(
                        items = state.cameras,
                        key = { item -> item.cameraId }
                    ) { camera ->
                        val cameraConfigInfo = remember(camera) { buildCameraConfigInfo(camera) }

                        Box(
                            modifier = Modifier
                                .aspectRatio(aspect)
                                .shadow(4.dp, shape = border)
                                .background(AppTheme.colors.surfaceSettingsLayer1)
                        ) {
                            if (camera.showPreview && !previewsBlocked && cameraAccess) {
                                CameraPreviewView(
                                    modifier = Modifier.fillMaxSize(),
                                    cameraId = camera.cameraId,
                                    previewSize = camera.defaultPreviewSize ?: camera.defaultVideoSize,
                                    enabled = true,
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(end = 16.dp, top = 16.dp)
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (camera.showPreview) {
                                            AppTheme.colors.autoStart
                                        } else AppTheme.colors.surfaceMenu
                                    )
                                    .alpha(if (previewsBlocked) .2f else 1f)
                                    .clickable(enabled = !previewsBlocked) {
                                        sendAction(PreviewViewModel.Action.ToggleCameraPreview(camera.cameraId))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    modifier = Modifier
                                        .size(23.dp),
                                    painter = painterResource(R.drawable.ic_eye),
                                    tint = AppTheme.colors.contentPrimary,
                                    contentDescription = "vis"
                                )
                            }

                            // Info
                            Text(
                                modifier = Modifier
                                    .padding(start = 16.dp, top = 16.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AppTheme.colors.surfaceBackground.copy(alpha = .72f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                color = AppTheme.colors.contentPrimary,
                                style = AppTheme.typography.dialogSubtitle,
                                text = cameraConfigInfo
                            )
                        }
                    }
                    item(
                        key = -2,
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        Spacer(Modifier.height(48.dp))
                    }
                }
            }
        }
    }

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun PreviewCardsLayout(
    isWideLayout: Boolean,
    firstCard: @Composable (Modifier) -> Unit,
    secondCard: @Composable (Modifier) -> Unit
) {
    if (isWideLayout) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(BLOCKS_PADDING.dp)
        ) {
            firstCard(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            secondCard(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(BLOCKS_PADDING.dp)
        ) {
            firstCard(Modifier.fillMaxWidth())
            secondCard(Modifier.fillMaxWidth())
        }
    }
}

@Preview
@Composable
private fun ListScreenDataPreview() {
    PreviewScreen {
        PreviewMainScreen(
            state = PreviewViewModel.ViewState()
        )
    }
}
