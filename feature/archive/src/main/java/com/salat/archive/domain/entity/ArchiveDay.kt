package com.salat.archive.domain.entity

import androidx.compose.runtime.Immutable

@Immutable
data class ArchiveDay(
    val id: ArchiveDayId,
    val tracks: List<ArchiveCameraTrack>,
)
