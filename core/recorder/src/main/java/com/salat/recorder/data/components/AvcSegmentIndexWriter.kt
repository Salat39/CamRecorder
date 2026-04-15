package com.salat.recorder.data.components

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

internal class AvcSegmentIndexWriter(
    private val ioBufferBytes: Int,
    private val syncOnSegmentClose: Boolean,
    private val declaredFrameRate: Int,
) {
    companion object {
        private val FILE_MAGIC = byteArrayOf(
            'A'.code.toByte(),
            'V'.code.toByte(),
            'C'.code.toByte(),
            'I'.code.toByte(),
            'D'.code.toByte(),
            'X'.code.toByte(),
            '1'.code.toByte(),
            '\n'.code.toByte(),
        )
        private const val FILE_VERSION = 1
        private const val HEADER_SIZE_BYTES = 64
        private const val RECORD_SIZE_BYTES = 32
        private const val RECORD_MAGIC = 0x31445849
        private const val NO_PRESENTATION_TIME_US = -1L
        const val FLAG_KEY_FRAME = 1
        const val FLAG_CODEC_CONFIG = 1 shl 1
    }

    private var activeFile: File? = null
    private var activeFileStream: FileOutputStream? = null
    private var activeOutput: BufferedOutputStream? = null

    private val headerScratch = ByteBuffer.allocate(HEADER_SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    private val recordScratch = ByteBuffer.allocate(RECORD_SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    private val crc32 = CRC32()

    fun open(file: File) {
        close()
        ensureParentDirectory(file)
        val fileStream = FileOutputStream(file, false)
        activeFile = file
        activeFileStream = fileStream
        activeOutput = BufferedOutputStream(fileStream, ioBufferBytes)
        writeHeader()
    }

    fun appendCodecConfig(fileOffset: Long, sampleSize: Int) {
        appendRecord(
            flags = FLAG_CODEC_CONFIG,
            sampleSize = sampleSize,
            fileOffset = fileOffset,
            presentationTimeUs = NO_PRESENTATION_TIME_US,
        )
    }

    fun appendSample(fileOffset: Long, sampleSize: Int, presentationTimeUs: Long, keyFrame: Boolean) {
        appendRecord(
            flags = if (keyFrame) FLAG_KEY_FRAME else 0,
            sampleSize = sampleSize,
            fileOffset = fileOffset,
            presentationTimeUs = presentationTimeUs,
        )
    }

    fun getCurrentFileOrNull(): File? = activeFile

    fun close() {
        val output = activeOutput
        activeOutput = null
        runCatching { output?.flush() }

        val fileStream = activeFileStream
        activeFileStream = null
        if (syncOnSegmentClose) {
            runCatching { fileStream?.fd?.sync() }
        }
        runCatching { output?.close() }
        runCatching { fileStream?.close() }
        activeFile = null
    }

    private fun writeHeader() {
        val output = activeOutput ?: return
        val bytes = headerScratch.array()
        headerScratch.clear()
        bytes.fill(0)
        headerScratch.put(FILE_MAGIC)
        headerScratch.putInt(FILE_VERSION)
        headerScratch.putInt(HEADER_SIZE_BYTES)
        headerScratch.putInt(RECORD_SIZE_BYTES)
        headerScratch.putInt(declaredFrameRate)
        headerScratch.putLong(System.currentTimeMillis())
        repeat(4) {
            headerScratch.putLong(0L)
        }
        output.write(bytes, 0, HEADER_SIZE_BYTES)
    }

    private fun appendRecord(flags: Int, sampleSize: Int, fileOffset: Long, presentationTimeUs: Long) {
        if (sampleSize <= 0) return
        val output = activeOutput ?: return
        val bytes = recordScratch.array()
        recordScratch.clear()
        recordScratch.putInt(RECORD_MAGIC)
        recordScratch.putInt(flags)
        recordScratch.putInt(sampleSize)
        recordScratch.putLong(fileOffset)
        recordScratch.putLong(presentationTimeUs)
        crc32.reset()
        crc32.update(bytes, 0, RECORD_SIZE_BYTES - Int.SIZE_BYTES)
        recordScratch.putInt(crc32.value.toInt())
        output.write(bytes, 0, RECORD_SIZE_BYTES)
    }

    private fun ensureParentDirectory(file: File) {
        val parent = file.parentFile ?: return
        if (parent.exists()) {
            check(parent.isDirectory) { "Parent path is not a directory: ${parent.absolutePath}" }
            return
        }

        if (!parent.mkdirs() && !parent.isDirectory) {
            error("Unable to create ${parent.absolutePath}")
        }
    }
}
