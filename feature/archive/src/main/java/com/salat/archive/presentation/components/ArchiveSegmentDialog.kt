package com.salat.archive.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.salat.archive.presentation.entity.DisplayArchiveSelectedSegment
import com.salat.archive.presentation.formatMillisOfDayHms
import com.salat.commonconst.UI_SCALE
import com.salat.resources.R
import com.salat.uikit.component.BaseButton
import com.salat.uikit.component.BaseDialog
import com.salat.uikit.theme.AppTheme
import kotlin.math.max

@Composable
internal fun ArchiveSegmentDialog(segment: DisplayArchiveSelectedSegment, onDismiss: () -> Unit) {
    BaseDialog(uiScaleState = UI_SCALE, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.archive_segment_dialog_title),
                style = AppTheme.typography.dialogTitle,
                color = AppTheme.colors.contentPrimary,
            )
            Text(
                text = segment.dayTitle,
                style = AppTheme.typography.dialogSubtitle,
                color = AppTheme.colors.contentPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.archive_segment_dialog_camera,
                    segment.cameraTitle,
                ),
                style = AppTheme.typography.cardTitle,
                color = AppTheme.colors.contentPrimary,
            )
            Text(
                text = stringResource(
                    R.string.archive_segment_dialog_time,
                    formatMillisOfDayHms(segment.startMillisOfDay),
                    formatMillisOfDayHms(segment.endMillisOfDay),
                ),
                style = AppTheme.typography.cardFormatTitle,
                color = AppTheme.colors.contentPrimary,
            )
            Text(
                text = stringResource(
                    R.string.archive_segment_dialog_files,
                    max(segment.sourceRecordIds.size, segment.sourceFilePaths.size),
                ),
                style = AppTheme.typography.cardFormatTitle,
                color = AppTheme.colors.contentPrimary,
            )
            Spacer(Modifier.height(8.dp))
            BaseButton(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.archive_dialog_close),
                onClick = onDismiss,
            )
        }
    }
}
