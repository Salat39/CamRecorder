package com.salat.drivestorage.domain.repository

import java.io.File
import kotlinx.coroutines.flow.Flow

interface DriveStorageRepository {
    val driveConnectedFlow: Flow<Boolean>

    suspend fun fullFileAccessGranted()

    fun getRemovableDriveRootOrNull(): File?

    fun prepareCameraDirectory(cameraAlias: String): File
}
