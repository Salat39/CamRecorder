package com.salat.archive.domain.entity

import androidx.compose.runtime.Immutable

@Immutable
data class ArchiveSegment(
    val id: String,
    val cameraType: ArchiveCameraType,
    val startMillisOfDay: Int,
    val endMillisOfDay: Int,
    val sourceRecordIds: List<String>,
    val sourceFilePaths: List<String>,
)
