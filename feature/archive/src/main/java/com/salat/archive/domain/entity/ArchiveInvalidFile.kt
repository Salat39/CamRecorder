package com.salat.archive.domain.entity

import androidx.compose.runtime.Immutable

@Immutable
data class ArchiveInvalidFile(
    val id: String,
    val absolutePath: String,
    val fileName: String,
    val dayId: ArchiveDayId?,
    val cameraType: ArchiveCameraType?,
    val status: ArchiveFileStatus,
)
