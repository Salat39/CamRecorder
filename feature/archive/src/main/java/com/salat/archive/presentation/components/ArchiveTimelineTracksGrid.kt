package com.salat.archive.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.salat.archive.presentation.computeMinorStepMs
import com.salat.archive.presentation.computeTickStepMs
import com.salat.archive.presentation.generateAlignedTicksMillisOfDay
import com.salat.uikit.theme.AppTheme

@Composable
internal fun ArchiveTimelineTracksGrid(
    modifier: Modifier = Modifier,
    labelColumnWidth: Dp,
    windowStartMillisOfDay: Int,
    windowEndMillisOfDay: Int,
) {
    val lineColor = AppTheme.colors.historyBorder.copy(alpha = 0.35f)
    val minorLineColor = AppTheme.colors.historyBorder.copy(alpha = 0.16f)
    val lineWidth = TIMELINE_LINE_WIDTH.dp
    val labelWidth = labelColumnWidth
    val edgeInset = TIMELINE_EDGE_INSET.dp
    val density = LocalDensity.current
    val minLabelWidthPx = with(density) { TIMELINE_TICK_LABEL_WIDTH.dp.toPx() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val startX = labelWidth.toPx() + edgeInset.toPx()
        val endX = size.width - edgeInset.toPx()
        val contentWidth = (endX - startX).coerceAtLeast(0f)
        val spanMs = (windowEndMillisOfDay - windowStartMillisOfDay).toLong().coerceAtLeast(1L)
        val span = spanMs.toFloat()
        val majorStepMs = computeTickStepMs(spanMs, contentWidth, minLabelWidthPx)
        val stroke = lineWidth.toPx()

        val minorStepMs = computeMinorStepMs(majorStepMs)
        if (minorStepMs > 0L) {
            val minorTicks = generateAlignedTicksMillisOfDay(
                windowStartMillisOfDay,
                windowEndMillisOfDay,
                minorStepMs,
            )
            minorTicks.forEach { millisOfDay ->
                if (millisOfDay.toLong() % majorStepMs == 0L) return@forEach
                val fraction = (millisOfDay - windowStartMillisOfDay) / span
                if (fraction !in 0f..1f) return@forEach
                val x = startX + contentWidth * fraction
                drawLine(
                    color = minorLineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = stroke,
                )
            }
        }

        val majorTicks = generateAlignedTicksMillisOfDay(
            windowStartMillisOfDay,
            windowEndMillisOfDay,
            majorStepMs,
        ).ifEmpty {
            listOf(windowStartMillisOfDay, windowEndMillisOfDay).distinct().sorted()
        }
        majorTicks.forEach { millisOfDay ->
            val fraction = (millisOfDay - windowStartMillisOfDay) / span
            if (fraction !in 0f..1f) return@forEach
            val x = startX + contentWidth * fraction
            drawLine(
                color = lineColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = stroke,
            )
        }
    }
}
