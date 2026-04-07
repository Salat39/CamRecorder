package com.salat.sharedevents.data.repository

import com.salat.sharedevents.domain.repository.SharedEventsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedEventsRepositoryImpl : SharedEventsRepository {
    private val _accessibilityServiceEnabled = MutableStateFlow(false)
    override val accessibilityServiceEnabled = _accessibilityServiceEnabled.asStateFlow()

    override suspend fun setAccessibilityServiceEnabled(value: Boolean) = _accessibilityServiceEnabled.emit(value)
}
