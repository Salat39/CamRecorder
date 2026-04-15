package com.salat.archive.presentation.mappers

import com.salat.archive.domain.entity.ArchiveContent
import com.salat.archive.domain.entity.ArchiveDay
import com.salat.archive.presentation.entity.DisplayArchiveCameraTrack
import com.salat.archive.presentation.entity.DisplayArchiveDay
import com.salat.archive.presentation.entity.DisplayArchiveSegment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val MILLIS_PER_DAY_INT = 24 * 60 * 60 * 1_000
private const val WINDOW_PADDING_MS = 5 * 60 * 1_000

private fun formatArchiveDayTitle(date: Date): String {
    val tz = TimeZone.getDefault()
    val dayYear = Calendar.getInstance(tz).apply { time = date }.get(Calendar.YEAR)
    val currentYear = Calendar.getInstance(tz).get(Calendar.YEAR)
    val pattern = if (dayYear == currentYear) "d MMMM" else "d MMMM yyyy"
    val primary = runCatching {
        SimpleDateFormat(pattern, Locale.getDefault()).apply { timeZone = tz }.format(date)
    }.getOrNull()?.takeIf { it.isNotBlank() }
    if (!primary.isNullOrBlank()) return primary
    return runCatching {
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = tz }.format(date)
    }.getOrNull().orEmpty()
}

internal fun ArchiveContent.toDisplay(): List<DisplayArchiveDay> = days.map { it.toDisplay() }

internal fun ArchiveDay.toDisplay(): DisplayArchiveDay {
    val title = formatArchiveDayTitle(id.toDate())
    var minStart = Int.MAX_VALUE
    var maxEnd = Int.MIN_VALUE
    tracks.forEach { track ->
        track.segments.forEach { segment ->
            minStart = minOf(minStart, segment.startMillisOfDay)
            maxEnd = maxOf(maxEnd, segment.endMillisOfDay)
        }
    }
    val (windowStart, windowEnd) = computeTimelineWindow(minStart, maxEnd)
    val span = (windowEnd - windowStart).toFloat().coerceAtLeast(1f)
    fun millisToFraction(millis: Int): Float = ((millis - windowStart) / span).coerceIn(0f, 1f)
    return DisplayArchiveDay(
        id = "${id.year}-${id.month}-${id.dayOfMonth}",
        title = title,
        tracks = tracks.map { track ->
            DisplayArchiveCameraTrack(
                cameraType = track.cameraType,
                segments = track.segments.map { segment ->
                    DisplayArchiveSegment(
                        id = segment.id,
                        cameraType = segment.cameraType,
                        startMillisOfDay = segment.startMillisOfDay,
                        endMillisOfDay = segment.endMillisOfDay,
                        startFraction = millisToFraction(segment.startMillisOfDay),
                        endFraction = millisToFraction(segment.endMillisOfDay),
                        sourceRecordIds = segment.sourceRecordIds,
                        sourceFilePaths = segment.sourceFilePaths,
                    )
                },
            )
        },
        windowStartMillisOfDay = windowStart,
        windowEndMillisOfDay = windowEnd,
    )
}

private fun computeTimelineWindow(minSegmentStart: Int, maxSegmentEnd: Int): Pair<Int, Int> {
    if (minSegmentStart == Int.MAX_VALUE || maxSegmentEnd == Int.MIN_VALUE) {
        return 0 to MILLIS_PER_DAY_INT
    }
    val paddedStart = (minSegmentStart - WINDOW_PADDING_MS).coerceAtLeast(0)
    val paddedEnd = (maxSegmentEnd + WINDOW_PADDING_MS).coerceAtMost(MILLIS_PER_DAY_INT)
    return if (paddedEnd <= paddedStart) {
        0 to MILLIS_PER_DAY_INT
    } else {
        paddedStart to paddedEnd
    }
}
