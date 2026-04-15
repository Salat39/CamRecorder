package com.salat.archive.presentation.entity

import androidx.compose.runtime.Immutable

@Immutable
data class DisplayArchiveDay(
    val id: String,
    val title: String,
    val tracks: List<DisplayArchiveCameraTrack>,
    val windowStartMillisOfDay: Int,
    val windowEndMillisOfDay: Int,
)
