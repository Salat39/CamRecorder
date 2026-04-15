package com.salat.archive.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.salat.archive.presentation.entity.DisplayArchiveCameraTrack
import com.salat.archive.presentation.entity.DisplayArchiveDay
import com.salat.archive.presentation.entity.DisplayArchiveSelectedSegment
import com.salat.uikit.theme.AppTheme

@Composable
internal fun ArchiveDayCard(day: DisplayArchiveDay, onSegmentClick: (DisplayArchiveSelectedSegment) -> Unit) {
    val labelColumnWidth = rememberCameraLabelColumnWidth(
        cameraTypes = day.tracks.map(DisplayArchiveCameraTrack::cameraType),
    )
    val shape = RoundedCornerShape(CARD_SHAPE_RADIUS.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(CARD_SHADOW.dp, shape = shape)
            .background(
                color = AppTheme.colors.surfaceSettingsLayer1,
                shape = shape,
            )
            .padding(horizontal = 23.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(CARD_CONTENT_SPACING.dp),
    ) {
        Text(
            text = day.title,
            color = AppTheme.colors.settingsTitleAccent,
            style = AppTheme.typography.settingsTitle,
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            ArchiveTimelineTracksGrid(
                modifier = Modifier.matchParentSize(),
                labelColumnWidth = labelColumnWidth,
                windowStartMillisOfDay = day.windowStartMillisOfDay,
                windowEndMillisOfDay = day.windowEndMillisOfDay,
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(TRACK_SPACING.dp),
            ) {
                day.tracks.forEach { track ->
                    ArchiveCameraTrackRow(
                        dayTitle = day.title,
                        track = track,
                        labelColumnWidth = labelColumnWidth,
                        onSegmentClick = onSegmentClick,
                    )
                }
            }
        }
        ArchiveTimelineScale(
            labelColumnWidth = labelColumnWidth,
            windowStartMillisOfDay = day.windowStartMillisOfDay,
            windowEndMillisOfDay = day.windowEndMillisOfDay,
        )
    }
}
