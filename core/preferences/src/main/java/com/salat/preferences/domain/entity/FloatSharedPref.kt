package com.salat.preferences.domain.entity

// private object FloatSharedPrefKey {
//    const val DUMMY = "DUMMY"
// }

sealed class FloatSharedPref(val key: String, val default: Float)
