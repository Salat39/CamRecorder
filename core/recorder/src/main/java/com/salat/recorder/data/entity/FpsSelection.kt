package com.salat.recorder.data.entity

import android.util.Range

internal data class FpsSelection(
    val range: Range<Int>,
    val frameRate: Int
)
