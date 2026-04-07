package com.salat.preferences.domain.entity

// private object BoolSharedPrefKey {
//    const val DARK_THEME = "dark_theme"
// }

sealed class BoolSharedPref(val key: String, val default: Boolean) {
//    data object DarkTheme : BoolSharedPref(BoolSharedPrefKey.DARK_THEME, true)
}
