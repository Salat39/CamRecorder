package com.salat.preferences.domain.entity

data class CameraDataStoreConfig(
    val cameraId: String,
    val enabled: Boolean,
    val fps: Int,
    val outputType: Int,
)
