package com.salat.drivestorage.domain.usecases

import com.salat.drivestorage.domain.repository.DriveStorageRepository

class FullFileAccessGrantedUseCase(private val repository: DriveStorageRepository) {
    suspend fun execute() = repository.fullFileAccessGranted()
}
