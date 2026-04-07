package com.salat.preview.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.salat.preview.presentation.entity.WarningStatus
import com.salat.uikit.theme.AppTheme

@Composable
internal fun RenderStatusWarning(text: String = "", type: WarningStatus = WarningStatus.WARNING) {
    val color = when (type) {
        WarningStatus.WARNING -> AppTheme.colors.warning
        WarningStatus.ERROR -> AppTheme.colors.statusError
        WarningStatus.OK -> AppTheme.colors.statusSuccess
    }
    val icon = when (type) {
        WarningStatus.WARNING -> Icons.Default.Warning
        WarningStatus.ERROR -> Icons.Filled.Close
        WarningStatus.OK -> Icons.Default.Check
    }
    Row(
        modifier = Modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier
                .size(11.dp)
                .then(
                    if (type == WarningStatus.WARNING) {
                        Modifier.padding(1.dp)
                    } else Modifier
                ),
            imageVector = icon,
            tint = color,
            contentDescription = "status"
        )
        Spacer(Modifier.width(5.dp))

        Text(
            text = text,
            style = AppTheme.typography.dialogSubtitle,
            color = color
        )
    }
}
