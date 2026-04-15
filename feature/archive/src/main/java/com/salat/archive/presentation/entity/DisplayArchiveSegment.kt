package com.salat.archive.presentation.entity

import androidx.compose.runtime.Immutable
import com.salat.archive.domain.entity.ArchiveCameraType

@Immutable
data class DisplayArchiveSegment(
    val id: String,
    val cameraType: ArchiveCameraType,
    val startMillisOfDay: Int,
    val endMillisOfDay: Int,
    val startFraction: Float,
    val endFraction: Float,
    val sourceRecordIds: List<String>,
    val sourceFilePaths: List<String>,
)
