package com.salat.preview.presentation.entity

import androidx.compose.runtime.Immutable

@Immutable
data class DisplayAvailableCameraSize(
    val width: Int,
    val height: Int,
    val label: String
)
