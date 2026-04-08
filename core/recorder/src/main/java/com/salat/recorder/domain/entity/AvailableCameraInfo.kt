package com.salat.recorder.domain.entity

data class AvailableCameraInfo(
    val cameraId: String,
    val lensFacing: Int?,
    val sensorOrientation: Int?,
    val hardwareLevel: Int?,
    val isLogicalMultiCamera: Boolean,
    val physicalCameraIds: List<String>,
    val activeArraySize: AvailableCameraSize?,
    val defaultPreviewSize: AvailableCameraSize?,
    val previewSizes: List<AvailableCameraSize>,
    val defaultVideoSize: AvailableCameraSize?,
    val videoSizes: List<AvailableCameraSize>,
    val targetFrameRate: Int?,
    val targetFpsRange: AvailableCameraFpsRange?,
    val capabilities: List<Int>
)
