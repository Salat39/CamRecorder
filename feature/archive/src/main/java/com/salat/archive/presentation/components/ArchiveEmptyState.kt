package com.salat.archive.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.salat.resources.R
import com.salat.uikit.theme.AppTheme

@Composable
internal fun ArchiveEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.archive_empty_message),
            modifier = Modifier.padding(horizontal = 24.dp),
            color = AppTheme.colors.contentPrimary,
            style = AppTheme.typography.screenTitle,
            textAlign = TextAlign.Center,
        )
    }
}
