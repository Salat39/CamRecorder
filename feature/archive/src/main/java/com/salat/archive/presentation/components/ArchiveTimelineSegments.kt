package com.salat.archive.presentation.components

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.salat.archive.presentation.entity.DisplayArchiveSegment
import com.salat.ui.clickableNoRipple

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
internal fun ArchiveTimelineSegments(
    modifier: Modifier = Modifier,
    segments: List<DisplayArchiveSegment>,
    segmentColor: Color,
    onSegmentClick: (DisplayArchiveSegment) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val totalWidth = maxWidth
        val minSegmentWidth = MIN_SEGMENT_WIDTH.dp
        val density = LocalDensity.current
        val shape = RoundedCornerShape(SEGMENT_CORNER_RADIUS.dp)
        segments.forEach { segment ->
            val widthFraction = (segment.endFraction - segment.startFraction).coerceAtLeast(0f)
            val naturalWidth = totalWidth * widthFraction
            val naturalWidthPx = with(density) { naturalWidth.toPx() }
            val segmentWidth = if (widthFraction > 0f && naturalWidthPx < 1f) {
                minSegmentWidth
            } else {
                naturalWidth
            }
            Box(
                modifier = Modifier
                    .offset(x = totalWidth * segment.startFraction)
                    .fillMaxHeight()
                    .width(segmentWidth)
                    .background(segmentColor, shape)
                    .clickableNoRipple { onSegmentClick(segment) }
            )
        }
    }
}
