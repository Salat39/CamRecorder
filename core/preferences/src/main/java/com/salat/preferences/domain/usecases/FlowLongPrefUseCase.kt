package com.salat.preferences.domain.usecases

import com.salat.preferences.domain.DataStoreRepository
import com.salat.preferences.domain.entity.LongPref

class FlowLongPrefUseCase(private val preferences: DataStoreRepository) {
    fun execute(pref: LongPref) = preferences.getLongPrefFlow(pref)
}
