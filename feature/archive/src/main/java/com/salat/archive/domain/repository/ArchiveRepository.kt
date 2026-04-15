package com.salat.archive.domain.repository

import com.salat.archive.domain.entity.ArchiveContent

interface ArchiveRepository {
    suspend fun getArchiveContent(): ArchiveContent
}
