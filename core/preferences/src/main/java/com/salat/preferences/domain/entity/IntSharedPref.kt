package com.salat.preferences.domain.entity

// private object IntSharedPrefKey {
//    const val DUMMY = "DUMMY"
// }

sealed class IntSharedPref(val key: String, val default: Int)
