package com.salat.recorder.data.components

import android.media.MediaCodec
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer

internal interface SegmentedVideoFileWriter {
    fun onOutputFormatChanged(format: MediaFormat)

    fun requestInitialSegment(file: File)

    fun requestRollover(file: File)

    fun getCurrentFileOrNull(): File?

    fun writeSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo)

    fun close(deleteActiveFile: Boolean)
}
