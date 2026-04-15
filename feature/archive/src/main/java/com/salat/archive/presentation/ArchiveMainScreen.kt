package com.salat.archive.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salat.archive.presentation.components.ArchiveDayCard
import com.salat.archive.presentation.components.ArchiveEmptyState
import com.salat.archive.presentation.components.ArchiveSegmentDialog
import com.salat.archive.presentation.components.LIST_CONTENT_PADDING
import com.salat.archive.presentation.components.LIST_ITEM_SPACING
import com.salat.resources.R
import com.salat.uikit.component.TopShadow
import com.salat.uikit.preview.PreviewScreen
import com.salat.uikit.theme.AppTheme

@Composable
internal fun ArchiveMainScreen(
    state: ArchiveViewModel.ViewState,
    sendAction: (ArchiveViewModel.Action) -> Unit = {},
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.surfaceBackground)
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .systemBarsPadding()
                .navigationBarsPadding()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(16.dp))
                IconButton(
                    modifier = Modifier
                        .size(56.dp)
                        .padding(start = 2.dp),
                    onClick = onNavigateBack
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        tint = AppTheme.colors.contentPrimary,
                        contentDescription = stringResource(R.string.content_description_back)
                    )
                }

                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.archive_screen_title),
                    modifier = Modifier.weight(1f, false),
                    color = AppTheme.colors.contentPrimary,
                    style = AppTheme.typography.toolbar,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
                Spacer(Modifier.width(10.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(AppTheme.colors.surfaceSettings)
            ) {
                TopShadow()
                when {
                    state.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = AppTheme.colors.contentAccent)
                        }
                    }

                    state.showEmptyState -> ArchiveEmptyState()

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(LIST_CONTENT_PADDING.dp),
                            verticalArrangement = Arrangement.spacedBy(LIST_ITEM_SPACING.dp),
                        ) {
                            items(
                                items = state.days,
                                key = { it.id },
                            ) { day ->
                                ArchiveDayCard(
                                    day = day,
                                    onSegmentClick = { selected ->
                                        sendAction(ArchiveViewModel.Action.OnSegmentClick(selected))
                                    },
                                )
                            }
                            item(key = -1) {
                                Spacer(Modifier.height(48.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    state.selectedSegment?.let { segment ->
        ArchiveSegmentDialog(
            segment = segment,
            onDismiss = { sendAction(ArchiveViewModel.Action.DismissSegment) },
        )
    }
}

@Preview
@Composable
private fun ArchiveMainScreenPreview() {
    PreviewScreen {
        ArchiveMainScreen(
            state = ArchiveViewModel.ViewState(isLoading = false),
            onNavigateBack = {}
        )
    }
}
