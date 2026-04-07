package com.salat.preferences.domain.entity

// private object IntPrefKey {
//    const val DUMMY = "DUMMY"
// }

sealed class IntPref(override val key: String, override val default: Int) : AnyPref
