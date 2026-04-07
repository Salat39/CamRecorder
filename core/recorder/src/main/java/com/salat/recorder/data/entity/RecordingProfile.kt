package com.salat.recorder.data.entity

import android.util.Size

internal data class RecordingProfile(
    val videoSize: Size,
    val fpsSelection: FpsSelection
)
