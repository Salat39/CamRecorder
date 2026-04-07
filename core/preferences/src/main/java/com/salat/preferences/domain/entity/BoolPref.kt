package com.salat.preferences.domain.entity

private object BoolPrefKey {
    const val ENABLE_RECORD = "ENABLE_RECORD"
    const val FORCE_RECORD = "FORCE_RECORD"
}

sealed class BoolPref(override val key: String, override val default: Boolean) : AnyPref {
    data object EnableRecord : BoolPref(BoolPrefKey.ENABLE_RECORD, false)
    data object ForceRecord : BoolPref(BoolPrefKey.FORCE_RECORD, false)
}
