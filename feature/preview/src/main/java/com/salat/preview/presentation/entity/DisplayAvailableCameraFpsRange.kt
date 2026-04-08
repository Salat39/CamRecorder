package com.salat.preview.presentation.entity

import androidx.compose.runtime.Immutable

@Immutable
data class DisplayAvailableCameraFpsRange(
    val min: Int,
    val max: Int,
    val label: String
)
