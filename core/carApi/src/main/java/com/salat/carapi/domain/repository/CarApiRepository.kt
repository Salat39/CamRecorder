package com.salat.carapi.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface CarApiRepository {
    val ignitionStateFlow: StateFlow<Boolean>
}
