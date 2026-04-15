package com.salat.preferences.domain.usecases

import com.salat.preferences.domain.DataStoreRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ObserveAllCamerasDiableUseCase(private val preferences: DataStoreRepository) {
    fun execute() = preferences.getCameraEnabledValuesFlow()
        .map { values ->
            values.isNotEmpty() && values.all { enabled -> !enabled }
        }
        .distinctUntilChanged()
}
