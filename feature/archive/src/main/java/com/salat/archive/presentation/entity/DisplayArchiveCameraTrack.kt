package com.salat.archive.presentation.entity

import androidx.compose.runtime.Immutable
import com.salat.archive.domain.entity.ArchiveCameraType

@Immutable
data class DisplayArchiveCameraTrack(
    val cameraType: ArchiveCameraType,
    val segments: List<DisplayArchiveSegment>,
)
