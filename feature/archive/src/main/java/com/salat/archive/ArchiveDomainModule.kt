package com.salat.archive

import com.salat.archive.domain.repository.ArchiveRepository
import com.salat.archive.domain.usecases.GetArchiveContentUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
object ArchiveDomainModule {

    @Provides
    fun provideGetArchiveContentUseCase(repository: ArchiveRepository) = GetArchiveContentUseCase(repository)
}
