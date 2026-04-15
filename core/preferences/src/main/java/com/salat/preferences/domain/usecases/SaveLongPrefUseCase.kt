package com.salat.preferences.domain.usecases

import com.salat.preferences.domain.DataStoreRepository
import com.salat.preferences.domain.entity.LongPref

class SaveLongPrefUseCase(private val preferences: DataStoreRepository) {
    suspend fun execute(pref: LongPref, value: Long) = preferences.save(pref, value)
}
