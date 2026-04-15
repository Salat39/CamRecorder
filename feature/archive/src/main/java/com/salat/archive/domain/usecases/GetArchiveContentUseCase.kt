package com.salat.archive.domain.usecases

import com.salat.archive.domain.entity.ArchiveContent
import com.salat.archive.domain.repository.ArchiveRepository

class GetArchiveContentUseCase(
    private val repository: ArchiveRepository,
) {
    suspend fun execute(): ArchiveContent = repository.getArchiveContent()
}
