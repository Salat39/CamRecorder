package com.salat.sharedevents

import com.salat.sharedevents.domain.repository.SharedEventsRepository
import com.salat.sharedevents.domain.usecases.CheckAccessibilityServiceEnabledUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
object SharedEventsDomainModule {

    @Provides
    fun provideCheckAccessibilityServiceEnabledUseCase(repository: SharedEventsRepository) =
        CheckAccessibilityServiceEnabledUseCase(repository)
}
