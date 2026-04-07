package com.salat.recorder.data.entity

internal data class AvcCodecConfig(
    val nalLengthFieldBytes: Int,
    val codecConfig: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AvcCodecConfig

        if (nalLengthFieldBytes != other.nalLengthFieldBytes) return false
        if (!codecConfig.contentEquals(other.codecConfig)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = nalLengthFieldBytes
        result = 31 * result + codecConfig.contentHashCode()
        return result
    }
}
