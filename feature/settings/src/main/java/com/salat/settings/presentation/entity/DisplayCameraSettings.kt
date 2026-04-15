package com.salat.settings.presentation.entity

import androidx.compose.runtime.Immutable
import com.salat.commonconst.CAMERA_OUTPUT_TYPE_AVC
import com.salat.commonconst.CAMERA_OUTPUT_TYPE_TS
import com.salat.commonconst.MAX_CAMERA_FPS
import com.salat.commonconst.MIN_CAMERA_FPS
import com.salat.commonconst.defaultCameraEnable
import com.salat.commonconst.defaultCameraFps
import com.salat.preferences.domain.entity.CameraDataStoreConfig

@Immutable
data class DisplayCameraSettings(
    val cameraId: String,
    val enabled: Boolean?,
    val fps: Int,
    val outputType: Int,
)

internal fun defaultCameraSettingsConfig(cameraId: String) = CameraDataStoreConfig(
    cameraId = cameraId,
    enabled = cameraId.defaultCameraEnable,
    fps = cameraId.defaultCameraFps.coerceIn(MIN_CAMERA_FPS, MAX_CAMERA_FPS),
    outputType = CAMERA_OUTPUT_TYPE_AVC,
)

internal fun CameraDataStoreConfig.normalizeCameraSettings() = copy(
    fps = fps.coerceIn(MIN_CAMERA_FPS, MAX_CAMERA_FPS),
    outputType = outputType.takeIf { it == CAMERA_OUTPUT_TYPE_AVC || it == CAMERA_OUTPUT_TYPE_TS }
        ?: CAMERA_OUTPUT_TYPE_AVC,
)

internal fun CameraDataStoreConfig.toDisplayCameraSettings(enabledOverride: Boolean? = enabled) = DisplayCameraSettings(
    cameraId = cameraId,
    enabled = enabledOverride,
    fps = fps,
    outputType = outputType,
)
