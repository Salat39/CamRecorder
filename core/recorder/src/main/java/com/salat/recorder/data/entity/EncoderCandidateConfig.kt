package com.salat.recorder.data.entity

internal data class EncoderCandidateConfig(
    val codecName: String,
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val bitRate: Int
)
