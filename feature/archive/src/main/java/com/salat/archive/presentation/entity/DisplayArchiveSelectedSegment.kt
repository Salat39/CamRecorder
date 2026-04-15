package com.salat.archive.presentation.entity

import androidx.compose.runtime.Immutable

@Immutable
data class DisplayArchiveSelectedSegment(
    val segmentId: String,
    val dayTitle: String,
    val cameraTitle: String,
    val startMillisOfDay: Int,
    val endMillisOfDay: Int,
    val sourceRecordIds: List<String>,
    val sourceFilePaths: List<String>,
)
