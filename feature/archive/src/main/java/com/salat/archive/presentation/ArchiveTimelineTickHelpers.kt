package com.salat.archive.presentation

import kotlin.math.max
import kotlin.math.min

private val TICK_STEPS_MS = longArrayOf(
    60_000L,
    5 * 60_000L,
    10 * 60_000L,
    15 * 60_000L,
    30 * 60_000L,
    60 * 60_000L,
    2 * 60 * 60_000L,
    3 * 60 * 60_000L,
    6 * 60 * 60_000L,
)

/**
 * Picks a step so the number of ticks does not exceed [maxTicks] (roughly), using the smallest catalog step >= ideal.
 */
private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1_000

internal fun computeTickStepMs(spanMs: Long, contentWidthPx: Float, minLabelWidthPx: Float): Long {
    if (spanMs <= 0L) return TICK_STEPS_MS.first()
    val safeMinLabel = minLabelWidthPx.coerceAtLeast(1f)
    val maxTicks = max(2, min(24, (contentWidthPx / safeMinLabel).toInt()))
    val ideal = spanMs / maxTicks.toLong()
    if (ideal <= 0L) return TICK_STEPS_MS.first()
    return TICK_STEPS_MS.firstOrNull { it >= ideal } ?: TICK_STEPS_MS.last()
}

internal fun generateAlignedTicksMillisOfDay(windowStart: Int, windowEnd: Int, stepMs: Long): List<Int> {
    if (stepMs <= 0L) return emptyList()
    val result = ArrayList<Int>()
    var t = windowStart.toLong()
    val rem = t % stepMs
    if (rem != 0L) t += stepMs - rem
    val end = windowEnd.toLong()
    while (t <= end) {
        result.add(t.coerceIn(0L, MILLIS_PER_DAY.toLong()).toInt())
        t += stepMs
    }
    return result
}

/**
 * Single set of millis-of-day labels for the timeline header: aligned ticks plus window edges,
 * pruned so label centers are at least [minCenterGapPx] apart (no overlap in one row).
 */
internal fun unifiedTimelineLabelMillis(
    windowStartMillisOfDay: Int,
    windowEndMillisOfDay: Int,
    majorStepMs: Long,
    contentWidthPx: Float,
    minCenterGapPx: Float,
): List<Int> {
    if (windowStartMillisOfDay == windowEndMillisOfDay) {
        return listOf(windowStartMillisOfDay)
    }
    val span = (windowEndMillisOfDay - windowStartMillisOfDay).toFloat().coerceAtLeast(1f)
    val gap = minCenterGapPx.coerceAtLeast(1f)
    val baseTicks = generateAlignedTicksMillisOfDay(
        windowStartMillisOfDay,
        windowEndMillisOfDay,
        majorStepMs,
    )
    val candidates = (baseTicks + windowStartMillisOfDay + windowEndMillisOfDay).distinct().sorted()
    val labels = candidates.mapNotNull { millis ->
        val frac = (millis - windowStartMillisOfDay) / span
        if (frac < 0f || frac > 1f) null else Triple(millis, frac, frac * contentWidthPx)
    }.sortedBy { it.third }
    if (labels.isEmpty()) {
        return listOf(windowStartMillisOfDay, windowEndMillisOfDay).distinct().sorted()
    }

    val pruned = mutableListOf<Triple<Int, Float, Float>>()
    for (l in labels) {
        if (pruned.isEmpty()) {
            pruned.add(l)
            continue
        }
        if (l.third - pruned.last().third >= gap) {
            pruned.add(l)
        }
    }

    if (pruned.first().first != windowStartMillisOfDay) {
        while (pruned.isNotEmpty() && pruned.first().third < gap) {
            pruned.removeAt(0)
        }
        pruned.add(0, Triple(windowStartMillisOfDay, 0f, 0f))
    }
    while (pruned.size > 1 && pruned[1].third - pruned[0].third < gap) {
        pruned.removeAt(1)
    }

    if (pruned.last().first != windowEndMillisOfDay) {
        while (pruned.size > 1 && contentWidthPx - pruned[pruned.lastIndex - 1].third < gap) {
            pruned.removeAt(pruned.lastIndex)
        }
        pruned.add(Triple(windowEndMillisOfDay, 1f, contentWidthPx))
    }
    while (pruned.size > 1 && pruned.last().third - pruned[pruned.lastIndex - 1].third < gap) {
        pruned.removeAt(pruned.lastIndex - 1)
    }

    return pruned.map { it.first }
}

internal fun computeMinorStepMs(majorStepMs: Long): Long {
    val minor = when {
        majorStepMs >= 60 * 60 * 1000L -> 15 * 60 * 1000L
        majorStepMs >= 15 * 60 * 1000L -> 5 * 60 * 1000L
        majorStepMs >= 5 * 60 * 1000L -> 60 * 1000L
        else -> 0L
    }
    return if (minor in 1..<majorStepMs) minor else 0L
}
