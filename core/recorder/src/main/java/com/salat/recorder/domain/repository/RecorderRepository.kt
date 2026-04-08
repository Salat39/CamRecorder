package com.salat.recorder.domain.repository

import com.salat.recorder.domain.entity.AvailableCameraInfo

interface RecorderRepository {
    fun watchdog()

    suspend fun getAvailableCameraInfos(): List<AvailableCameraInfo>

    val hasCameraPermission: Boolean
}
