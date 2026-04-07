package com.salat.recorder.data.utils

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaRecorder
import android.util.Range
import android.util.Size
import android.view.Surface
import timber.log.Timber

internal fun logCameraCapabilities(cameraManager: CameraManager) {
    val cameraIds = runCatching { cameraManager.cameraIdList.toList() }
        .getOrElse { throwable ->
            Timber.e(throwable, "Failed to read cameraIdList for capability logging")
            return
        }

    cameraIds.forEach { cameraId ->
        runCatching {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val configurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                ?.filterNotNull()
                .orEmpty()

            val privateSizes = configurationMap.safeOutputSizes(ImageFormat.PRIVATE)
            val surfaceSizes = configurationMap.safeOutputSizes(Surface::class.java)
            val mediaRecorderSizes = configurationMap.safeOutputSizes(MediaRecorder::class.java)

            Timber.e(
                "Camera capabilities cameraId=%s privateSizes=%s " +
                    "surfaceSizes=%s mediaRecorderSizes=%s fpsRanges=%s",
                cameraId,
                privateSizes.formatSizesForLog(),
                surfaceSizes.formatSizesForLog(),
                mediaRecorderSizes.formatSizesForLog(),
                fpsRanges.formatRangesForLog(),
            )
        }.onFailure { throwable ->
            Timber.e(throwable, "Failed to log capabilities for cameraId=%s", cameraId)
        }
    }
}

private fun StreamConfigurationMap?.safeOutputSizes(format: Int): List<Size> {
    if (this == null) return emptyList()
    return runCatching { getOutputSizes(format)?.toList().orEmpty() }
        .getOrElse { emptyList() }
        .distinctBy { it.width to it.height }
        .sortedWith(compareByDescending<Size> { it.width.toLong() * it.height.toLong() }.thenByDescending { it.width })
}

private fun StreamConfigurationMap?.safeOutputSizes(outputClass: Class<*>): List<Size> {
    if (this == null) return emptyList()
    return runCatching { getOutputSizes(outputClass)?.toList().orEmpty() }
        .getOrElse { emptyList() }
        .distinctBy { it.width to it.height }
        .sortedWith(compareByDescending<Size> { it.width.toLong() * it.height.toLong() }.thenByDescending { it.width })
}

private fun List<Size>.formatSizesForLog(): String {
    if (isEmpty()) return "[]"
    return joinToString(prefix = "[", postfix = "]") { size ->
        "${size.width}x${size.height}"
    }
}

private fun List<Range<Int>>.formatRangesForLog(): String {
    if (isEmpty()) return "[]"
    return joinToString(prefix = "[", postfix = "]") { range ->
        "${range.lower}..${range.upper}"
    }
}
