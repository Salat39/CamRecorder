package com.salat.recorder.data.entity

import java.io.File

internal data class SegmentGroup(
    val key: String,
    val files: List<File>
)
