package com.salat.recorder.data.entity

import android.util.Range
import android.util.Size

internal data class CameraDescriptor(
    val cameraId: String,
    val alias: String,
    val fileAlias: String,
    val videoSize: Size,
    val fpsRange: Range<Int>,
    val frameRate: Int,
    val videoBitrate: Int,
    val outputFormat: Int,
    val fileExtension: String
)
