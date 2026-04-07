package com.salat.recorder.data.components

import android.media.MediaCodec
import android.media.MediaFormat
import com.salat.recorder.data.entity.AvcCodecConfig
import com.salat.recorder.data.entity.ByteArraySlice
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import timber.log.Timber

internal class AvcSegmentedFileWriter(
    private val brokenFileDeleteThresholdBytes: Long,
    private val ioBufferBytes: Int,
    private val syncOnSegmentClose: Boolean,
) : SegmentedVideoFileWriter {
    private companion object {
        private val ANNEX_B_START_CODE = byteArrayOf(0x00, 0x00, 0x00, 0x01)
        private const val DEFAULT_NAL_LENGTH_FIELD_BYTES = 4
        private const val NO_BYTES_WRITTEN = -1
    }

    private var activeFile: File? = null
    private var activeFileStream: FileOutputStream? = null
    private var activeOutput: BufferedOutputStream? = null
    private var activeSegmentNeedsCodecConfig = false

    private var pendingFile: File? = null
    private var codecConfigBytes: ByteArray? = null
    private var nalLengthFieldBytes = DEFAULT_NAL_LENGTH_FIELD_BYTES

    private var sampleScratch = ByteArray(0)
    private var annexBScratch = ByteArray(0)

    @Synchronized
    override fun onOutputFormatChanged(format: MediaFormat) {
        codecConfigBytes = buildCodecConfigBytes(format)?.also {
            Timber.d("AVC codec config received: %s bytes", it.size)
        }
    }

    @Synchronized
    override fun requestInitialSegment(file: File) {
        replacePendingFile(file)
    }

    @Synchronized
    override fun requestRollover(file: File) {
        replacePendingFile(file)
    }

    @Synchronized
    override fun getCurrentFileOrNull(): File? = activeFile

    @Synchronized
    override fun writeSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (bufferInfo.size <= 0) return

        val sampleSize = readSampleBytes(buffer, bufferInfo)
        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
            codecConfigBytes = normalizeCodecConfig(sampleScratch, sampleSize)
            return
        }

        val normalized = normalizeSampleBytes(sampleScratch, sampleSize)
        val keyFrame = bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0

        if (activeOutput == null) {
            if (!keyFrame) return
            promotePendingSegmentOrThrow()
        } else if (pendingFile != null && keyFrame) {
            promotePendingSegmentOrThrow()
        }

        val output = activeOutput ?: return
        if (activeSegmentNeedsCodecConfig) {
            codecConfigBytes?.let { output.write(it) }
            activeSegmentNeedsCodecConfig = false
        }
        output.write(normalized.bytes, 0, normalized.length)
    }

    @Synchronized
    override fun close(deleteActiveFile: Boolean) {
        val active = activeFile
        closeActiveStream(syncToDisk = syncOnSegmentClose)
        activeSegmentNeedsCodecConfig = false

        if (deleteActiveFile) {
            deleteIfBroken(active)
        }

        val pending = pendingFile
        pendingFile = null
        deleteIfBroken(pending)
    }

    @Synchronized
    private fun replacePendingFile(file: File) {
        if (pendingFile?.absolutePath == file.absolutePath) return
        deleteIfBroken(pendingFile)
        pendingFile = file
    }

    @Synchronized
    private fun promotePendingSegmentOrThrow() {
        val nextFile = pendingFile ?: error("Pending segment file is not prepared")
        pendingFile = null

        val previousActiveFile = activeFile
        closeActiveStream(syncToDisk = syncOnSegmentClose)
        deleteIfBroken(previousActiveFile)

        ensureParentDirectory(nextFile)
        val fileStream = FileOutputStream(nextFile, false)
        activeFile = nextFile
        activeFileStream = fileStream
        activeOutput = BufferedOutputStream(fileStream, ioBufferBytes)
        activeSegmentNeedsCodecConfig = true
    }

    private fun readSampleBytes(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo): Int {
        ensureSampleScratchCapacity(bufferInfo.size)
        val sampleBuffer = buffer.duplicate().apply {
            position(bufferInfo.offset)
            limit(bufferInfo.offset + bufferInfo.size)
        }
        sampleBuffer.get(sampleScratch, 0, bufferInfo.size)
        return bufferInfo.size
    }

    private fun normalizeSampleBytes(bytes: ByteArray, size: Int): ByteArraySlice {
        if (bytes.hasStartCodePrefix(size)) {
            return ByteArraySlice(bytes = bytes, length = size)
        }

        val convertedLength = convertLengthPrefixedToAnnexB(bytes, size, nalLengthFieldBytes)
        return if (convertedLength > 0) {
            ByteArraySlice(bytes = annexBScratch, length = convertedLength)
        } else {
            ByteArraySlice(bytes = bytes, length = size)
        }
    }

    private fun normalizeCodecConfig(bytes: ByteArray, size: Int): ByteArray {
        if (bytes.hasStartCodePrefix(size)) {
            return bytes.copyOf(size)
        }

        parseAvcDecoderConfigurationRecord(bytes, size)?.let { parsed ->
            nalLengthFieldBytes = parsed.nalLengthFieldBytes
            return parsed.codecConfig
        }

        val convertedLength = convertLengthPrefixedToAnnexB(bytes, size, nalLengthFieldBytes)
        return if (convertedLength > 0) {
            annexBScratch.copyOf(convertedLength)
        } else {
            bytes.copyOf(size)
        }
    }

    private fun buildCodecConfigBytes(format: MediaFormat): ByteArray? {
        val csd0 = format.getByteBuffer("csd-0")?.toByteArray() ?: return null
        val csd1 = format.getByteBuffer("csd-1")?.toByteArray()

        val output = ByteArrayOutputStream(csd0.size + (csd1?.size ?: 0) + 16)
        output.write(normalizeCodecConfig(csd0, csd0.size))
        if (csd1 != null) {
            output.write(normalizeCodecConfig(csd1, csd1.size))
        }
        return output.toByteArray()
    }

    @Suppress("KotlinConstantConditions")
    private fun parseAvcDecoderConfigurationRecord(bytes: ByteArray, size: Int): AvcCodecConfig? {
        if (size < 7) return null
        if (bytes[0].toInt() != 1) return null

        val localNalLengthFieldBytes = (bytes[4].toInt() and 0x03) + 1
        var offset = 5
        val output = ByteArrayOutputStream(size + 16)

        if (offset >= size) return null
        val spsCount = bytes[offset].toInt() and 0x1F
        offset += 1
        repeat(spsCount) {
            val unitLength = bytes.readUnsignedShort(offset, size) ?: return null
            offset += 2
            if (offset + unitLength > size) return null
            output.write(ANNEX_B_START_CODE)
            output.write(bytes, offset, unitLength)
            offset += unitLength
        }

        if (offset >= size) {
            return AvcCodecConfig(
                nalLengthFieldBytes = localNalLengthFieldBytes,
                codecConfig = output.toByteArray(),
            )
        }

        val ppsCount = bytes[offset].toInt() and 0xFF
        offset += 1
        repeat(ppsCount) {
            val unitLength = bytes.readUnsignedShort(offset, size) ?: return null
            offset += 2
            if (offset + unitLength > size) return null
            output.write(ANNEX_B_START_CODE)
            output.write(bytes, offset, unitLength)
            offset += unitLength
        }

        return AvcCodecConfig(
            nalLengthFieldBytes = localNalLengthFieldBytes,
            codecConfig = output.toByteArray(),
        )
    }

    private fun convertLengthPrefixedToAnnexB(bytes: ByteArray, size: Int, lengthFieldBytes: Int): Int {
        if (lengthFieldBytes !in 1..4) return NO_BYTES_WRITTEN
        if (size <= lengthFieldBytes) return NO_BYTES_WRITTEN

        ensureAnnexBScratchCapacity(size + (size / maxOf(1, lengthFieldBytes)) * ANNEX_B_START_CODE.size)

        var inputOffset = 0
        var outputOffset = 0
        while (inputOffset + lengthFieldBytes <= size) {
            var nalLength = 0
            repeat(lengthFieldBytes) { index ->
                nalLength = (nalLength shl 8) or (bytes[inputOffset + index].toInt() and 0xFF)
            }
            inputOffset += lengthFieldBytes

            if (nalLength <= 0) return NO_BYTES_WRITTEN
            if (inputOffset + nalLength > size) return NO_BYTES_WRITTEN

            ANNEX_B_START_CODE.copyInto(annexBScratch, outputOffset)
            outputOffset += ANNEX_B_START_CODE.size
            System.arraycopy(bytes, inputOffset, annexBScratch, outputOffset, nalLength)
            outputOffset += nalLength
            inputOffset += nalLength
        }

        return if (inputOffset == size) outputOffset else NO_BYTES_WRITTEN
    }

    private fun closeActiveStream(syncToDisk: Boolean) {
        val output = activeOutput
        activeOutput = null
        runCatching { output?.flush() }

        val fileStream = activeFileStream
        activeFileStream = null
        if (syncToDisk) {
            runCatching { fileStream?.fd?.sync() }
        }
        runCatching { output?.close() }
        runCatching { fileStream?.close() }
        activeFile = null
    }

    private fun ensureSampleScratchCapacity(size: Int) {
        if (sampleScratch.size >= size) return
        sampleScratch = ByteArray(size)
    }

    private fun ensureAnnexBScratchCapacity(size: Int) {
        if (annexBScratch.size >= size) return
        annexBScratch = ByteArray(size)
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

    private fun deleteIfBroken(file: File?) {
        if (file == null || !file.exists()) return
        if (file.length() > brokenFileDeleteThresholdBytes) return
        runCatching { file.delete() }
            .onFailure { Timber.w(it, "Failed to delete broken segment: %s", file.absolutePath) }
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        val duplicate = duplicate()
        val bytes = ByteArray(duplicate.remaining())
        duplicate.get(bytes)
        return bytes
    }

    private fun ByteArray.hasStartCodePrefix(size: Int): Boolean {
        if (size < 4) return false
        if (this[0] == 0.toByte() && this[1] == 0.toByte() && this[2] == 1.toByte()) return true
        return this[0] == 0.toByte() &&
            this[1] == 0.toByte() &&
            this[2] == 0.toByte() &&
            this[3] == 1.toByte()
    }

    private fun ByteArray.readUnsignedShort(offset: Int, size: Int): Int? {
        if (offset < 0 || offset + 1 >= size) return null
        return ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)
    }
}
