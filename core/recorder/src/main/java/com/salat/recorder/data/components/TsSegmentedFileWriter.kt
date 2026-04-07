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
import kotlin.math.max
import timber.log.Timber

internal class TsSegmentedFileWriter(
    private val brokenFileDeleteThresholdBytes: Long,
    private val ioBufferBytes: Int,
    private val syncOnSegmentClose: Boolean,
) : SegmentedVideoFileWriter {
    private companion object {
        private const val TS_PACKET_SIZE = 188
        private const val TS_HEADER_SIZE = 4
        private const val TS_PAYLOAD_SIZE = TS_PACKET_SIZE - TS_HEADER_SIZE
        private const val ADAPTATION_FIELD_LENGTH_BYTES = 1
        private const val PCR_ADAPTATION_FIELD_LENGTH = 7
        private const val NO_ADAPTATION_FIELD = -1
        private const val MPEG_TIMEBASE_HZ = 90_000L
        private const val PES_STREAM_ID_VIDEO = 0xE0
        private const val PAT_PID = 0x0000
        private const val PMT_PID = 0x0100
        private const val VIDEO_PID = 0x0101
        private const val PTS_ONLY_PREFIX = 0x02
        private const val PTS_BYTES_LENGTH = 5
        private const val TS_CONTINUITY_COUNTER_MASK = 0x0F
        private const val TS_ADAPTATION_CONTROL_PAYLOAD_ONLY = 0x10
        private const val TS_ADAPTATION_CONTROL_ADAPTATION_AND_PAYLOAD = 0x30
        private const val PSI_PAYLOAD_START_MASK = 0x40
        private const val TS_PAYLOAD_START_MASK = 0x40
        private const val PCR_FLAG_MASK = 0x10
        private const val DEFAULT_NAL_LENGTH_FIELD_BYTES = 4
        private const val PES_HEADER_BYTES = 14
        private const val MAX_PAYLOAD_PARTS = 4
        private const val NO_BYTES_WRITTEN = -1
        private const val TS_SYNC_BYTE = 0x47.toByte()
        private const val TS_STUFFING_BYTE = 0xFF.toByte()
        private val ANNEX_B_START_CODE = byteArrayOf(0x00, 0x00, 0x00, 0x01)
        private val AUD_NAL = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x09, 0x10)
        private val PAT_SECTION = byteArrayOf(
            0x00,
            0xB0.toByte(), 0x0D,
            0x00, 0x01,
            0xC1.toByte(),
            0x00,
            0x00,
            0x00, 0x01,
            0xE1.toByte(), 0x00,
            0xE8.toByte(), 0xF9.toByte(), 0x5E, 0x7D,
        )
        private val PMT_SECTION = byteArrayOf(
            0x02,
            0xB0.toByte(), 0x12,
            0x00, 0x01,
            0xC1.toByte(),
            0x00,
            0x00,
            0xE1.toByte(), 0x01,
            0xF0.toByte(), 0x00,
            0x1B,
            0xE1.toByte(), 0x01,
            0xF0.toByte(), 0x00,
            0x4F, 0xC4.toByte(), 0x3D, 0x1B,
        )
    }

    private var activeFile: File? = null
    private var activeFileStream: FileOutputStream? = null
    private var activeOutput: BufferedOutputStream? = null
    private var activeSegmentNeedsBootstrap = false
    private var activeSegmentStartPtsUs: Long? = null

    private var pendingFile: File? = null
    private var codecConfigBytes: ByteArray? = null
    private var nalLengthFieldBytes = DEFAULT_NAL_LENGTH_FIELD_BYTES

    private var patContinuityCounter = 0
    private var pmtContinuityCounter = 0
    private var videoContinuityCounter = 0

    private var sampleScratch = ByteArray(0)
    private var annexBScratch = ByteArray(0)
    private val tsPacketScratch = ByteArray(TS_PACKET_SIZE)
    private val pesHeaderScratch = ByteArray(PES_HEADER_BYTES)
    private val payloadParts = arrayOfNulls<ByteArray>(MAX_PAYLOAD_PARTS)
    private val payloadLengths = IntArray(MAX_PAYLOAD_PARTS)

    @Synchronized
    override fun onOutputFormatChanged(format: MediaFormat) {
        codecConfigBytes = buildCodecConfigBytes(format)?.also {
            Timber.d("TS codec config received: %s bytes", it.size)
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
        if (activeSegmentStartPtsUs == null) {
            activeSegmentStartPtsUs = bufferInfo.presentationTimeUs
        }

        val relativePtsUs = max(0L, bufferInfo.presentationTimeUs - (activeSegmentStartPtsUs ?: 0L))
        if (activeSegmentNeedsBootstrap) {
            writePat(output)
            writePmt(output)
        }
        writeVideoPes(
            output = output,
            sampleBytes = normalized.bytes,
            sampleLength = normalized.length,
            codecConfig = if (activeSegmentNeedsBootstrap) codecConfigBytes else null,
            presentationTimeUs = relativePtsUs,
        )
        activeSegmentNeedsBootstrap = false
    }

    @Synchronized
    override fun close(deleteActiveFile: Boolean) {
        val active = activeFile
        closeActiveStream(syncToDisk = syncOnSegmentClose)
        activeSegmentNeedsBootstrap = false
        activeSegmentStartPtsUs = null

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
        activeSegmentNeedsBootstrap = true
        activeSegmentStartPtsUs = null
        patContinuityCounter = 0
        pmtContinuityCounter = 0
        videoContinuityCounter = 0
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

    private fun writeVideoPes(
        output: BufferedOutputStream,
        sampleBytes: ByteArray,
        sampleLength: Int,
        codecConfig: ByteArray?,
        presentationTimeUs: Long,
    ) {
        val pts90k = usToPts90k(presentationTimeUs)
        val pesHeaderLength = buildPesHeader(pts90k)
        val partCount = buildPayloadParts(
            pesHeaderLength = pesHeaderLength,
            codecConfig = codecConfig,
            sampleBytes = sampleBytes,
            sampleLength = sampleLength,
        )

        var remainingPayload = 0
        repeat(partCount) { index ->
            remainingPayload += payloadLengths[index]
        }

        var currentPartIndex = 0
        var currentPartOffset = 0
        var firstPacket = true

        while (remainingPayload > 0) {
            val useAdaptationField = firstPacket || remainingPayload < TS_PAYLOAD_SIZE
            val minAdaptationFieldLength = if (firstPacket) PCR_ADAPTATION_FIELD_LENGTH else 0
            val payloadSize = if (useAdaptationField) {
                minOf(
                    remainingPayload,
                    TS_PACKET_SIZE - TS_HEADER_SIZE - ADAPTATION_FIELD_LENGTH_BYTES - minAdaptationFieldLength,
                )
            } else {
                minOf(remainingPayload, TS_PAYLOAD_SIZE)
            }

            val adaptationFieldLength = if (useAdaptationField) {
                TS_PACKET_SIZE - TS_HEADER_SIZE - ADAPTATION_FIELD_LENGTH_BYTES - payloadSize
            } else {
                NO_ADAPTATION_FIELD
            }

            writeTsHeader(
                packet = tsPacketScratch,
                payloadStart = firstPacket,
                adaptationFieldLength = adaptationFieldLength,
            )

            var packetOffset = TS_HEADER_SIZE
            if (useAdaptationField) {
                packetOffset += writeAdaptationField(
                    packet = tsPacketScratch,
                    offset = packetOffset,
                    adaptationFieldLength = adaptationFieldLength,
                    pcr90k = if (firstPacket) pts90k else null,
                )
            }

            var bytesLeftForPacket = payloadSize
            while (bytesLeftForPacket > 0 && currentPartIndex < partCount) {
                val partBytes = payloadParts[currentPartIndex] ?: break
                val partLength = payloadLengths[currentPartIndex]
                val partRemaining = partLength - currentPartOffset
                val copyLength = minOf(partRemaining, bytesLeftForPacket)
                System.arraycopy(partBytes, currentPartOffset, tsPacketScratch, packetOffset, copyLength)
                packetOffset += copyLength
                currentPartOffset += copyLength
                bytesLeftForPacket -= copyLength
                remainingPayload -= copyLength

                if (currentPartOffset >= partLength) {
                    currentPartIndex += 1
                    currentPartOffset = 0
                }
            }

            output.write(tsPacketScratch, 0, TS_PACKET_SIZE)
            firstPacket = false
        }
    }

    private fun buildPayloadParts(
        pesHeaderLength: Int,
        codecConfig: ByteArray?,
        sampleBytes: ByteArray,
        sampleLength: Int,
    ): Int {
        payloadParts[0] = pesHeaderScratch
        payloadLengths[0] = pesHeaderLength

        payloadParts[1] = AUD_NAL
        payloadLengths[1] = AUD_NAL.size

        if (codecConfig != null) {
            payloadParts[2] = codecConfig
            payloadLengths[2] = codecConfig.size
            payloadParts[3] = sampleBytes
            payloadLengths[3] = sampleLength
            return 4
        }

        payloadParts[2] = sampleBytes
        payloadLengths[2] = sampleLength
        payloadParts[3] = null
        payloadLengths[3] = 0
        return 3
    }

    private fun writePat(output: BufferedOutputStream) {
        writePsiPacket(
            output = output,
            pid = PAT_PID,
            continuityCounter = patContinuityCounter,
            section = PAT_SECTION,
        )
        patContinuityCounter = (patContinuityCounter + 1) and TS_CONTINUITY_COUNTER_MASK
    }

    private fun writePmt(output: BufferedOutputStream) {
        writePsiPacket(
            output = output,
            pid = PMT_PID,
            continuityCounter = pmtContinuityCounter,
            section = PMT_SECTION,
        )
        pmtContinuityCounter = (pmtContinuityCounter + 1) and TS_CONTINUITY_COUNTER_MASK
    }

    private fun writePsiPacket(output: BufferedOutputStream, pid: Int, continuityCounter: Int, section: ByteArray) {
        tsPacketScratch[0] = TS_SYNC_BYTE
        tsPacketScratch[1] = (((pid ushr 8) and 0x1F) or PSI_PAYLOAD_START_MASK).toByte()
        tsPacketScratch[2] = (pid and 0xFF).toByte()
        tsPacketScratch[3] =
            (TS_ADAPTATION_CONTROL_PAYLOAD_ONLY or (continuityCounter and TS_CONTINUITY_COUNTER_MASK)).toByte()
        tsPacketScratch[4] = 0x00
        System.arraycopy(section, 0, tsPacketScratch, 5, section.size)
        fillPacketTailWithStuffing(5 + section.size)
        output.write(tsPacketScratch, 0, TS_PACKET_SIZE)
    }

    private fun writeTsHeader(packet: ByteArray, payloadStart: Boolean, adaptationFieldLength: Int) {
        packet[0] = TS_SYNC_BYTE
        packet[1] = (((VIDEO_PID ushr 8) and 0x1F) or if (payloadStart) TS_PAYLOAD_START_MASK else 0).toByte()
        packet[2] = (VIDEO_PID and 0xFF).toByte()
        val adaptationControl = if (adaptationFieldLength == NO_ADAPTATION_FIELD) {
            TS_ADAPTATION_CONTROL_PAYLOAD_ONLY
        } else {
            TS_ADAPTATION_CONTROL_ADAPTATION_AND_PAYLOAD
        }
        packet[3] = (adaptationControl or (videoContinuityCounter and TS_CONTINUITY_COUNTER_MASK)).toByte()
        videoContinuityCounter = (videoContinuityCounter + 1) and TS_CONTINUITY_COUNTER_MASK
    }

    private fun writeAdaptationField(packet: ByteArray, offset: Int, adaptationFieldLength: Int, pcr90k: Long?): Int {
        packet[offset] = adaptationFieldLength.toByte()
        if (adaptationFieldLength == 0) return ADAPTATION_FIELD_LENGTH_BYTES

        val flagsOffset = offset + 1
        val stuffingStartOffset = if (pcr90k != null) {
            packet[flagsOffset] = PCR_FLAG_MASK.toByte()
            writePcr(packet, flagsOffset + 1, pcr90k)
            flagsOffset + 7
        } else {
            packet[flagsOffset] = 0x00
            flagsOffset + 1
        }

        var stuffingOffset = stuffingStartOffset
        val fieldEndOffset = offset + 1 + adaptationFieldLength
        while (stuffingOffset < fieldEndOffset) {
            packet[stuffingOffset] = TS_STUFFING_BYTE
            stuffingOffset += 1
        }

        return ADAPTATION_FIELD_LENGTH_BYTES + adaptationFieldLength
    }

    private fun writePcr(packet: ByteArray, offset: Int, pcr90k: Long) {
        val pcrBase = pcr90k and 0x1FFFFFFFFL
        packet[offset] = ((pcrBase ushr 25) and 0xFF).toByte()
        packet[offset + 1] = ((pcrBase ushr 17) and 0xFF).toByte()
        packet[offset + 2] = ((pcrBase ushr 9) and 0xFF).toByte()
        packet[offset + 3] = ((pcrBase ushr 1) and 0xFF).toByte()
        packet[offset + 4] = (((pcrBase and 0x01) shl 7) or 0x7E).toByte()
        packet[offset + 5] = 0x00
    }

    private fun buildPesHeader(pts90k: Long): Int {
        pesHeaderScratch[0] = 0x00
        pesHeaderScratch[1] = 0x00
        pesHeaderScratch[2] = 0x01
        pesHeaderScratch[3] = PES_STREAM_ID_VIDEO.toByte()
        pesHeaderScratch[4] = 0x00
        pesHeaderScratch[5] = 0x00
        pesHeaderScratch[6] = 0x80.toByte()
        pesHeaderScratch[7] = 0x80.toByte()
        pesHeaderScratch[8] = PTS_BYTES_LENGTH.toByte()
        writePtsBytes(pesHeaderScratch, 9, PTS_ONLY_PREFIX, pts90k)
        return 9 + PTS_BYTES_LENGTH
    }

    @Suppress("SameParameterValue")
    private fun writePtsBytes(target: ByteArray, offset: Int, prefix: Int, pts90k: Long) {
        val pts = pts90k and 0x1FFFFFFFFL
        target[offset] =
            (((prefix and 0x0F) shl 4) or (((pts ushr 30) and 0x07).toInt() shl 1) or 0x01).toByte()
        target[offset + 1] = ((pts ushr 22) and 0xFF).toByte()
        target[offset + 2] = ((((pts ushr 15) and 0x7F).toInt() shl 1) or 0x01).toByte()
        target[offset + 3] = ((pts ushr 7) and 0xFF).toByte()
        target[offset + 4] = ((((pts and 0x7F).toInt()) shl 1) or 0x01).toByte()
    }

    private fun usToPts90k(presentationTimeUs: Long): Long {
        return (presentationTimeUs * MPEG_TIMEBASE_HZ) / 1_000_000L
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

    private fun fillPacketTailWithStuffing(fromOffset: Int) {
        if (fromOffset >= TS_PACKET_SIZE) return
        tsPacketScratch.fill(TS_STUFFING_BYTE, fromOffset, TS_PACKET_SIZE)
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
