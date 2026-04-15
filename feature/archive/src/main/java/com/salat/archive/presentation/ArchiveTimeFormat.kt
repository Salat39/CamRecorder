package com.salat.archive.presentation

import java.util.Locale

internal fun formatMillisOfDayHms(millis: Int): String {
    val totalSeconds = (millis / 1_000).coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}

internal fun formatMillisOfDayHm(millis: Int): String {
    val totalSeconds = (millis / 1_000).coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    return String.format(Locale.US, "%02d:%02d", hours, minutes)
}
