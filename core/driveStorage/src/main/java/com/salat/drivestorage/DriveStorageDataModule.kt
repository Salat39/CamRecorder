package com.salat.drivestorage

import android.content.Context
import com.salat.drivestorage.data.repository.DriveStorageRepositoryImpl
import com.salat.drivestorage.domain.repository.DriveStorageRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DriveStorageDataModule {

    @Provides
    @Singleton
    fun provideDriveStorageRepository(@ApplicationContext context: Context): DriveStorageRepository =
        DriveStorageRepositoryImpl(context)
}
