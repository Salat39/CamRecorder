package com.salat.archive.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.salat.archive.domain.entity.ArchiveCameraType
import com.salat.archive.presentation.entity.DisplayArchiveCameraTrack
import com.salat.archive.presentation.entity.DisplayArchiveSegment
import com.salat.archive.presentation.entity.DisplayArchiveSelectedSegment
import com.salat.uikit.theme.AppTheme

@Composable
internal fun ArchiveCameraTrackRow(
    dayTitle: String,
    track: DisplayArchiveCameraTrack,
    labelColumnWidth: Dp,
    onSegmentClick: (DisplayArchiveSelectedSegment) -> Unit,
) {
    val segmentColor = track.cameraType.toSegmentColor()
    val trackBackgroundColor = AppTheme.colors.historyBorder.copy(alpha = 0.12f)
    val trackShape = RoundedCornerShape(SEGMENT_CORNER_RADIUS.dp)

    val cameraLabel = archiveCameraLabel(track.cameraType)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.width(labelColumnWidth),
            text = cameraLabel,
            color = AppTheme.colors.contentPrimary,
            style = AppTheme.typography.cardFormatTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = TIMELINE_EDGE_INSET.dp)
                .height(SEGMENT_TRACK_HEIGHT.dp)
                .background(trackBackgroundColor, trackShape)
        ) {
            ArchiveTimelineSegments(
                segments = track.segments,
                segmentColor = segmentColor,
                onSegmentClick = { segment ->
                    onSegmentClick(segment.toSelected(dayTitle, cameraLabel))
                },
            )
        }
    }
}

private fun DisplayArchiveSegment.toSelected(dayTitle: String, cameraTitle: String): DisplayArchiveSelectedSegment {
    return DisplayArchiveSelectedSegment(
        segmentId = id,
        dayTitle = dayTitle,
        cameraTitle = cameraTitle,
        startMillisOfDay = startMillisOfDay,
        endMillisOfDay = endMillisOfDay,
        sourceRecordIds = sourceRecordIds,
        sourceFilePaths = sourceFilePaths,
    )
}

@Composable
private fun ArchiveCameraType.toSegmentColor(): Color = when (this) {
    ArchiveCameraType.FRONT -> AppTheme.colors.contentAccent
    ArchiveCameraType.BACK -> AppTheme.colors.historyAccentBorder
    ArchiveCameraType.LEFT -> AppTheme.colors.statusWarning
    ArchiveCameraType.RIGHT -> AppTheme.colors.statusError
}
