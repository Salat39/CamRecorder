package com.salat.sharedevents.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface SharedEventsRepository {
    val accessibilityServiceEnabled: StateFlow<Boolean>

    suspend fun setAccessibilityServiceEnabled(value: Boolean)
}
