package com.salat.recorder.data.entity

internal data class ByteArraySlice(
    val bytes: ByteArray,
    val length: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ByteArraySlice

        if (length != other.length) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = length
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}
