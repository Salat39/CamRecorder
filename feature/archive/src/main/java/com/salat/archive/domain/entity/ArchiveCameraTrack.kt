package com.salat.archive.domain.entity

import androidx.compose.runtime.Immutable

@Immutable
data class ArchiveCameraTrack(
    val cameraType: ArchiveCameraType,
    val records: List<ArchiveRecord>,
    val segments: List<ArchiveSegment>,
)
