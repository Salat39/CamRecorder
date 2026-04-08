package com.salat.recorder.domain.usecases

import com.salat.recorder.domain.repository.RecorderRepository

data class HasCameraPermissionUseCase(private val repository: RecorderRepository) {
    val check
        get() = repository.hasCameraPermission
}
