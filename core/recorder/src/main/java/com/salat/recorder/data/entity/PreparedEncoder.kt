package com.salat.recorder.data.entity

import android.media.MediaCodec

internal data class PreparedEncoder(
    val codec: MediaCodec,
    val config: EncoderCandidateConfig
)
