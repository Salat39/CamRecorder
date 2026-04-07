package com.salat.carapi

import com.salat.carapi.domain.repository.CarApiRepository
import com.salat.carapi.domain.usecases.GetIgnitionUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
object CarApiDomainModule {

    @Provides
    fun provideGetIgnitionUseCase(preferences: CarApiRepository) = GetIgnitionUseCase(preferences)
}
