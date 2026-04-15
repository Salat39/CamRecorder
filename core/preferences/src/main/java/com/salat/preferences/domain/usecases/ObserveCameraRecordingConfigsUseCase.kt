package com.salat.preferences.domain.usecases

import com.salat.preferences.domain.DataStoreRepository
import com.salat.preferences.domain.entity.CameraDataStoreConfig

class ObserveCameraRecordingConfigsUseCase(private val preferences: DataStoreRepository) {
    fun execute(defaultConfigsByCameraId: Map<String, CameraDataStoreConfig>, minFps: Int, maxFps: Int) =
        preferences.getCameraRecordingConfigsFlow(
            defaultConfigsByCameraId = defaultConfigsByCameraId,
            minFps = minFps,
            maxFps = maxFps,
        )
}
