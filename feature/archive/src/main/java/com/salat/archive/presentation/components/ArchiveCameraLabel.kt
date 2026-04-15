package com.salat.archive.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.salat.archive.domain.entity.ArchiveCameraType
import com.salat.resources.R
import com.salat.uikit.theme.AppTheme

internal fun archiveCameraLabelRes(type: ArchiveCameraType): Int = when (type) {
    ArchiveCameraType.LEFT -> R.string.archive_camera_left
    ArchiveCameraType.RIGHT -> R.string.archive_camera_right
    ArchiveCameraType.FRONT -> R.string.archive_camera_front
    ArchiveCameraType.BACK -> R.string.archive_camera_back
}

@Composable
internal fun archiveCameraLabel(type: ArchiveCameraType): String = stringResource(archiveCameraLabelRes(type))

@Composable
internal fun rememberCameraLabelColumnWidth(cameraTypes: List<ArchiveCameraType>): Dp {
    val uniqueTypes = cameraTypes.distinct()
    val labels = uniqueTypes.map { stringResource(archiveCameraLabelRes(it)) }
    val configuration = LocalConfiguration.current
    val localeTag = configuration.locales[0].toLanguageTag()
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val densityScale = density.density
    val textMeasurer = rememberTextMeasurer()
    val style = AppTheme.typography.cardFormatTitle
    return remember(cameraTypes, localeTag, fontScale, densityScale) {
        if (labels.isEmpty()) return@remember 0.dp
        val maxPx = labels.maxOf { label ->
            textMeasurer.measure(
                text = AnnotatedString(label),
                style = style,
                maxLines = 1,
            ).size.width
        }
        with(density) { maxPx.toDp() }
    }
}
