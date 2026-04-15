package com.salat.archive

import com.salat.archive.data.repository.ArchiveRepositoryImpl
import com.salat.archive.domain.repository.ArchiveRepository
import com.salat.drivestorage.domain.repository.DriveStorageRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ArchiveDataModule {

    @Provides
    @Singleton
    fun provideArchiveRepository(driveStorageRepository: DriveStorageRepository): ArchiveRepository =
        ArchiveRepositoryImpl(driveStorageRepository)
}
