package com.salat.settings.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.salat.commonconst.CAMERA_OUTPUT_TYPE_AVC
import com.salat.commonconst.CAMERA_OUTPUT_TYPE_TS
import com.salat.commonconst.MAX_CAMERA_FPS
import com.salat.commonconst.MIN_CAMERA_FPS
import com.salat.commonconst.defaultCameraFps
import com.salat.resources.R
import com.salat.settings.presentation.entity.DisplayCameraSettings
import com.salat.uikit.component.RenderSwitcher
import com.salat.uikit.component.ValueSlider
import com.salat.uikit.theme.AppTheme

@Composable
internal fun CameraSettingsCard(
    camera: DisplayCameraSettings,
    onEnabledChange: (Boolean) -> Unit,
    onOutputTypeChange: (Int) -> Unit,
    onFpsChange: (Int) -> Unit,
) {
    val shape = remember { RoundedCornerShape(16.dp) }
    val chipShape = remember { RoundedCornerShape(14.dp) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape = shape)
            .clip(shape)
            .background(AppTheme.colors.surfaceSettingsLayer1)
            .padding(vertical = 8.dp)
    ) {
//        Text(
//            text = cameraTitleForId(camera.cameraId),
//            modifier = Modifier.padding(horizontal = 23.dp, vertical = 12.dp),
//            color = AppTheme.colors.settingsTitleAccent,
//            style = AppTheme.typography.settingsTitle
//        )

        RenderSwitcher(
            title = cameraTitleForId(camera.cameraId),
            value = camera.enabled,
            onSubtitle = stringResource(R.string.settings_camera_recording_on),
            offSubtitle = stringResource(R.string.settings_camera_recording_off),
            groupDivider = false,

            onChange = onEnabledChange
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 23.dp),
                text = stringResource(R.string.settings_camera_format_title),
                color = AppTheme.colors.contentPrimary,
                style = AppTheme.typography.screenTitle
            )

            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = camera.outputType == CAMERA_OUTPUT_TYPE_AVC,
                    onClick = { onOutputTypeChange(CAMERA_OUTPUT_TYPE_AVC) },
                    shape = chipShape,
                    label = {
                        Text(
                            text = stringResource(R.string.settings_camera_format_avc),
                            style = AppTheme.typography.buttonTitle,
                            modifier = Modifier.padding(vertical = 9.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = AppTheme.colors.surfaceSettingsLayer1,
                        labelColor = AppTheme.colors.contentPrimary,
                        selectedContainerColor = AppTheme.colors.contentAccent,
                        selectedLabelColor = AppTheme.colors.contentPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = camera.outputType == CAMERA_OUTPUT_TYPE_AVC,
                        borderColor = AppTheme.colors.sliderPassive,
                        selectedBorderColor = AppTheme.colors.contentAccent
                    )
                )
                FilterChip(
                    selected = camera.outputType == CAMERA_OUTPUT_TYPE_TS,
                    onClick = { onOutputTypeChange(CAMERA_OUTPUT_TYPE_TS) },
                    shape = chipShape,
                    label = {
                        Text(
                            text = stringResource(R.string.settings_camera_format_ts),
                            style = AppTheme.typography.buttonTitle,
                            modifier = Modifier.padding(vertical = 9.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = AppTheme.colors.surfaceSettingsLayer1,
                        labelColor = AppTheme.colors.contentPrimary,
                        selectedContainerColor = AppTheme.colors.contentAccent,
                        selectedLabelColor = AppTheme.colors.contentPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = camera.outputType == CAMERA_OUTPUT_TYPE_TS,
                        borderColor = AppTheme.colors.sliderPassive,
                        selectedBorderColor = AppTheme.colors.contentAccent
                    )
                )
            }
        }

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
                text = stringResource(R.string.settings_camera_fps, camera.fps),
                color = AppTheme.colors.contentPrimary
            )
            ValueSlider(
                value = camera.fps,
                valueRange = MIN_CAMERA_FPS..MAX_CAMERA_FPS,
                step = 1,
                defaultMark = camera.cameraId.defaultCameraFps,
                onValueChange = onFpsChange
            )
        }
    }
}

@Composable
private fun cameraTitleForId(cameraId: String): String = when (cameraId) {
    "0" -> stringResource(R.string.settings_camera_left)
    "1" -> stringResource(R.string.settings_camera_right)
    "2" -> stringResource(R.string.settings_camera_front)
    "3" -> stringResource(R.string.settings_camera_rear)
    else -> stringResource(R.string.settings_camera_generic, cameraId)
}
