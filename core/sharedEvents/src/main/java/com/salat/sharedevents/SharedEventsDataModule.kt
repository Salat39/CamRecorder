package com.salat.sharedevents

import com.salat.sharedevents.data.repository.SharedEventsRepositoryImpl
import com.salat.sharedevents.domain.repository.SharedEventsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SharedEventsDataModule {

    @Provides
    @Singleton
    fun provideStateKeeperRepository(): SharedEventsRepository = SharedEventsRepositoryImpl()
}
