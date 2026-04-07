package com.salat.preferences.domain.entity

// private object StringPrefKey {
//    const val DUMMY = "DUMMY"
// }

sealed class StringPref(override val key: String, override val default: String) : AnyPref
