package com.salat.preferences.domain.usecases

import com.salat.preferences.domain.DataStoreRepository
import com.salat.preferences.domain.entity.CameraDataStoreConfig

class SaveCameraRecordingConfigUseCase(private val preferences: DataStoreRepository) {
    suspend fun execute(config: CameraDataStoreConfig) = preferences.saveCameraRecordingConfig(config)
}
