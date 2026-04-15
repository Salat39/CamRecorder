package com.salat.archive.data.entity

import com.salat.archive.domain.entity.ArchiveCameraType
import com.salat.archive.domain.entity.ArchiveDayId
import com.salat.archive.domain.entity.ArchiveFileExtension
import com.salat.archive.domain.entity.ArchiveFileStatus

internal data class ArchiveParsedFile(
    val absolutePath: String,
    val fileName: String,
    val dayId: ArchiveDayId?,
    val cameraType: ArchiveCameraType?,
    val extension: ArchiveFileExtension?,
    val durationSec: Int?,
    val fps: Int?,
    val startMillisOfDay: Int?,
    val status: ArchiveFileStatus,
)
