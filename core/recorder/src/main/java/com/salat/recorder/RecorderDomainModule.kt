package com.salat.recorder

import com.salat.recorder.domain.repository.RecorderRepository
import com.salat.recorder.domain.usecases.GetAvailableCamInfoUseCase
import com.salat.recorder.domain.usecases.HasCameraPermissionUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
object RecorderDomainModule {

    @Provides
    fun provideGetAvailableCamInfoUseCase(repository: RecorderRepository) = GetAvailableCamInfoUseCase(repository)

    @Provides
    fun provideHasCameraPermissionUseCase(repository: RecorderRepository) = HasCameraPermissionUseCase(repository)
}
