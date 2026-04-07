package com.salat.sharedevents.domain.usecases

import com.salat.sharedevents.domain.repository.SharedEventsRepository

class CheckAccessibilityServiceEnabledUseCase(repository: SharedEventsRepository) {
    val flow = repository.accessibilityServiceEnabled
}
