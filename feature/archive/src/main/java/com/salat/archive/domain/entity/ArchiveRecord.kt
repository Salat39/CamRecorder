package com.salat.archive.domain.entity

import androidx.compose.runtime.Immutable

@Immutable
data class ArchiveRecord(
    val id: String,
    val absolutePath: String,
    val fileName: String,
    val folderDayId: ArchiveDayId,
    val cameraType: ArchiveCameraType,
    val extension: ArchiveFileExtension,
    val durationSec: Int,
    val fps: Int?,
    val startMillisOfDay: Int,
    val endMillisOfDay: Int,
)
