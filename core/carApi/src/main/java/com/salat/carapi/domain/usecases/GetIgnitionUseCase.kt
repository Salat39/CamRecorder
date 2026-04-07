package com.salat.carapi.domain.usecases

import com.salat.carapi.domain.repository.CarApiRepository

class GetIgnitionUseCase(repository: CarApiRepository) {
    val flow = repository.ignitionStateFlow
}
