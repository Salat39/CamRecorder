package com.salat.preview.presentation.mappers

import android.hardware.camera2.CameraCharacteristics
import com.salat.preview.presentation.entity.DisplayAvailableCamera
import com.salat.preview.presentation.entity.DisplayAvailableCameraFpsRange
import com.salat.preview.presentation.entity.DisplayAvailableCameraSize
import com.salat.recorder.domain.entity.AvailableCameraFpsRange
import com.salat.recorder.domain.entity.AvailableCameraInfo
import com.salat.recorder.domain.entity.AvailableCameraSize

fun AvailableCameraInfo.toDisplay(): DisplayAvailableCamera {
    val previewSizesUi = previewSizes.map { it.toDisplay() }
    val videoSizesUi = videoSizes.map { it.toDisplay() }
    val capabilitiesUi = capabilities.map { it.toCameraCapabilityLabel() }

    return DisplayAvailableCamera(
        cameraId = cameraId,
        title = buildCameraTitle(
            cameraId = cameraId,
            lensFacing = lensFacing,
        ),
        lensFacingLabel = cameraId.toRepoSideLabel(lensFacing),
        sensorOrientationLabel = sensorOrientation.toSensorOrientationLabel(),
        hardwareLevelLabel = hardwareLevel.toHardwareLevelLabel(),
        logicalMultiCameraLabel = if (isLogicalMultiCamera) "Logical multi camera" else "Physical camera",
        physicalCameraIdsLabel = physicalCameraIds.toDisplayLabel(),
        activeArraySize = activeArraySize?.toDisplay(),
        defaultPreviewSize = defaultPreviewSize?.toDisplay(),
        previewSizes = previewSizesUi,
        defaultVideoSize = defaultVideoSize?.toDisplay(),
        videoSizes = videoSizesUi,
        targetFrameRateLabel = targetFrameRate?.let { "$it fps" } ?: "Unknown fps",
        targetFpsRange = targetFpsRange?.toDisplay(),
        capabilities = capabilitiesUi,
        capabilitiesLabel = capabilitiesUi.joinToString(),
        showPreview = false,
        showInfo = false
    )
}

fun List<AvailableCameraInfo>.toDisplay() = map { it.toDisplay() }

fun AvailableCameraSize.toDisplay() = DisplayAvailableCameraSize(
    width = width,
    height = height,
    label = "$width×$height"
)

fun AvailableCameraFpsRange.toDisplay() = DisplayAvailableCameraFpsRange(
    min = min,
    max = max,
    label = "$min-$max fps"
)

private fun buildCameraTitle(cameraId: String, lensFacing: Int?) =
    "${cameraId.toRepoSideLabel(lensFacing)} camera ($cameraId)"

private fun String.toRepoSideLabel(fallbackLensFacing: Int?): String = when (toIntOrNull()) {
    0 -> "Left"
    1 -> "Right"
    2 -> "Front"
    3 -> "Back"
    null -> fallbackLensFacing.toRawLensFacingLabel()
    else -> "Other"
}

private fun Int?.toRawLensFacingLabel() = when (this) {
    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
    CameraCharacteristics.LENS_FACING_BACK -> "Back"
    CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
    null -> "Unknown"
    else -> "Other ($this)"
}

private fun Int?.toSensorOrientationLabel() = this?.let { "$it°" } ?: "Unknown"

private fun Int?.toHardwareLevelLabel() = when (this) {
    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"
    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3"
    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "External"
    null -> "Unknown"
    else -> "Other ($this)"
}

private fun Int.toCameraCapabilityLabel() = when (this) {
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE -> "Backward compatible"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> "Manual sensor"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> "Manual post processing"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW -> "RAW"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING -> "Private reprocessing"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS -> "Read sensor settings"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE -> "Burst capture"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING -> "YUV reprocessing"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT -> "Depth output"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO -> "High speed video"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING -> "Motion tracking"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA -> "Logical multi camera"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME -> "Monochrome"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA -> "Secure image data"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA -> "System camera"
    else -> "Capability $this"
}

private fun List<String>.toDisplayLabel(): String {
    return if (isEmpty()) {
        "None"
    } else {
        joinToString()
    }
}

internal fun buildCameraConfigInfo(camera: DisplayAvailableCamera): String {
    return buildList {
        add(
            camera.title.takeIf { it.isNotBlank() && it != camera.cameraId }
                ?.let { "${camera.cameraId} • $it" }
                ?: camera.cameraId
        )

        camera.lensFacingLabel.takeIf { it.isNotBlank() }?.let { add("Lens: $it") }
        camera.defaultPreviewSize?.label?.let { add("Preview: $it") }
        camera.defaultVideoSize?.label
            ?.takeIf { it != camera.defaultPreviewSize?.label }
            ?.let { add("Video: $it") }

        camera.targetFpsRange?.label?.let { add("FPS: $it") }
            ?: camera.targetFrameRateLabel.takeIf { it.isNotBlank() }?.let { add("FPS: $it") }

        camera.hardwareLevelLabel.takeIf { it.isNotBlank() }?.let { add("HW level: $it") }
        camera.sensorOrientationLabel.takeIf { it.isNotBlank() }?.let { add("Orientation: $it") }
        camera.logicalMultiCameraLabel.takeIf { it.isNotBlank() }?.let { add("Logical multi: $it") }
        camera.physicalCameraIdsLabel.takeIf { it.isNotBlank() }?.let { add("Physical ids: $it") }
    }.joinToString(separator = "\n")
}
