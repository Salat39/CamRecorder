package com.salat.drivestorage.domain.usecases

import com.salat.drivestorage.domain.repository.DriveStorageRepository

class DriveConnectedUseCase(repository: DriveStorageRepository) {
    val flow = repository.driveConnectedFlow
}
