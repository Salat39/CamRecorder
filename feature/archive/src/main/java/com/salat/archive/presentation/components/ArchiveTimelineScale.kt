package com.salat.archive.presentation.components

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.salat.archive.presentation.computeTickStepMs
import com.salat.archive.presentation.formatMillisOfDayHm
import com.salat.archive.presentation.formatMillisOfDayHms
import com.salat.archive.presentation.unifiedTimelineLabelMillis
import com.salat.uikit.theme.AppTheme
import kotlin.math.roundToInt

private const val SPAN_MS_USE_SECONDS_ON_SCALE = 10 * 60 * 1_000

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
internal fun ArchiveTimelineScale(
    modifier: Modifier = Modifier,
    labelColumnWidth: Dp,
    windowStartMillisOfDay: Int,
    windowEndMillisOfDay: Int
) {
    val labelWidth = TIMELINE_TICK_LABEL_WIDTH.dp
    val edgeInset = TIMELINE_EDGE_INSET.dp
    val textColor = AppTheme.colors.contentPrimary.copy(alpha = 0.8f)
    val density = LocalDensity.current

    val spanMs = (windowEndMillisOfDay - windowStartMillisOfDay).toLong().coerceAtLeast(1L)
    val useSecondsOnTicks = spanMs < SPAN_MS_USE_SECONDS_ON_SCALE

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(start = labelColumnWidth)
            .padding(horizontal = edgeInset),
    ) {
        val labelWidthPx = with(density) { labelWidth.toPx() }
        val timelineContentWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(0f)
        val minCenterGapPx = labelWidthPx
        val span = spanMs.toFloat()
        val majorStepMs = computeTickStepMs(spanMs, timelineContentWidthPx, minCenterGapPx)
        val labelsMillis = unifiedTimelineLabelMillis(
            windowStartMillisOfDay = windowStartMillisOfDay,
            windowEndMillisOfDay = windowEndMillisOfDay,
            majorStepMs = majorStepMs,
            contentWidthPx = timelineContentWidthPx,
            minCenterGapPx = minCenterGapPx,
        )

        labelsMillis.forEach { millisOfDay ->
            val fraction = (millisOfDay - windowStartMillisOfDay) / span
            if (fraction !in 0f..1f) return@forEach
            val centerX = timelineContentWidthPx * fraction
            val leftPx = (centerX - labelWidthPx / 2f).roundToInt()
            val label = if (useSecondsOnTicks) {
                formatMillisOfDayHms(millisOfDay)
            } else {
                formatMillisOfDayHm(millisOfDay)
            }
            Text(
                text = label,
                style = AppTheme.typography.dialogSubtitle,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .width(labelWidth)
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            leftPx,
                            0,
                        )
                    },
            )
        }
    }
}
