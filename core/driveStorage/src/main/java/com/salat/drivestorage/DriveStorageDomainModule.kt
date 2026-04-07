package com.salat.drivestorage

import com.salat.drivestorage.domain.repository.DriveStorageRepository
import com.salat.drivestorage.domain.usecases.DriveConnectedUseCase
import com.salat.drivestorage.domain.usecases.FullFileAccessGrantedUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
object DriveStorageDomainModule {

    @Provides
    fun provideDriveConnectedUseCase(repository: DriveStorageRepository) = DriveConnectedUseCase(repository)

    @Provides
    fun provideFullFileAccessGrantedUseCase(repository: DriveStorageRepository) =
        FullFileAccessGrantedUseCase(repository)
}
