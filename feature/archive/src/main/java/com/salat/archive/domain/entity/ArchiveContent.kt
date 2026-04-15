package com.salat.archive.domain.entity

import androidx.compose.runtime.Immutable

@Immutable
data class ArchiveContent(
    val days: List<ArchiveDay>,
    val invalidFiles: List<ArchiveInvalidFile>,
) {
    val isEmpty: Boolean
        get() = days.isEmpty()
}
