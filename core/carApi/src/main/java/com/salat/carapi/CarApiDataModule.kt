package com.salat.carapi

import android.content.Context
import com.salat.carapi.data.repository.CarApiRepositoryImpl
import com.salat.carapi.domain.repository.CarApiRepository
import com.salat.coroutines.IoCoroutineScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
object CarApiDataModule {

    @Provides
    @Singleton
    fun provideCarApiRepository(
        @ApplicationContext context: Context,
        @IoCoroutineScope scope: CoroutineScope,
    ): CarApiRepository = CarApiRepositoryImpl(context, scope)
}
