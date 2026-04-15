package com.salat.preview.presentation.entity

import androidx.compose.runtime.Immutable

@Immutable
data class DisplayAvailableCamera(
    val cameraId: String,
    val title: String,
    val lensFacingLabel: String,
    val sensorOrientationLabel: String,
    val hardwareLevelLabel: String,
    val logicalMultiCameraLabel: String,
    val physicalCameraIdsLabel: String,
    val activeArraySize: DisplayAvailableCameraSize?,
    val defaultPreviewSize: DisplayAvailableCameraSize?,
    val previewSizes: List<DisplayAvailableCameraSize>,
    val defaultVideoSize: DisplayAvailableCameraSize?,
    val videoSizes: List<DisplayAvailableCameraSize>,
    val targetFrameRateLabel: String,
    val targetFpsRange: DisplayAvailableCameraFpsRange?,
    val capabilities: List<String>,
    val capabilitiesLabel: String,
    val showPreview: Boolean,
    val showInfo: Boolean
)
