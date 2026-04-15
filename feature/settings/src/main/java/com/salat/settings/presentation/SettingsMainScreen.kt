package com.salat.settings.presentation

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salat.commonconst.SEGMENT_DURATION_MS
import com.salat.resources.R
import com.salat.settings.presentation.components.CameraSettingsCard
import com.salat.uikit.component.RenderSwitcher
import com.salat.uikit.component.TopShadow
import com.salat.uikit.component.ValueSlider
import com.salat.uikit.theme.AppTheme

private const val BLOCKS_PADDING = 20
private const val MS_IN_SECOND = 1_000L
private const val SECONDS_IN_MINUTE = 60L

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
internal fun SettingsMainScreen(
    state: SettingsViewModel.ViewState,
    sendAction: (SettingsViewModel.Action) -> Unit = {},
    onNavigateBack: () -> Unit
) = Scaffold { innerPadding ->
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(16.dp))
            IconButton(
                modifier = Modifier
                    .size(56.dp)
                    .padding(start = 2.dp),
                onClick = onNavigateBack
            ) {
                Icon(
                    modifier = Modifier,
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    tint = AppTheme.colors.contentPrimary,
                    contentDescription = stringResource(R.string.content_description_back)
                )
            }

            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.settings_screen_title),
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
                .fillMaxWidth()
                .background(AppTheme.colors.surfaceSettings)
        ) {
            val minCardWidth = 320.dp
            val isWideLayout = maxWidth >= minCardWidth * 2 + BLOCKS_PADDING.dp

            TopShadow()

            when (state.hasCameraPermission) {
                false -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.settings_camera_permission_required),
                            modifier = Modifier.padding(horizontal = 24.dp),
                            color = AppTheme.colors.contentPrimary,
                            style = AppTheme.typography.screenTitle,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                true -> {
                    if (state.cameras.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.settings_no_cameras_found),
                                modifier = Modifier.padding(horizontal = 24.dp),
                                color = AppTheme.colors.contentPrimary,
                                style = AppTheme.typography.screenTitle,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            modifier = Modifier.fillMaxSize(),
                            columns = GridCells.Fixed(if (isWideLayout) 2 else 1),
                            contentPadding = PaddingValues(BLOCKS_PADDING.dp),
                            verticalArrangement = Arrangement.spacedBy(BLOCKS_PADDING.dp),
                            horizontalArrangement = Arrangement.spacedBy(BLOCKS_PADDING.dp)
                        ) {
                            items(
                                items = state.cameras,
                                key = { it.cameraId }
                            ) { camera ->
                                CameraSettingsCard(
                                    camera = camera,
                                    onEnabledChange = { enabled ->
                                        sendAction(
                                            SettingsViewModel.Action.ChangeCameraEnabled(
                                                cameraId = camera.cameraId,
                                                enabled = enabled,
                                            )
                                        )
                                    },
                                    onOutputTypeChange = { outputType ->
                                        sendAction(
                                            SettingsViewModel.Action.ChangeCameraOutputType(
                                                cameraId = camera.cameraId,
                                                outputType = outputType,
                                            )
                                        )
                                    },
                                    onFpsChange = { fps ->
                                        sendAction(
                                            SettingsViewModel.Action.ChangeCameraFps(
                                                cameraId = camera.cameraId,
                                                fps = fps,
                                            )
                                        )
                                    }
                                )
                            }

                            item(
                                key = -2,
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                val shape = remember { RoundedCornerShape(16.dp) }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .shadow(4.dp, shape = shape)
                                        .clip(shape)
                                        .background(AppTheme.colors.surfaceSettingsLayer1)
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_general_title),
                                        modifier = Modifier.padding(horizontal = 23.dp, vertical = 12.dp),
                                        color = AppTheme.colors.settingsTitleAccent,
                                        style = AppTheme.typography.settingsTitle
                                    )

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 13.dp, end = 13.dp, top = 8.dp)
                                    ) {
                                        Text(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp),
                                            textAlign = TextAlign.Left,
                                            style = AppTheme.typography.screenTitle,
                                            text = formatSegmentDurationTitle(state.segmentDurationMs),
                                            color = AppTheme.colors.contentPrimary
                                        )
                                        ValueSlider(
                                            value = state.segmentDurationMs,
                                            valueRange = MIN_SEGMENT_DURATION_MS..MAX_SEGMENT_DURATION_MS,
                                            step = SEGMENT_DURATION_STEP_MS,
                                            defaultMark = SEGMENT_DURATION_MS,
                                            onValueChange = { value ->
                                                sendAction(SettingsViewModel.Action.ChangeSegmentDurationMs(value))
                                            }
                                        )
                                    }

                                    RenderSwitcher(
                                        title = stringResource(R.string.preview_switch_ignition_title),
                                        subtitle = if (state.forceRecord != true) {
                                            stringResource(R.string.preview_switch_ignition_subtitle_ignition_only)
                                        } else {
                                            stringResource(R.string.preview_switch_ignition_subtitle_always)
                                        },
                                        groupDivider = false,
                                        value = state.forceRecord?.let { !it },
                                        onChange = { isEnabled ->
                                            sendAction(SettingsViewModel.Action.SetForceRecord(!isEnabled))
                                        }
                                    )
                                }
                            }
                            item(
                                key = -3,
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                Spacer(Modifier.height(48.dp))
                            }
                        }
                    }
                }

                null -> Unit
            }
        }
    }
}

@Composable
private fun formatSegmentDurationTitle(value: Long): String {
    val totalSeconds = value / MS_IN_SECOND
    return if (totalSeconds >= SECONDS_IN_MINUTE) {
        stringResource(
            R.string.settings_segment_duration_minutes,
            (totalSeconds / SECONDS_IN_MINUTE).toInt()
        )
    } else {
        stringResource(
            R.string.settings_segment_duration_seconds,
            totalSeconds.toInt()
        )
    }
}
