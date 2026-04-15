package com.salat.recorder.data.repository

import com.salat.preferences.domain.entity.LongPref
import com.salat.recorder.data.entity.SegmentOutputMode

internal data class CameraRuntimeConfig(
    val cameraId: String,
    val enabled: Boolean,
    val fps: Int,
    val outputMode: SegmentOutputMode,
)

internal data class RecorderConfigSnapshot(
    val cameraConfigs: Map<String, CameraRuntimeConfig> = emptyMap(),
    val segmentDurationMs: Long = LongPref.SegmentDurationMs.default,
) {
    fun configFor(cameraId: String): CameraRuntimeConfig {
        return cameraConfigs[cameraId] ?: CameraRuntimeConfig(
            cameraId = cameraId,
            enabled = false,
            fps = 20,
            outputMode = SegmentOutputMode.RAW_AVC,
        )
    }
}
