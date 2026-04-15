package com.salat.preferences.domain.entity

import com.salat.commonconst.SEGMENT_DURATION_MS

private object LongPrefKey {
    const val SEGMENT_DURATION_MS_PN = "SEGMENT_DURATION_MS"
}

sealed class LongPref(override val key: String, override val default: Long) : AnyPref {
    data object SegmentDurationMs : LongPref(LongPrefKey.SEGMENT_DURATION_MS_PN, SEGMENT_DURATION_MS)
}
