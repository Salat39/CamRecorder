package com.salat.recorder

import android.content.Context
import com.salat.carapi.domain.repository.CarApiRepository
import com.salat.coroutines.IoCoroutineScope
import com.salat.drivestorage.domain.repository.DriveStorageRepository
import com.salat.preferences.domain.DataStoreRepository
import com.salat.recorder.data.repository.RecorderRepositoryImpl
import com.salat.recorder.domain.repository.RecorderRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
object RecorderDataModule {

    @Provides
    @Singleton
    fun provideRecorderRepository(
        @ApplicationContext context: Context,
        @IoCoroutineScope scope: CoroutineScope,
        carApi: CarApiRepository,
        driveStorage: DriveStorageRepository,
        dataStore: DataStoreRepository
    ): RecorderRepository = RecorderRepositoryImpl(context, scope, carApi, driveStorage, dataStore)
}
