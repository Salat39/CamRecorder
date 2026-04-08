package com.salat.recorder.domain.usecases

import com.salat.recorder.domain.repository.RecorderRepository

class GetAvailableCamInfoUseCase(private val repository: RecorderRepository) {
    suspend fun execute() = repository.getAvailableCameraInfos()
}
